package com.aistudio.missioncontrol.pxytwe.audio

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class FakeBase64PcmDecoder : Base64PcmDecoder {
    override fun decode(base64Payload: String): ByteArray {
        if (base64Payload == "BAD_BASE64") throw IllegalArgumentException("Bad base64")
        return Base64.getDecoder().decode(base64Payload)
    }
}

class FakeAudioOutput : AudioOutput {
    var playCalled = 0
    var stopCalled = 0
    var releaseCalled = 0
    
    val writtenBytes = mutableListOf<Byte>()
    
    // Test configuration
    var simulateWriteError = false
    var simulatePartialWrites = false

    override fun play() {
        playCalled++
    }

    override fun write(pcmBytes: ByteArray, offsetInBytes: Int, sizeInBytes: Int): Int {
        if (simulateWriteError) return -1

        val bytesToWrite = if (simulatePartialWrites) {
            // Write half the bytes at a time
            sizeInBytes / 2 + 1
        } else {
            sizeInBytes
        }

        val actualWrite = minOf(bytesToWrite, sizeInBytes)
        
        for (i in 0 until actualWrite) {
            writtenBytes.add(pcmBytes[offsetInBytes + i])
        }
        
        return actualWrite
    }

    override fun stop() {
        stopCalled++
    }

    override fun release() {
        releaseCalled++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AudioPlaybackEngineTest {

    private val sessionId = "session-test"

    private fun validFrame(
        payload: String = Base64.getEncoder().encodeToString(ByteArray(4000) { it.toByte() }),
        sid: String = sessionId,
        dur: Int = 250,
        enc: String = "PCM_16BIT_8000HZ_MONO"
    ): EncodedAudioFrame {
        return EncodedAudioFrame(
            sessionId = sid,
            deviceId = "device1",
            seq = 1,
            timestamp = 1000L,
            encoding = enc,
            durationMs = dur,
            payload = payload
        )
    }

    @Test
    fun testA_validBase64DecodeAndWrite() = runTest {
        val decoder = FakeBase64PcmDecoder()
        val output = FakeAudioOutput()
        val engine = AudioPlaybackEngine(sessionId, decoder, output)

        val originalBytes = ByteArray(4000) { (it % 256).toByte() }
        val payload = Base64.getEncoder().encodeToString(originalBytes)

        engine.start()
        val result = engine.write(validFrame(payload = payload))

        assertTrue(result.isSuccess)
        assertEquals(1, output.playCalled)
        assertEquals(4000, output.writtenBytes.size)
        
        val writtenArray = output.writtenBytes.toByteArray()
        assertArrayEquals(originalBytes, writtenArray)
        
        engine.release()
    }

    @Test
    fun testB_wrongPayloadSize() = runTest {
        val decoder = FakeBase64PcmDecoder()
        val output = FakeAudioOutput()
        val engine = AudioPlaybackEngine(sessionId, decoder, output)

        // 3999 bytes
        val originalBytes = ByteArray(3999) { it.toByte() }
        val payload = Base64.getEncoder().encodeToString(originalBytes)

        engine.start()
        val result = engine.write(validFrame(payload = payload))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AudioPlaybackError.InvalidPayloadSize)
        assertEquals(0, output.writtenBytes.size)
    }

    @Test
    fun testC_oversizedPayload() = runTest {
        val decoder = FakeBase64PcmDecoder()
        val output = FakeAudioOutput()
        val engine = AudioPlaybackEngine(sessionId, decoder, output)

        // 4001 bytes
        val originalBytes = ByteArray(4001) { it.toByte() }
        val payload = Base64.getEncoder().encodeToString(originalBytes)

        engine.start()
        val result = engine.write(validFrame(payload = payload))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AudioPlaybackError.InvalidPayloadSize)
        assertEquals(0, output.writtenBytes.size)
    }

    @Test
    fun testD_invalidBase64() = runTest {
        val decoder = FakeBase64PcmDecoder()
        val output = FakeAudioOutput()
        val engine = AudioPlaybackEngine(sessionId, decoder, output)

        engine.start()
        val result = engine.write(validFrame(payload = "BAD_BASE64"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AudioPlaybackError.DecodingFailed)
        assertEquals(0, output.writtenBytes.size)
    }

    @Test
    fun testE_wrongEncoding() = runTest {
        val engine = AudioPlaybackEngine(sessionId, FakeBase64PcmDecoder(), FakeAudioOutput())
        engine.start()
        
        val result = engine.write(validFrame(enc = "OGG_OPUS"))
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AudioPlaybackError.InvalidEncoding)
    }

    @Test
    fun testF_wrongDuration() = runTest {
        val engine = AudioPlaybackEngine(sessionId, FakeBase64PcmDecoder(), FakeAudioOutput())
        engine.start()
        
        val result = engine.write(validFrame(dur = 500))
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AudioPlaybackError.InvalidDuration)
    }

    @Test
    fun testG_sessionIsolation() = runTest {
        val engine = AudioPlaybackEngine("EXPECTED_SESSION", FakeBase64PcmDecoder(), FakeAudioOutput())
        engine.start()
        
        val result = engine.write(validFrame(sid = "DIFFERENT_SESSION"))
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AudioPlaybackError.InvalidSession)
    }

    @Test
    fun testI_writeFailure() = runTest {
        val output = FakeAudioOutput()
        output.simulateWriteError = true
        val engine = AudioPlaybackEngine(sessionId, FakeBase64PcmDecoder(), output)

        engine.start()
        val result = engine.write(validFrame())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AudioPlaybackError.WriteFailed)
    }

    @Test
    fun testJ_lifecycleSafety() = runTest {
        val output = FakeAudioOutput()
        val engine = AudioPlaybackEngine(sessionId, FakeBase64PcmDecoder(), output)

        engine.start()
        engine.start() // idempotent
        assertEquals(1, output.playCalled)

        engine.stop()
        engine.stop() // idempotent
        assertEquals(1, output.stopCalled)

        engine.release()
        engine.release() // idempotent
        assertEquals(1, output.stopCalled) // release skips stop since it's already IDLE
        assertEquals(1, output.releaseCalled)
    }

    @Test
    fun testK_writeAfterRelease() = runTest {
        val engine = AudioPlaybackEngine(sessionId, FakeBase64PcmDecoder(), FakeAudioOutput())
        
        engine.start()
        engine.release()
        
        val result = engine.write(validFrame())
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AudioPlaybackError.StateError)
    }

    @Test
    fun testL_noPayloadMutation() = runTest {
        val engine = AudioPlaybackEngine(sessionId, FakeBase64PcmDecoder(), FakeAudioOutput())
        engine.start()
        
        val frame = validFrame()
        val originalPayload = frame.payload
        
        engine.write(frame)
        
        assertEquals(originalPayload, frame.payload)
    }

    @Test
    fun testM_partialWriteHandling() = runTest {
        val output = FakeAudioOutput()
        output.simulatePartialWrites = true // forces writing to loop
        
        val engine = AudioPlaybackEngine(sessionId, FakeBase64PcmDecoder(), output)

        engine.start()
        val result = engine.write(validFrame())

        assertTrue(result.isSuccess)
        assertEquals(4000, output.writtenBytes.size)
    }
}

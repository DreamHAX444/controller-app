package com.aistudio.missioncontrol.pxytwe.audio

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take

class FakeAudioReceiverTransport : AudioReceiverTransport {
    var subscribeCount = 0
    var unsubscribeCount = 0
    var lastSubscribedTopic: String? = null
    
    val incomingFrames = MutableSharedFlow<EncodedAudioFrame>(extraBufferCapacity = 10)

    override suspend fun subscribe(topic: String): Flow<EncodedAudioFrame> {
        subscribeCount++
        lastSubscribedTopic = topic
        return incomingFrames
    }

    override suspend fun unsubscribe() {
        unsubscribeCount++
        lastSubscribedTopic = null
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AudioMonitorSessionTest {

    private val sessionId = "test-session"
    private val deviceId = "test-device"

    private fun validFrame(
        sId: String = sessionId,
        dId: String = deviceId,
        sequence: Long = 0L,
        durMs: Int = 250,
        enc: String = "PCM_16BIT_8000HZ_MONO",
        pay: String = "fakeBase64"
    ): EncodedAudioFrame {
        return EncodedAudioFrame(
            sessionId = sId,
            deviceId = dId,
            seq = sequence,
            timestamp = 1000L,
            encoding = enc,
            durationMs = durMs,
            payload = pay
        )
    }

    // ─── Test A ────────────────────────────────────────────────────────────────
    @Test
    fun testA_topicConstruction() {
        val session = AudioMonitorSession("abc", null, FakeAudioReceiverTransport(), kotlinx.coroutines.GlobalScope)
        assertEquals("media-audio-abc", session.topic)
    }

    // ─── Test B ────────────────────────────────────────────────────────────────
    @Test
    fun testB_sessionIsolation() {
        val s1 = AudioMonitorSession("session-1", null, FakeAudioReceiverTransport(), kotlinx.coroutines.GlobalScope)
        val s2 = AudioMonitorSession("session-2", null, FakeAudioReceiverTransport(), kotlinx.coroutines.GlobalScope)
        assertEquals("media-audio-session-1", s1.topic)
        assertEquals("media-audio-session-2", s2.topic)
    }

    // ─── Test C, D, E, F, G, H ──────────────────────────────────────────────────
    @Test
    fun testValidationRules() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeAudioReceiverTransport()
        val session = AudioMonitorSession(sessionId, deviceId, transport, this)
        
        val emitted = mutableListOf<EncodedAudioFrame>()
        val collectJob = launch {
            session.audioFrames.collect { emitted.add(it) }
        }

        session.start()

        // Valid frame
        transport.incomingFrames.emit(validFrame())
        assertEquals(1, emitted.size)

        // Test C - Wrong session rejected
        transport.incomingFrames.emit(validFrame(sId = "wrong-session"))
        assertEquals(1, emitted.size) // No change

        // Test E - Invalid duration rejected
        transport.incomingFrames.emit(validFrame(durMs = 500))
        assertEquals(1, emitted.size)

        // Test F - Invalid encoding rejected
        transport.incomingFrames.emit(validFrame(enc = "OPUS"))
        assertEquals(1, emitted.size)

        // Test G - Invalid sequence rejected
        transport.incomingFrames.emit(validFrame(sequence = -1L))
        assertEquals(1, emitted.size)

        // Test H - Malformed/Blank payload rejected
        transport.incomingFrames.emit(validFrame(pay = ""))
        assertEquals(1, emitted.size)

        // Device mismatch rejected
        transport.incomingFrames.emit(validFrame(dId = "wrong-device"))
        assertEquals(1, emitted.size)

        session.close()
        collectJob.cancel()
    }

    // ─── Test I ────────────────────────────────────────────────────────────────
    @Test
    fun testI_closeIdempotency() = runTest {
        val transport = FakeAudioReceiverTransport()
        val session = AudioMonitorSession(sessionId, null, transport, this)
        
        session.start()
        session.close()
        session.close() // Second call

        assertEquals(1, transport.unsubscribeCount)
        assertEquals(AudioMonitorState.CLOSED, session.state.value)
    }

    // ─── Test J ────────────────────────────────────────────────────────────────
    @Test
    fun testJ_startIdempotency() = runTest {
        val transport = FakeAudioReceiverTransport()
        val session = AudioMonitorSession(sessionId, null, transport, this)
        
        session.start()
        session.start() // Second call

        assertEquals(1, transport.subscribeCount)
        assertEquals(AudioMonitorState.STREAMING, session.state.value)
        session.close()
    }

    // ─── Test K ────────────────────────────────────────────────────────────────
    @Test
    fun testK_oldSessionProtection() = runTest(UnconfinedTestDispatcher()) {
        val oldTransport = FakeAudioReceiverTransport()
        val oldSession = AudioMonitorSession("session-old", null, oldTransport, this)
        
        val newTransport = FakeAudioReceiverTransport()
        val newSession = AudioMonitorSession("session-new", null, newTransport, this)

        val newEmitted = mutableListOf<EncodedAudioFrame>()
        val job = launch { newSession.audioFrames.collect { newEmitted.add(it) } }

        oldSession.start()
        newSession.start()

        // Emit an old frame into the new transport (should be rejected)
        newTransport.incomingFrames.emit(validFrame(sId = "session-old"))
        assertEquals(0, newEmitted.size)

        // Emit a new frame into the new transport (should be accepted)
        newTransport.incomingFrames.emit(validFrame(sId = "session-new"))
        assertEquals(1, newEmitted.size)

        oldSession.close()
        newSession.close()
        job.cancel()
    }

    // ─── Test L ────────────────────────────────────────────────────────────────
    @Test
    fun testL_outOfOrderFramesPreserved() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeAudioReceiverTransport()
        val session = AudioMonitorSession(sessionId, null, transport, this)
        
        val emitted = mutableListOf<EncodedAudioFrame>()
        val job = launch { session.audioFrames.collect { emitted.add(it) } }

        session.start()

        // seq 0, 2, 1
        transport.incomingFrames.emit(validFrame(sequence = 0L))
        transport.incomingFrames.emit(validFrame(sequence = 2L))
        transport.incomingFrames.emit(validFrame(sequence = 1L))

        assertEquals(3, emitted.size)
        assertEquals(0L, emitted[0].seq)
        assertEquals(2L, emitted[1].seq)
        assertEquals(1L, emitted[2].seq)

        session.close()
        job.cancel()
    }
}

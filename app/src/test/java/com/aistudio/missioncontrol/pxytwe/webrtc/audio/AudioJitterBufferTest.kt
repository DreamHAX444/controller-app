package com.aistudio.missioncontrol.pxytwe.webrtc.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioJitterBufferTest {

    private var mockTimeNs = 1000L // Start > 0 so missingSequenceWaitStartNs doesn't get set to 0
    private val clock = { mockTimeNs }

    private fun createPacket(seq: Long): PlaybackAudioPacket {
        return PlaybackAudioPacket(
            sessionId = "s",
            deviceId = "d",
            sequence = seq,
            captureTimestampNs = seq * 1000,
            payload = ByteArray(1920) { seq.toByte() }
        )
    }

    @Test
    fun `buffers until startup frames reached`() {
        val buffer = AudioJitterBuffer(maxFrames = 10, startupFrames = 3, missingFrameTimeoutMs = 40, clock = clock)
        
        assertEquals(JitterBufferState.IDLE, buffer.state)
        
        buffer.push(createPacket(10))
        assertEquals(JitterBufferState.BUFFERING, buffer.state)
        assertNull(buffer.pullNextFrame())

        buffer.push(createPacket(11))
        assertEquals(JitterBufferState.BUFFERING, buffer.state)
        assertNull(buffer.pullNextFrame())

        buffer.push(createPacket(12))
        assertEquals(JitterBufferState.PLAYING, buffer.state)
        
        val p10 = buffer.pullNextFrame()
        assertEquals(10L, p10?.sequence)
        
        val p11 = buffer.pullNextFrame()
        assertEquals(11L, p11?.sequence)
        
        val p12 = buffer.pullNextFrame()
        assertEquals(12L, p12?.sequence)
        
        assertNull(buffer.pullNextFrame()) // This triggers STARVED
        assertEquals(JitterBufferState.STARVED, buffer.state)
    }

    @Test
    fun `reorders frames correctly`() {
        val buffer = AudioJitterBuffer(maxFrames = 10, startupFrames = 3, missingFrameTimeoutMs = 40, clock = clock)
        
        buffer.push(createPacket(10))
        buffer.push(createPacket(12))
        buffer.push(createPacket(11))
        
        assertEquals(JitterBufferState.PLAYING, buffer.state)
        assertEquals(10L, buffer.pullNextFrame()?.sequence)
        assertEquals(11L, buffer.pullNextFrame()?.sequence)
        assertEquals(12L, buffer.pullNextFrame()?.sequence)
    }

    @Test
    fun `drops late frames`() {
        val buffer = AudioJitterBuffer(maxFrames = 10, startupFrames = 3, missingFrameTimeoutMs = 40, clock = clock)
        
        buffer.push(createPacket(10))
        buffer.push(createPacket(11))
        buffer.push(createPacket(12))
        
        buffer.pullNextFrame() // pulls 10, next expected is 11
        
        // Late frame 9 arrives
        buffer.push(createPacket(9))
        assertEquals(1, buffer.lateFrames)
        
        // Only 11 and 12 are left
        assertEquals(11L, buffer.pullNextFrame()?.sequence)
        assertEquals(12L, buffer.pullNextFrame()?.sequence)
    }

    @Test
    fun `skips missing frame after timeout`() {
        val buffer = AudioJitterBuffer(maxFrames = 10, startupFrames = 3, missingFrameTimeoutMs = 40, clock = clock)
        
        buffer.push(createPacket(10))
        buffer.push(createPacket(12))
        buffer.push(createPacket(13))
        
        assertEquals(10L, buffer.pullNextFrame()?.sequence) // Next expected is 11
        
        // 11 is missing. Pull should return null and start timer.
        assertNull(buffer.pullNextFrame())
        
        // Advance time by 39ms
        mockTimeNs += 39_000_000
        assertNull(buffer.pullNextFrame())
        
        // Advance time by 2ms (total 41ms >= 40ms)
        mockTimeNs += 2_000_000
        
        val skippedTo = buffer.pullNextFrame()
        assertEquals(12L, skippedTo?.sequence)
        assertEquals(1, buffer.missingFramesSkipped)
        
        assertEquals(13L, buffer.pullNextFrame()?.sequence)
    }

    @Test
    fun `drops duplicates`() {
        val buffer = AudioJitterBuffer(maxFrames = 10, startupFrames = 3, missingFrameTimeoutMs = 40, clock = clock)
        
        buffer.push(createPacket(10))
        buffer.push(createPacket(10))
        
        assertEquals(1, buffer.duplicates)
    }

    @Test
    fun `enforces max bounds by dropping oldest`() {
        val buffer = AudioJitterBuffer(maxFrames = 3, startupFrames = 3, missingFrameTimeoutMs = 40, clock = clock)
        
        buffer.push(createPacket(10))
        buffer.push(createPacket(11))
        buffer.push(createPacket(12))
        buffer.push(createPacket(13)) // Overflows, drops oldest (10)
        
        assertEquals(1, buffer.bufferOverflows)
        
        // It should have fast-forwarded the expected sequence to 11
        val p11 = buffer.pullNextFrame()
        assertEquals(11L, p11?.sequence)
        assertEquals(12L, buffer.pullNextFrame()?.sequence)
        assertEquals(13L, buffer.pullNextFrame()?.sequence)
    }
}

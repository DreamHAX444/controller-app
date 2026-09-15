package com.aistudio.missioncontrol.pxytwe.webrtc.audio

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

class PlaybackAudioPacketTest {

    @Test
    fun `encode and decode yields identical packet`() {
        val original = PlaybackAudioPacket(
            sessionId = "session-123",
            deviceId = "device-456",
            sequence = 42L,
            captureTimestampNs = 1234567890L,
            payload = ByteArray(1920) { it.toByte() }
        )

        val buffer = PlaybackAudioPacketSerializer.encode(original)
        val decoded = PlaybackAudioPacketSerializer.decode(buffer)

        assertEquals(original, decoded)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects invalid payload size on decode`() {
        val original = PlaybackAudioPacket(
            sessionId = "s",
            deviceId = "d",
            sequence = 0,
            captureTimestampNs = 0,
            payload = ByteArray(100) // Invalid size
        )
        
        val buffer = PlaybackAudioPacketSerializer.encode(original)
        PlaybackAudioPacketSerializer.decode(buffer)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects invalid version`() {
        val original = PlaybackAudioPacket(
            sessionId = "s",
            deviceId = "d",
            sequence = 0,
            captureTimestampNs = 0,
            payload = ByteArray(1920)
        )
        val buffer = PlaybackAudioPacketSerializer.encode(original)
        
        buffer.put(0, 99.toByte())
        
        PlaybackAudioPacketSerializer.decode(buffer)
    }
}

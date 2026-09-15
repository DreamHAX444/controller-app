package com.aistudio.missioncontrol.pxytwe.webrtc.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class PlaybackAudioPacket(
    val version: Byte = 1,
    val sessionId: String,
    val deviceId: String,
    val sequence: Long,
    val captureTimestampNs: Long,
    val sampleRate: Int = 48000,
    val channels: Byte = 1,
    val encoding: Byte = 2,
    val durationMs: Int = 20,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PlaybackAudioPacket

        if (version != other.version) return false
        if (sessionId != other.sessionId) return false
        if (deviceId != other.deviceId) return false
        if (sequence != other.sequence) return false
        if (captureTimestampNs != other.captureTimestampNs) return false
        if (sampleRate != other.sampleRate) return false
        if (channels != other.channels) return false
        if (encoding != other.encoding) return false
        if (durationMs != other.durationMs) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = version.toInt()
        result = 31 * result + sessionId.hashCode()
        result = 31 * result + deviceId.hashCode()
        result = 31 * result + sequence.hashCode()
        result = 31 * result + captureTimestampNs.hashCode()
        result = 31 * result + sampleRate
        result = 31 * result + channels
        result = 31 * result + encoding
        result = 31 * result + durationMs
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

object PlaybackAudioPacketSerializer {
    const val EXPECTED_PAYLOAD_SIZE = 1920

    fun encode(packet: PlaybackAudioPacket): ByteBuffer {
        val sessionBytes = packet.sessionId.toByteArray(Charsets.UTF_8)
        val deviceBytes = packet.deviceId.toByteArray(Charsets.UTF_8)
        
        val totalSize = 1 + // version
            4 + sessionBytes.size + // sessionId length + bytes
            4 + deviceBytes.size + // deviceId length + bytes
            8 + // sequence
            8 + // captureTimestampNs
            4 + // sampleRate
            1 + // channels
            1 + // encoding
            4 + // durationMs
            4 + // payload size
            packet.payload.size

        val buffer = ByteBuffer.allocateDirect(totalSize)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(packet.version)
        
        buffer.putInt(sessionBytes.size)
        buffer.put(sessionBytes)
        
        buffer.putInt(deviceBytes.size)
        buffer.put(deviceBytes)
        
        buffer.putLong(packet.sequence)
        buffer.putLong(packet.captureTimestampNs)
        buffer.putInt(packet.sampleRate)
        buffer.put(packet.channels)
        buffer.put(packet.encoding)
        buffer.putInt(packet.durationMs)
        
        buffer.putInt(packet.payload.size)
        buffer.put(packet.payload)

        buffer.flip()
        return buffer
    }

    fun decode(buffer: ByteBuffer): PlaybackAudioPacket {
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        val version = buffer.get()
        require(version == 1.toByte()) { "Unsupported packet version: $version" }

        val sessionLen = buffer.getInt()
        require(sessionLen in 0..1024) { "Invalid session length: $sessionLen" }
        val sessionBytes = ByteArray(sessionLen)
        buffer.get(sessionBytes)
        val sessionId = String(sessionBytes, Charsets.UTF_8)

        val deviceLen = buffer.getInt()
        require(deviceLen in 0..1024) { "Invalid device length: $deviceLen" }
        val deviceBytes = ByteArray(deviceLen)
        buffer.get(deviceBytes)
        val deviceId = String(deviceBytes, Charsets.UTF_8)

        val sequence = buffer.getLong()
        require(sequence >= 0) { "Invalid sequence: $sequence" }

        val captureTimestampNs = buffer.getLong()
        val sampleRate = buffer.getInt()
        val channels = buffer.get()
        val encoding = buffer.get()
        val durationMs = buffer.getInt()

        val payloadLen = buffer.getInt()
        require(payloadLen == EXPECTED_PAYLOAD_SIZE) { "Invalid payload length: $payloadLen, expected $EXPECTED_PAYLOAD_SIZE" }
        
        val payload = ByteArray(payloadLen)
        buffer.get(payload)

        return PlaybackAudioPacket(
            version = version,
            sessionId = sessionId,
            deviceId = deviceId,
            sequence = sequence,
            captureTimestampNs = captureTimestampNs,
            sampleRate = sampleRate,
            channels = channels,
            encoding = encoding,
            durationMs = durationMs,
            payload = payload
        )
    }
}

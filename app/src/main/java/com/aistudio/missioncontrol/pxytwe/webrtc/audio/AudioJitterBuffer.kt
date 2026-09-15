package com.aistudio.missioncontrol.pxytwe.webrtc.audio

import android.util.Log
import java.util.concurrent.ConcurrentSkipListMap

enum class JitterBufferState {
    IDLE,
    BUFFERING,
    PLAYING,
    STARVED,
    STOPPED
}

class AudioJitterBuffer(
    private val maxFrames: Int = 10,
    private val startupFrames: Int = 3,
    private val missingFrameTimeoutMs: Long = 40,
    private val clock: () -> Long = { System.nanoTime() }
) {
    private val tag = "AudioJitterBuffer"

    private val buffer = ConcurrentSkipListMap<Long, PlaybackAudioPacket>()

    @Volatile
    var state = JitterBufferState.IDLE
        private set

    var expectedSequence = -1L
        private set

    private var missingSequenceWaitStartNs = 0L

    var framesReceived = 0L
        private set
    var framesPlayed = 0L
        private set
    var framesDropped = 0L
        private set
    var duplicates = 0L
        private set
    var lateFrames = 0L
        private set
    var bufferUnderruns = 0L
        private set
    var bufferOverflows = 0L
        private set
    var missingFramesSkipped = 0L
        private set

    fun push(packet: PlaybackAudioPacket) {
        if (state == JitterBufferState.STOPPED) return

        framesReceived++
        val seq = packet.sequence

        if (expectedSequence != -1L && seq < expectedSequence) {
            // Late frame
            lateFrames++
            framesDropped++
            return
        }

        if (buffer.containsKey(seq)) {
            duplicates++
            framesDropped++
            return
        }

        buffer[seq] = packet

        // Enforce max bounds (drop oldest)
        while (buffer.size > maxFrames) {
            val oldest = buffer.pollFirstEntry()
            if (oldest != null) {
                bufferOverflows++
                framesDropped++
                // Fast forward expected sequence if we drop something from the front
                if (oldest.key >= expectedSequence) {
                    expectedSequence = oldest.key + 1
                    missingSequenceWaitStartNs = 0L // reset wait because expected changed
                }
            }
        }

        // State transitions
        if (state == JitterBufferState.IDLE || state == JitterBufferState.STARVED) {
            state = JitterBufferState.BUFFERING
        }

        if (state == JitterBufferState.BUFFERING && buffer.size >= startupFrames) {
            state = JitterBufferState.PLAYING
            if (expectedSequence == -1L) {
                expectedSequence = buffer.firstKey()
            }
        }
    }

    fun pullNextFrame(): PlaybackAudioPacket? {
        if (state != JitterBufferState.PLAYING) {
            return null
        }

        if (buffer.isEmpty()) {
            state = JitterBufferState.STARVED
            bufferUnderruns++
            return null
        }

        // Check if we have the expected frame
        if (buffer.containsKey(expectedSequence)) {
            val packet = buffer.remove(expectedSequence)
            expectedSequence++
            missingSequenceWaitStartNs = 0L
            framesPlayed++
            return packet
        }

        // We don't have expectedSequence, but buffer is not empty (future frames exist).
        // Wait for bounded timeout before skipping.
        if (missingSequenceWaitStartNs == 0L) {
            missingSequenceWaitStartNs = clock()
            return null
        }

        val elapsedMs = (clock() - missingSequenceWaitStartNs) / 1_000_000
        if (elapsedMs >= missingFrameTimeoutMs) {
            // Deadline expired. Skip to the next available sequence.
            val nextAvail = buffer.firstKey()
            val packet = buffer.remove(nextAvail)
            missingFramesSkipped += (nextAvail - expectedSequence)
            expectedSequence = nextAvail + 1
            missingSequenceWaitStartNs = 0L
            framesPlayed++
            Log.d(tag, "Missing frame timeout expired. Skipped to $nextAvail.")
            return packet
        }

        // Still waiting for missing frame
        return null
    }

    fun stop() {
        state = JitterBufferState.STOPPED
        buffer.clear()
    }

    fun clear() {
        buffer.clear()
        expectedSequence = -1L
        missingSequenceWaitStartNs = 0L
        state = JitterBufferState.IDLE
    }
}

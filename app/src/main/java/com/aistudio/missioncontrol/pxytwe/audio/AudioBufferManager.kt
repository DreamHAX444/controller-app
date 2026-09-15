package com.aistudio.missioncontrol.pxytwe.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.TreeMap

/**
 * State of the AudioBufferManager.
 */
enum class AudioBufferState {
    IDLE,
    BUFFERING,
    PLAYING,
    CLOSED
}

/**
 * Controller-side audio buffering and ordering layer.
 * 
 * Responsibilities:
 * - Owns EXACTLY one logical audio session.
 * - Enforces session isolation.
 * - Handles sequences (ordering), drops duplicates and late frames.
 * - Buffers frames during startup to reach a target threshold (e.g. 4 frames = 1 second).
 * - Discards missing frames if they do not arrive within a timeout to prevent permanent stalling.
 * - Enforces a maximum buffer capacity (e.g. 10 frames = 2.5 seconds) to prevent unbounded memory growth.
 * - Emits ordered, strictly validated frames to a SharedFlow for playback.
 */
class AudioBufferManager(
    val sessionId: String,
    private val scope: CoroutineScope
) {
    // Capacity matches the maximum buffer plus some breathing room
    private val _outputFlow = MutableSharedFlow<EncodedAudioFrame>(extraBufferCapacity = 16)
    val outputFlow: SharedFlow<EncodedAudioFrame> = _outputFlow.asSharedFlow()

    private val mutex = Mutex()
    
    // TreeMap maintains sequence-based ordering naturally
    private val buffer = TreeMap<Long, EncodedAudioFrame>()
    
    var nextSequenceToEmit: Long? = null
        private set
        
    var state = AudioBufferState.IDLE
        private set

    private var collectJob: Job? = null
    private var missingFrameJob: Job? = null

    companion object {
        const val STARTUP_TARGET_FRAMES = 4
        const val MAX_BUFFERED_FRAMES = 10
        const val MISSING_FRAME_TIMEOUT_MS = 500L
    }

    /**
     * Idempotent start. Subscribes to the incoming stream.
     */
    suspend fun start(incomingStream: Flow<EncodedAudioFrame>) {
        mutex.withLock {
            if (state != AudioBufferState.IDLE) return
            state = AudioBufferState.BUFFERING
            
            collectJob = scope.launch {
                incomingStream.collect { frame ->
                    processIncomingFrame(frame)
                }
            }
        }
    }

    private suspend fun processIncomingFrame(frame: EncodedAudioFrame) {
        mutex.withLock {
            if (state == AudioBufferState.CLOSED) return
            
            // 1. Double-check session isolation. Step 6 defense in depth.
            if (frame.sessionId != sessionId) return
            
            // 2. Late Frame / Future Frame / Duplicate Filtering
            val expectedSeq = nextSequenceToEmit
            if (expectedSeq != null && frame.seq < expectedSeq) {
                // Late frame: we've already emitted past this sequence. Discard.
                return
            }
            if (buffer.containsKey(frame.seq)) {
                // Duplicate frame. Discard.
                return
            }

            // 3. Store frame
            buffer[frame.seq] = frame

            // 4. Maximum Buffer Overflow Policy
            // If the buffer grows beyond our bounded maximum (consumer is slow or massive frame loss),
            // we must drop the oldest buffered frame to preserve playback continuity and prevent OOM.
            if (buffer.size > MAX_BUFFERED_FRAMES) {
                val oldestKey = buffer.firstKey()
                buffer.remove(oldestKey)
                
                // If we dropped the frame we were waiting for, we must advance the sequence
                if (expectedSeq != null && expectedSeq <= oldestKey) {
                    val nextAvailable = if (buffer.isNotEmpty()) buffer.firstKey() else oldestKey + 1
                    nextSequenceToEmit = nextAvailable
                }
            }

            // 5. Evaluate state and output
            checkOutput()
        }
    }

    private fun checkOutput() {
        if (state == AudioBufferState.BUFFERING) {
            // Wait for startup target to be reached
            if (buffer.size >= STARTUP_TARGET_FRAMES) {
                state = AudioBufferState.PLAYING
                nextSequenceToEmit = buffer.firstKey()
            } else {
                return // Continue buffering
            }
        }

        if (state == AudioBufferState.PLAYING) {
            flushAvailableFrames()
            
            // Missing Frame Policy Evaluation
            // If we have frames buffered but are missing the EXACT expected sequence, we wait up to a timeout.
            if (buffer.isNotEmpty() && !buffer.containsKey(nextSequenceToEmit)) {
                startMissingFrameTimeout()
            } else {
                cancelMissingFrameTimeout()
            }
        }
    }

    private fun flushAvailableFrames() {
        var expected = nextSequenceToEmit ?: return
        while (buffer.containsKey(expected)) {
            val frameToEmit = buffer.remove(expected)!!
            
            // Output API Backpressure
            val emitted = _outputFlow.tryEmit(frameToEmit)
            if (!emitted) {
                // Consumer (Playback Layer) is blocked. Put it back and pause emission.
                // We do not drop it here; MAX_BUFFERED_FRAMES handles bounded growth.
                buffer[expected] = frameToEmit
                break
            }
            
            expected++
            nextSequenceToEmit = expected
        }
    }

    private fun startMissingFrameTimeout() {
        if (missingFrameJob?.isActive == true) return
        
        missingFrameJob = scope.launch {
            delay(MISSING_FRAME_TIMEOUT_MS)
            
            mutex.withLock {
                if (state == AudioBufferState.PLAYING && buffer.isNotEmpty()) {
                    val expected = nextSequenceToEmit
                    if (expected != null && !buffer.containsKey(expected)) {
                        // Timeout reached! Skip to the next available sequence rather than blocking forever.
                        val nextAvailable = buffer.firstKey()
                        if (nextAvailable > expected) {
                            nextSequenceToEmit = nextAvailable
                            flushAvailableFrames()
                            
                            // Re-evaluate if we are STILL missing the next contiguous sequence
                            if (buffer.isNotEmpty() && !buffer.containsKey(nextSequenceToEmit)) {
                                startMissingFrameTimeout()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun cancelMissingFrameTimeout() {
        missingFrameJob?.cancel()
        missingFrameJob = null
    }

    /**
     * Idempotent close. Clears buffer, resets state, and stops all jobs.
     * Guarantees no stale frames leak into a subsequent session.
     */
    suspend fun close() {
        mutex.withLock {
            if (state == AudioBufferState.CLOSED) return
            state = AudioBufferState.CLOSED
            
            collectJob?.cancel()
            collectJob = null
            
            cancelMissingFrameTimeout()
            
            buffer.clear()
            nextSequenceToEmit = null
        }
    }
}

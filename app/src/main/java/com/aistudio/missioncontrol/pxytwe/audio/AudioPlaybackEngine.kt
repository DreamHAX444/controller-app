package com.aistudio.missioncontrol.pxytwe.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Decouples Base64 decoding for testability on the JVM.
 */
interface Base64PcmDecoder {
    fun decode(base64Payload: String): ByteArray
}

class AndroidBase64PcmDecoder : Base64PcmDecoder {
    override fun decode(base64Payload: String): ByteArray {
        return Base64.decode(base64Payload, Base64.NO_WRAP)
    }
}

/**
 * Decouples actual Android AudioTrack playback for JVM unit testing.
 */
interface AudioOutput {
    fun play()
    fun write(pcmBytes: ByteArray, offsetInBytes: Int, sizeInBytes: Int): Int
    fun stop()
    fun release()
}

class AndroidAudioTrackOutput : AudioOutput {
    private var audioTrack: AudioTrack? = null

    init {
        // Step 7 Exact Specification
        // Sample rate: 8000 Hz, Channels: 1 (mono), Encoding: PCM 16-bit
        val sampleRate = 8000
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        
        // Ensure buffer is large enough for continuous 250ms writes (4000 bytes)
        // 8000 bytes allows double-buffering.
        val bufferSize = maxOf(minBufferSize, 8000)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    override fun play() {
        audioTrack?.play()
    }

    override fun write(pcmBytes: ByteArray, offsetInBytes: Int, sizeInBytes: Int): Int {
        // WRITE_BLOCKING ensures we block until space is available in the track buffer
        return audioTrack?.write(pcmBytes, offsetInBytes, sizeInBytes, AudioTrack.WRITE_BLOCKING) ?: -1
    }

    override fun stop() {
        try {
            audioTrack?.stop()
            audioTrack?.flush()
        } catch (e: IllegalStateException) {
            // Ignore if not initialized properly
        }
    }

    override fun release() {
        try {
            audioTrack?.release()
        } catch (e: Exception) {
            // Ignore errors during tear down
        } finally {
            audioTrack = null
        }
    }
}

enum class AudioPlaybackState {
    IDLE,
    STARTED,
    STOPPING,
    RELEASED
}

sealed class AudioPlaybackError(message: String) : Exception(message) {
    class InvalidEncoding(encoding: String) : AudioPlaybackError("Unsupported encoding: $encoding")
    class InvalidDuration(duration: Int) : AudioPlaybackError("Unsupported duration: $duration")
    class InvalidSession(sessionId: String) : AudioPlaybackError("Session ID mismatch: $sessionId")
    class InvalidPayloadSize(size: Int) : AudioPlaybackError("Invalid decoded payload size: $size")
    class EmptyPayload : AudioPlaybackError("Payload is blank")
    class DecodingFailed : AudioPlaybackError("Base64 decoding failed")
    class WriteFailed : AudioPlaybackError("AudioTrack write failed")
    class StateError(state: AudioPlaybackState) : AudioPlaybackError("Engine in invalid state: $state")
}

/**
 * Controller-side audio decoding and playback engine.
 * Receives strictly ordered frames from the AudioBufferManager.
 * Enforces schema validation, byte integrity, and Android AudioTrack lifecycles.
 */
class AudioPlaybackEngine(
    private val expectedSessionId: String,
    private val decoder: Base64PcmDecoder = AndroidBase64PcmDecoder(),
    private val output: AudioOutput = AndroidAudioTrackOutput()
) {

    private val mutex = Mutex()
    var state = AudioPlaybackState.IDLE
        private set

    companion object {
        const val EXPECTED_PCM_SIZE = 4000
        const val EXPECTED_ENCODING = "PCM_16BIT_8000HZ_MONO"
        const val EXPECTED_DURATION_MS = 250
        const val MAX_ZERO_WRITES = 5
    }

    /**
     * Prepares and starts the audio output.
     */
    suspend fun start() {
        mutex.withLock {
            if (state == AudioPlaybackState.RELEASED) {
                throw AudioPlaybackError.StateError(state)
            }
            if (state == AudioPlaybackState.STARTED) return
            
            output.play()
            state = AudioPlaybackState.STARTED
        }
    }

    /**
     * Validates, decodes, and writes a single audio frame to the output.
     * Blocks (on the IO dispatcher) until the entire frame is written or a bounded failure occurs.
     */
    suspend fun write(frame: EncodedAudioFrame): Result<Unit> = withContext(Dispatchers.IO) {
        // Ensure state is started
        mutex.withLock {
            if (state != AudioPlaybackState.STARTED) {
                return@withContext Result.failure(AudioPlaybackError.StateError(state))
            }
        }

        // 1. Validate Session Isolation
        if (frame.sessionId != expectedSessionId) {
            return@withContext Result.failure(AudioPlaybackError.InvalidSession(frame.sessionId))
        }

        // 2. Validate Duration
        if (frame.durationMs != EXPECTED_DURATION_MS) {
            return@withContext Result.failure(AudioPlaybackError.InvalidDuration(frame.durationMs))
        }

        // 3. Validate Encoding
        if (frame.encoding != EXPECTED_ENCODING) {
            return@withContext Result.failure(AudioPlaybackError.InvalidEncoding(frame.encoding))
        }

        // 4. Validate Payload isn't blank
        if (frame.payload.isBlank()) {
            return@withContext Result.failure(AudioPlaybackError.EmptyPayload())
        }

        // 5. Decode Base64 Payload
        val decodedBytes = try {
            decoder.decode(frame.payload)
        } catch (e: Exception) {
            return@withContext Result.failure(AudioPlaybackError.DecodingFailed())
        }

        // 6. Validate PCM Size
        if (decodedBytes.size != EXPECTED_PCM_SIZE) {
            return@withContext Result.failure(AudioPlaybackError.InvalidPayloadSize(decodedBytes.size))
        }

        // 7. Write strictly ordered decoded PCM to AudioOutput handling partial writes
        var offset = 0
        var remaining = EXPECTED_PCM_SIZE
        var zeroWriteCount = 0

        while (remaining > 0) {
            // Check state again midway. If released concurrently, abort safely.
            mutex.withLock {
                if (state != AudioPlaybackState.STARTED) {
                    return@withContext Result.failure(AudioPlaybackError.StateError(state))
                }
            }

            val written = output.write(decodedBytes, offset, remaining)
            
            if (written < 0) {
                return@withContext Result.failure(AudioPlaybackError.WriteFailed())
            }
            
            if (written == 0) {
                // Bounded failure: prevent infinite busy-looping
                zeroWriteCount++
                if (zeroWriteCount > MAX_ZERO_WRITES) {
                    return@withContext Result.failure(AudioPlaybackError.WriteFailed())
                }
                delay(10) // Brief suspension before retry
            } else {
                zeroWriteCount = 0
                offset += written
                remaining -= written
            }
        }

        Result.success(Unit)
    }

    /**
     * Stops playback but allows restarting.
     */
    suspend fun stop() {
        mutex.withLock {
            if (state == AudioPlaybackState.RELEASED || state == AudioPlaybackState.IDLE) return
            
            state = AudioPlaybackState.STOPPING
            output.stop()
            state = AudioPlaybackState.IDLE
        }
    }

    /**
     * Cleans up all audio resources permanently. Safe to call multiple times.
     */
    suspend fun release() {
        mutex.withLock {
            if (state == AudioPlaybackState.RELEASED) return
            
            if (state != AudioPlaybackState.IDLE) {
                state = AudioPlaybackState.STOPPING
                output.stop()
            }
            output.release()
            state = AudioPlaybackState.RELEASED
        }
    }
}

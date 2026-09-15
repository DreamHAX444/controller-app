package com.aistudio.missioncontrol.pxytwe.audio

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Transport-ready serialized audio frame representation from the Tracker.
 */
@Serializable
data class EncodedAudioFrame(
    @SerialName("session_id") val sessionId: String,
    @SerialName("device_id") val deviceId: String,
    val seq: Long,
    val timestamp: Long,
    val encoding: String,
    @SerialName("duration_ms") val durationMs: Int,
    val payload: String,
)

/**
 * Lifecycle state of the Controller-side Audio Monitor.
 */
enum class AudioMonitorState {
    IDLE,
    CONNECTING,
    STREAMING,
    DISCONNECTING,
    CLOSED
}

/**
 * Interface representing the outbound/inbound Realtime receiver transport.
 * Allows decoupling the Supabase SDK for JVM unit testing.
 */
interface AudioReceiverTransport {
    suspend fun subscribe(topic: String): Flow<EncodedAudioFrame>
    suspend fun unsubscribe()
}

/**
 * Supabase implementation of the Realtime audio receiver.
 */
class SupabaseAudioReceiverTransport(
    private val supabase: SupabaseClient
) : AudioReceiverTransport {
    private var channel: RealtimeChannel? = null

    override suspend fun subscribe(topic: String): Flow<EncodedAudioFrame> {
        // Defensively remove any existing orphaned channel with the exact topic
        supabase.realtime.subscriptions.values.find { it.topic == topic }?.let { existing ->
            supabase.realtime.removeChannel(existing)
        }
        val newChannel = supabase.channel(topic)
        val flow = newChannel.broadcastFlow<EncodedAudioFrame>(event = "audio_frame")
        
        newChannel.subscribe(blockUntilSubscribed = true)
        channel = newChannel
        return flow
    }

    override suspend fun unsubscribe() {
        channel?.let { supabase.realtime.removeChannel(it) }
        channel = null
    }
}

/**
 * Manages the Controller-side Realtime inbound stream for a single audio session.
 * 
 * Responsibilities:
 * - Creates a single Realtime subscription to `media-audio-{sessionId}`.
 * - Extracts and validates incoming `EncodedAudioFrame` payloads.
 * - Enforces session isolation (rejects frames from other sessions).
 * - Exposes validated frames via a SharedFlow.
 * - Provides idempotent start/stop logic and clean shutdown.
 */
class AudioMonitorSession(
    val sessionId: String,
    val expectedDeviceId: String?,
    private val transport: AudioReceiverTransport,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(AudioMonitorState.IDLE)
    val state: StateFlow<AudioMonitorState> = _state.asStateFlow()

    // 64 frames = ~16 seconds of 250ms audio buffering room before blocking/dropping
    private val _audioFrames = MutableSharedFlow<EncodedAudioFrame>(extraBufferCapacity = 64)
    val audioFrames: SharedFlow<EncodedAudioFrame> = _audioFrames.asSharedFlow()

    private val mutex = Mutex()
    private var collectJob: Job? = null

    val topic = "media-audio-$sessionId"

    /**
     * Idempotent connect. Subscribes to the transport and starts collecting frames.
     */
    suspend fun start() {
        mutex.withLock {
            val currentState = _state.value
            if (currentState == AudioMonitorState.STREAMING || currentState == AudioMonitorState.CONNECTING) {
                return
            }
            if (currentState == AudioMonitorState.CLOSED || currentState == AudioMonitorState.DISCONNECTING) {
                throw IllegalStateException("Cannot start a closed session")
            }

            _state.value = AudioMonitorState.CONNECTING
            try {
                val flow = transport.subscribe(topic)
                
                collectJob = scope.launch {
                    try {
                        flow.collect { frame ->
                            if (validateFrame(frame)) {
                                _audioFrames.tryEmit(frame)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Silent collection failure. Error handling/reconnect belongs to orchestrator.
                    }
                }
                
                _state.value = AudioMonitorState.STREAMING
            } catch (e: CancellationException) {
                _state.value = AudioMonitorState.IDLE
                throw e
            } catch (e: Exception) {
                _state.value = AudioMonitorState.IDLE
                throw e
            }
        }
    }

    private fun validateFrame(frame: EncodedAudioFrame): Boolean {
        // 1. Session Isolation: Reject wrong session
        if (frame.sessionId != sessionId) return false

        // 2. Device Identity: Reject blank device IDs or mismatched expected device IDs
        if (frame.deviceId.isBlank()) return false
        if (expectedDeviceId != null && frame.deviceId != expectedDeviceId) return false

        // 3. Sequence: Reject negative sequence (but DO NOT discard out-of-order positive sequences)
        if (frame.seq < 0) return false

        // 4. Encoding: Require exactly the supported protocol
        if (frame.encoding != "PCM_16BIT_8000HZ_MONO") return false

        // 5. Duration: Require exactly 250ms
        if (frame.durationMs != 250) return false

        // 6. Payload: Require non-empty payload
        if (frame.payload.isBlank()) return false

        return true
    }

    /**
     * Idempotent close. Unsubscribes transport, cancels collection, and transitions to CLOSED.
     */
    suspend fun close() {
        mutex.withLock {
            val currentState = _state.value
            if (currentState == AudioMonitorState.CLOSED || currentState == AudioMonitorState.DISCONNECTING) {
                return
            }

            _state.value = AudioMonitorState.DISCONNECTING
            try {
                collectJob?.cancel()
                collectJob = null
                
                transport.unsubscribe()
            } catch (e: Exception) {
                // Ignore cleanup errors
            } finally {
                _state.value = AudioMonitorState.CLOSED
            }
        }
    }
}

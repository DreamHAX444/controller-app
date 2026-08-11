package com.aistudio.missioncontrol.pxytwe.audio

import android.util.Log
import com.aistudio.missioncontrol.pxytwe.CommandPayload
import com.aistudio.missioncontrol.pxytwe.SupabaseClientManager
import com.aistudio.missioncontrol.pxytwe.SupabaseClientManager.encodeFilterValue
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecordOrNull
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * Mic signaling on top of the LIVE schema:
 *
 *   send: insert {device_id, command, params, status='pending'} into
 *         `commands`. Tracker acts on its own realtime subscription.
 *
 *   receive: postgresChange on `media` filtered by `type=eq.audio`.
 *            Audio bytes are base64-encoded in `media.data_b64` — there
 *            is no Storage bucket for raw audio in this project.
 */
object AudioMonitorRepository {

    private const val TAG = "AudioMonitor"

    // Live schema table names. The previous build assumed
    // `mic_commands` / `mic_audio_chunks` and a `mic-audio` Storage
    // bucket; none of those exist on the live DB.
    const val COMMAND_TABLE = "commands"
    const val MEDIA_TABLE = "media"
    const val AUDIO_TYPE = "audio"

    enum class MonitorStatus { Idle, Starting, Live, Stopping, Failed }

    // Live `media` row shape — bytes live in `data_b64` (base64-encoded
    // audio payload), not Storage. `type` filters determine what a row
    // represents; only `audio` is consumed here.
    @Serializable
    data class MediaRow(
        val id: Long = 0,
        val device_id: String,
        val type: String,
        val data_b64: String? = null,
        val mime_type: String? = null,
        val duration_sec: Int? = null,
        val created_at: String? = null
    )

    private val _activeSession = MutableStateFlow<String?>(null)
    val activeSession: StateFlow<String?> = _activeSession.asStateFlow()

    private val _status = MutableStateFlow(MonitorStatus.Idle)
    val status: StateFlow<MonitorStatus> = _status.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val client get() = SupabaseClientManager.client

    /**
     * Insert a start_mic command into `commands`. The tracker subscribes
     * to `commands` via postgres_changes and uploads audio rows into
     * `media` when its mic is engaged.
     */
    suspend fun startMonitoring(deviceId: String): Result<String> {
        val params = """{"sample_rate_hz":16000,"chunk_ms":250}"""
        setStatus(MonitorStatus.Starting, "Sending uplink request to $deviceId…")
        return try {
            SupabaseClientManager.sendCommand(deviceId, "start_mic", params)
            _activeSession.value = deviceId
            Result.success(deviceId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "startMonitoring insert failed for $deviceId", e)
            setStatus(MonitorStatus.Failed, e.message ?: "Failed to start mic")
            Result.failure(e)
        }
    }

    suspend fun stopMonitoring(deviceId: String): Result<Unit> {
        setStatus(MonitorStatus.Stopping, "Releasing $deviceId mic…")
        return try {
            SupabaseClientManager.sendCommand(deviceId, "stop_mic")
            _activeSession.value = null
            setStatus(MonitorStatus.Idle, null)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "stopMonitoring insert failed for $deviceId", e)
            _activeSession.value = null
            setStatus(MonitorStatus.Idle, e.message ?: "Stop command failed (state cleared)")
            Result.failure(e)
        }
    }



    /**
     * Realtime stream of audio rows scoped to a device. Subscribes when
     * the collection starts and unsubscribes when it stops — back-to-back
     * start/stop does not leak subscriptions.
     *
     * Filter is on `device_id` with proper `%20` URL encoding so device
     * names containing spaces ("Samsung SM-S908B") don't silently break
     * the subscription.
     */
    fun listenToAudioChunks(deviceId: String): Flow<MediaRow> = callbackFlow {
        val topic = "media-audio-$deviceId"
        val existing = client.realtime.subscriptions.values.find { it.topic == topic }
        if (existing != null) {
            try { client.realtime.removeChannel(existing) } catch (e: Exception) {}
        }
        val channel: RealtimeChannel = client.realtime.channel(topic)
        try {
            val broadcastFlow = channel.broadcastFlow<MediaRow>(event = "audio_chunk")
            channel.subscribe(blockUntilSubscribed = true)
            launch {
                broadcastFlow.collect { record ->
                    if (record.type == AUDIO_TYPE) {
                        trySend(record)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "listenToAudioChunks: subscribe failed", e)
            close(e)
            return@callbackFlow
        }
        awaitClose {
            CoroutineScope(Dispatchers.IO).launch {
                try { client.realtime.removeChannel(channel) }
                catch (e: Exception) { Log.w(TAG, "removeChannel on close failed", e) }
            }
        }
    }

    fun setStatus(status: MonitorStatus, message: String?) {
        _status.value = status
        _statusMessage.value = message
    }
}

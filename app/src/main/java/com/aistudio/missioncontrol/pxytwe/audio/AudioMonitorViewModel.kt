package com.aistudio.missioncontrol.pxytwe.audio

import android.app.Application
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.missioncontrol.pxytwe.audio.AudioMonitorRepository.MediaRow
import com.aistudio.missioncontrol.pxytwe.audio.AudioMonitorRepository.MonitorStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * State holder for a single mic-monitoring session. Bytes arrive
 * inline (base64 in `media.data_b64`) — no Storage roundtrip.
 *
 * `peakHistory` / `rms` / `peak` are not cheaply computable in O(1) per
 * chunk from raw PCM without an FFT pass, so we approximate by feeding
 * `data_b64.length` length-modulated bars on the UI side. Cheap and
 * honest about what the live `media` table actually exposes.
 */
class AudioMonitorViewModel(
    app: Application
) : AndroidViewModel(app) {

    data class UiState(
        val status: MonitorStatus = MonitorStatus.Idle,
        val statusMessage: String? = null,
        val isMonitoring: Boolean = false,
        val peakHistory: List<Float> = emptyList(),
        val rms: Float = 0f,
        val peak: Float = 0f,
        val chunksReceived: Int = 0,
        val chunksDecoded: Int = 0,
        val lastChunkAt: Long = 0L,
        val logs: List<LogEntry> = emptyList(),
        val error: String? = null
    )

    data class LogEntry(val time: String, val text: String, val kind: Kind = Kind.Info) {
        enum class Kind { Info, Warn, Good, Error }
    }

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    val statusFlow: StateFlow<MonitorStatus> = AudioMonitorRepository.status

    val isPlayingFlow: SharedFlow<Boolean> get() = player.isPlayingFlow

    private val player = AmbientAudioPlayer()

    private var deviceId: String? = null
    private var collectorJob: Job? = null
    private var staleWatchdog: Job? = null

    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val logCap = 200

    fun start(deviceId: String) {
        if (this.deviceId == deviceId && _ui.value.isMonitoring) return
        if (_ui.value.isMonitoring) stop(sendCommand = false)

        this.deviceId = deviceId
        _ui.update {
            it.copy(
                isMonitoring = true,
                status = MonitorStatus.Starting,
                statusMessage = "Requesting uplink…",
                peakHistory = emptyList(),
                chunksReceived = 0,
                chunksDecoded = 0,
                lastChunkAt = 0L,
                error = null,
                logs = emptyList(),
            )
        }
        log("STARTING UPLINK TO $deviceId…")

        collectorJob = viewModelScope.launch {
            // CRITICAL: the audio channel must be subscribed BEFORE we tell the
            // tracker to start broadcasting. listenToAudioChunks() returns a cold
            // callbackFlow that only subscribes when collected. We launch the
            // collector first, wait for subscription confirmation, THEN send start_mic.
            log("SUBSCRIBING TO AUDIO CHANNEL…")

            val subscribed = kotlinx.coroutines.CompletableDeferred<Unit>()

            // Start collecting in a child coroutine — this triggers the callbackFlow
            // subscription. The onSubscribed callback signals when the channel is ready.
            val chunkCollector = launch {
                try {
                    AudioMonitorRepository.listenToAudioChunks(deviceId) {
                        subscribed.complete(Unit)
                    }.collectLatest { row ->
                        onChunk(row)
                    }
                } catch (e: CancellationException) {
                    if (!subscribed.isCompleted) subscribed.completeExceptionally(e)
                    throw e
                } catch (e: Exception) {
                    if (!subscribed.isCompleted) subscribed.completeExceptionally(e)
                    Log.e(TAG, "Chunk stream failed", e)
                    log("CHUNK STREAM ERROR: ${e.message}", LogEntry.Kind.Error)
                    _ui.update { it.copy(status = MonitorStatus.Failed, statusMessage = e.message) }
                }
            }

            // Wait for the callbackFlow to confirm channel subscription (fast, <1s)
            try {
                subscribed.await()
            } catch (e: Exception) {
                log("SUBSCRIPTION FAILED: ${e.message}", LogEntry.Kind.Error)
                _ui.update { it.copy(isMonitoring = false, status = MonitorStatus.Failed, statusMessage = e.message, error = e.message) }
                return@launch
            }
            log("AUDIO CHANNEL READY. SENDING START COMMAND…")

            val cmdResult = AudioMonitorRepository.startMonitoring(deviceId)
            if (cmdResult.isFailure) {
                val msg = cmdResult.exceptionOrNull()?.message ?: "Start failed"
                _ui.update { it.copy(isMonitoring = false, status = MonitorStatus.Failed, statusMessage = msg, error = msg) }
                log("UPLINK REJECTED: $msg", LogEntry.Kind.Error)
                chunkCollector.cancel()
                return@launch
            }
            log("COMMAND SENT. AWAITING AUDIO DATA…")
        }

        staleWatchdog = viewModelScope.launch {
            while (true) {
                delay(1000)
                val s = _ui.value
                if (!s.isMonitoring) continue
                val sinceMs = if (s.lastChunkAt == 0L) -1L else (System.currentTimeMillis() - s.lastChunkAt)
                if (s.status == MonitorStatus.Live && sinceMs in 0..5_000) continue
            }
        }
    }

    fun stop(sendCommand: Boolean = true) {
        val did = deviceId ?: return
        collectorJob?.cancel()
        collectorJob = null
        staleWatchdog?.cancel()
        staleWatchdog = null
        player.stop()
        log("STOPPING UPLINK…")

        // Clear local state synchronously so a rapid start() doesn't collide
        deviceId = null
        _ui.update {
            it.copy(
                isMonitoring = false,
                status = MonitorStatus.Stopping,
                statusMessage = "Releasing mic…",
            )
        }
        if (sendCommand) {
            viewModelScope.launch {
                val r = AudioMonitorRepository.stopMonitoring(did)
                if (r.isFailure) log("STOP FAILED: ${r.exceptionOrNull()?.message}", LogEntry.Kind.Error)
                // Only update to Idle if no new session has started in the meantime
                if (deviceId == null) {
                    _ui.update { it.copy(status = MonitorStatus.Idle, statusMessage = null) }
                }
            }
        } else {
            _ui.update { it.copy(status = MonitorStatus.Idle, statusMessage = null) }
        }
    }

    private suspend fun onChunk(row: MediaRow) {
        val (status, _) = _ui.value.status to _ui.value.statusMessage
        if (status != MonitorStatus.Live) {
            AudioMonitorRepository.setStatus(MonitorStatus.Live, "Streaming audio inline")
            _ui.update { it.copy(status = MonitorStatus.Live, statusMessage = "Streaming audio inline") }
            if (status == MonitorStatus.Starting) {
                log("UPLINK SECURED — receiving audio.", LogEntry.Kind.Good)
            }
        }

        val now = System.currentTimeMillis()
        val lenNorm = ((row.data_b64?.length ?: 0).coerceAtMost(16_000)) / 16_000f
        _ui.update {
            val newHist = (it.peakHistory + lenNorm).takeLast(WAVEFORM_LEN)
            it.copy(
                peakHistory = newHist,
                peak = lenNorm,
                rms = lenNorm * 0.6f,
                chunksReceived = it.chunksReceived + 1,
                lastChunkAt = now,
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val pcm = decodeMediaRow(row)
            withContext(Dispatchers.Main) {
                if (pcm != null && pcm.isNotEmpty()) {
                    player.start()
                    player.feed(pcm)
                    _ui.update { it.copy(chunksDecoded = it.chunksDecoded + 1) }
                } else {
                    log("DROP CHUNK id=${row.id}", LogEntry.Kind.Warn)
                }
            }
        }
    }

    /**
     * Decode one `media` row's `data_b64` to PCM-16 LE. Tracker is
     * expected to push raw PCM packets with `mime_type='audio/pcm'` —
     * other mime types are ignored for now.
     */
    private fun decodeMediaRow(row: MediaRow): ByteArray? {
        val b64 = row.data_b64 ?: return null
        if (b64.isEmpty()) return null
        if (!row.mime_type.isNullOrEmpty() && row.mime_type != "audio/pcm") {
            Log.w(TAG, "Skipping media row id=${row.id}: mime_type=${row.mime_type}")
            return null
        }
        return try {
            Base64.decode(b64, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Base64 decode failed for row id=${row.id}", e)
            null
        }
    }

    private fun log(text: String, kind: LogEntry.Kind = LogEntry.Kind.Info) {
        val time = timeFmt.format(Date())
        _ui.update {
            val cap = (it.logs + LogEntry(time, text, kind)).takeLast(logCap)
            it.copy(logs = cap)
        }
    }

    fun onRepoStatus(status: MonitorStatus, message: String?) {
        _ui.update { it.copy(status = status, statusMessage = message) }
    }

    override fun onCleared() {
        val did = deviceId
        if (did != null && _ui.value.isMonitoring) {
            // Don't kill on rotation. Pause collector + player; user can resume.
            player.stop()
            collectorJob?.cancel()
            collectorJob = null
        }
        super.onCleared()
    }

    private companion object {
        const val TAG = "AudioMonitorViewModel"
        const val WAVEFORM_LEN = 96
    }
}

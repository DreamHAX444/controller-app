package com.aistudio.missioncontrol.pxytwe.webrtc

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.missioncontrol.pxytwe.SupabaseClientManager
import com.aistudio.missioncontrol.pxytwe.webrtc.signaling.SupabaseWebRtcSignalingTransport
import com.aistudio.missioncontrol.pxytwe.webrtc.adaptation.AdaptationTelemetryMessage
import com.aistudio.missioncontrol.pxytwe.webrtc.adaptation.SupabaseAdaptationTelemetryTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.webrtc.VideoTrack
import java.util.UUID

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@Serializable
data class StartScreenParams(
    @SerialName("session_id") val sessionId: String,
    @SerialName("quality_profile") val qualityProfile: String
)

enum class ScreenMonitorUiState {
    IDLE, CONNECTING, PLAYING, DISCONNECTED, ERROR
}

data class ScreenMonitorState(
    val uiState: ScreenMonitorUiState = ScreenMonitorUiState.IDLE,
    val errorMessage: String? = null,
    val deviceId: String = "",
    val sessionId: String? = null,
    val videoTrack: VideoTrack? = null,
    val cameraVideoTrack: VideoTrack? = null,
    val audioTrack: org.webrtc.AudioTrack? = null,
    val cameraTelemetry: com.aistudio.missioncontrol.pxytwe.webrtc.signaling.CameraTelemetryPayload? = null,
    val isAutoMode: Boolean = true,
    val selectedProfile: String = "BALANCED",
    val networkQuality: String = "UNKNOWN"
)

class ScreenMonitorViewModel : ViewModel(), ControllerWebRtcManagerListener {

    private val _uiState = MutableStateFlow(ScreenMonitorState())
    val uiState: StateFlow<ScreenMonitorState> = _uiState.asStateFlow()

    private var rtcManager: ControllerWebRtcManager? = null

    override fun onCameraTelemetryReceived(telemetry: com.aistudio.missioncontrol.pxytwe.webrtc.signaling.CameraTelemetryPayload) {
        _uiState.update { it.copy(cameraTelemetry = telemetry) }
    }

    companion object {
        private const val TAG = "ScreenMonitorVM"
    }

    fun setDevice(deviceId: String) {
        _uiState.update { it.copy(deviceId = deviceId) }
    }

    private var telemetryJob: kotlinx.coroutines.Job? = null
    private var telemetryTransport: SupabaseAdaptationTelemetryTransport? = null

    
    fun handleRealtimeReconnect() {
        viewModelScope.launch {
            rtcManager?.handleRealtimeReconnect()
            try {
                telemetryJob?.cancel()
                telemetryTransport?.invalidate()
                _uiState.value.sessionId?.let { if (it.isNotBlank()) telemetryTransport?.connect(it) }
                telemetryJob = viewModelScope.launch {
                    try {
                        telemetryTransport?.receiveTelemetry()?.collect { msg ->
                            onAdaptationTelemetryReceived(msg)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Telemetry collection failed", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reconnecting telemetry transport", e)
            }
        }
    }

    fun startMonitoring(context: Context, sessionId: String, qualityProfile: String = "BALANCED") {
        if (_uiState.value.uiState == ScreenMonitorUiState.CONNECTING ||
            _uiState.value.uiState == ScreenMonitorUiState.PLAYING) {
            return
        }

        val targetDevice = _uiState.value.deviceId
        if (targetDevice.isBlank()) return

        _uiState.update { 
            it.copy(
                uiState = ScreenMonitorUiState.CONNECTING, 
                sessionId = sessionId,
                errorMessage = null,
                videoTrack = null,
                audioTrack = null,
                isAutoMode = true,
                networkQuality = "UNKNOWN"
            ) 
        }

        viewModelScope.launch {
            try {
                val paramsObj = StartScreenParams(sessionId, qualityProfile)
                val paramsJson = Json.encodeToString(paramsObj)
                
                SupabaseClientManager.sendCommand(
                    deviceId = targetDevice,
                    command = "start_screen",
                    params = paramsJson
                )
                
                val transport = SupabaseWebRtcSignalingTransport { SupabaseClientManager.client }
                rtcManager = ControllerWebRtcManager(
                    context = context,
                    sessionId = sessionId,
                    deviceId = "controller",
                    transport = transport,
                    scope = viewModelScope,
                    listener = this@ScreenMonitorViewModel
                )
                
                rtcManager?.start()
                
                telemetryTransport = SupabaseAdaptationTelemetryTransport { SupabaseClientManager.client }
                telemetryTransport?.connect(sessionId)
                telemetryJob = viewModelScope.launch {
                    try {
                        telemetryTransport?.receiveTelemetry()?.collect { msg ->
                            onAdaptationTelemetryReceived(msg)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Telemetry collection failed", e)
                    }
                }
                
                // Set initial mode to AUTO
                rtcManager?.sendVideoProfile("AUTO", null)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start monitoring", e)
                val mgr = rtcManager
                if (mgr != null) {
                    mgr.stop()
                    if (rtcManager === mgr) rtcManager = null
                }
                _uiState.update { it.copy(uiState = ScreenMonitorUiState.ERROR, errorMessage = e.message) }
            }
        }
    }

    override fun onRemoteVideoTrackReceived(track: VideoTrack, streamId: String?) {
        Log.i(TAG, "VideoTrack received in ViewModel! streamId=$streamId")
        if (streamId == "camera_stream") {
            _uiState.update { 
                it.copy(
                    cameraVideoTrack = track
                ) 
            }
        } else {
            _uiState.update { 
                it.copy(
                    uiState = ScreenMonitorUiState.PLAYING,
                    videoTrack = track
                ) 
            }
        }
    }

    override fun onRemoteAudioTrackReceived(track: org.webrtc.AudioTrack) {
        Log.i(TAG, "AudioTrack received in ViewModel!")
        _uiState.update { 
            it.copy(
                audioTrack = track
            ) 
        }
    }
    
    fun onAdaptationTelemetryReceived(payload: AdaptationTelemetryMessage) {
        _uiState.update {
            it.copy(
                networkQuality = payload.networkQuality,
                isAutoMode = payload.mode == "AUTO",
                selectedProfile = payload.actualProfile
            )
        }
    }

    fun stopMonitoring() {
        viewModelScope.launch {
            executeStopMonitoring()
        }
    }

    private suspend fun executeStopMonitoring() {
        try {
            val targetDevice = _uiState.value.deviceId
            if (targetDevice.isNotBlank()) {
                SupabaseClientManager.sendCommand(
                    deviceId = targetDevice,
                    command = "stop_screen",
                    params = _uiState.value.sessionId
                )
            }
            rtcManager?.stop()
            rtcManager = null
            telemetryTransport?.disconnect()
            telemetryTransport = null
            _uiState.update { 
                it.copy(uiState = ScreenMonitorUiState.DISCONNECTED, videoTrack = null, audioTrack = null) 
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping monitoring", e)
        }
    }

    fun setVideoProfile(profileName: String) {
        if (profileName == "AUTO") {
            _uiState.update { it.copy(isAutoMode = true) }
            viewModelScope.launch {
                rtcManager?.sendVideoProfile("AUTO", null)
            }
        } else {
            _uiState.update { it.copy(isAutoMode = false, selectedProfile = profileName) }
            viewModelScope.launch {
                rtcManager?.sendVideoProfile("MANUAL", profileName)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Do NOT use viewModelScope, it is cancelled immediately.
        // Use GlobalScope with a timeout to ensure STOP_SCREEN is sent even if activity dies.
        val targetDevice = _uiState.value.deviceId
        val sessionId = _uiState.value.sessionId
        val rtc = rtcManager
        
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch {
            try {
                kotlinx.coroutines.withTimeout(5000) {
                    if (targetDevice.isNotBlank()) {
                        SupabaseClientManager.sendCommand(
                            deviceId = targetDevice,
                            command = "stop_screen",
                            params = sessionId
                        )
                    }
                    rtc?.stop()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during onCleared shutdown", e)
            }
        }
    }
}

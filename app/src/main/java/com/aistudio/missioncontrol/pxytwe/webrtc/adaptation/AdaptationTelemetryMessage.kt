package com.aistudio.missioncontrol.pxytwe.webrtc.adaptation

import kotlinx.serialization.Serializable

@Serializable
data class AdaptationTelemetryMessage(
    val sessionId: String,
    val deviceId: String,
    val timestamp: Long,
    val networkQuality: String,
    val requestedProfile: String,
    val actualProfile: String,
    val mode: String,
    val reason: String,
    val isStale: Boolean = false,
    val audioTransportPressure: String = "NORMAL",
    val audioPlaybackHealth: String = "UNKNOWN"
)

package com.aistudio.missioncontrol.pxytwe.webrtc.signaling

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

enum class SignalType {
    START_REQUEST,
    READY,
    OFFER,
    ANSWER,
    ICE_CANDIDATE,
    STOP_REQUEST,
    SESSION_ENDED,
    ERROR,
    VIDEO_PROFILE,
    CAMERA_TELEMETRY
}

@Serializable
data class CameraTelemetryPayload(
    val requestedWidth: Int,
    val requestedHeight: Int,
    val requestedFps: Int,
    val actualWidth: Int?,
    val actualHeight: Int?,
    val observedFps: Double?,
    val frameCount: Long,
    val firstFrameTimestampNs: Long?,
    val lastFrameTimestampNs: Long?
)

@Serializable
data class WebRtcSignalMessage(
    val sessionId: String,
    val deviceId: String,
    val type: SignalType,
    val timestamp: Long = System.currentTimeMillis(),
    val messageId: String = java.util.UUID.randomUUID().toString(),
    val payload: JsonElement? = null
)

@Serializable
data class VideoProfilePayload(
    val mode: String,
    val profileName: String? = null
)

@Serializable
data class ReadyPayload(val generation: Long)

@Serializable
data class OfferPayload(val sdp: String, val generation: Long, val offerId: String = "")

@Serializable
data class AnswerPayload(val sdp: String, val generation: Long, val answerId: String = "")

@Serializable
data class IceCandidatePayload(
    val candidate: String,
    val sdpMid: String,
    val sdpMLineIndex: Int,
    val generation: Long,
    val candidateId: String = ""
)

@Serializable
data class ErrorPayload(val message: String)

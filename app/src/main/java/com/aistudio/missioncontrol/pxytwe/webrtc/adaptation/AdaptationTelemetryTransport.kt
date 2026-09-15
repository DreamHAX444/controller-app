package com.aistudio.missioncontrol.pxytwe.webrtc.adaptation

import kotlinx.coroutines.flow.Flow

interface AdaptationTelemetryTransport {
    suspend fun connect(sessionId: String)
    suspend fun disconnect()
    suspend fun invalidate()
    fun receiveTelemetry(): Flow<AdaptationTelemetryMessage>
}

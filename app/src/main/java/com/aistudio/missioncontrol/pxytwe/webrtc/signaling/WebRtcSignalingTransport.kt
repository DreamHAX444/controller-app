package com.aistudio.missioncontrol.pxytwe.webrtc.signaling

import kotlinx.coroutines.flow.Flow

interface WebRtcSignalingTransport {
    suspend fun connect(sessionId: String)
    suspend fun disconnect()
    suspend fun invalidate()
    fun receiveSignals(): Flow<WebRtcSignalMessage>
    suspend fun sendSignal(message: WebRtcSignalMessage)
}

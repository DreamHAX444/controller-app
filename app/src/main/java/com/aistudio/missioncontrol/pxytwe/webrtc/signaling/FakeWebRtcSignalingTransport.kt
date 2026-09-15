package com.aistudio.missioncontrol.pxytwe.webrtc.signaling

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class FakeWebRtcSignalingTransport : WebRtcSignalingTransport {
    private var isConnected = false
    private var currentSessionId: String? = null

    val sentMessages = mutableListOf<WebRtcSignalMessage>()
    private val incomingSignals = MutableSharedFlow<WebRtcSignalMessage>(extraBufferCapacity = 100)

    override suspend fun connect(sessionId: String) {
        isConnected = true
        currentSessionId = sessionId
    }

    override suspend fun invalidate() { }
    override suspend fun disconnect() {
        isConnected = false
        currentSessionId = null
    }

    override fun receiveSignals(): Flow<WebRtcSignalMessage> = incomingSignals.asSharedFlow()

    override suspend fun sendSignal(message: WebRtcSignalMessage) {
        if (!isConnected) throw IllegalStateException("Not connected")
        sentMessages.add(message)
    }

    fun injectRemoteSignal(message: WebRtcSignalMessage) {
        incomingSignals.tryEmit(message)
    }
}

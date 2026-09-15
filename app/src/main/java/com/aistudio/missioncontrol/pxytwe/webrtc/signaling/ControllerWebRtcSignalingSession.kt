package com.aistudio.missioncontrol.pxytwe.webrtc.signaling

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement

enum class WebRtcSignalingState {
    IDLE,
    WAITING_FOR_TRACKER,
    ACTIVE,
    CLOSED,
    ERROR
}

class ControllerWebRtcSignalingSession(
    val sessionId: String,
    private val deviceId: String,
    private val transport: WebRtcSignalingTransport,
    private val scope: CoroutineScope
) {
    private val tag = "ControllerSignaling"
    
    private val _state = MutableStateFlow(WebRtcSignalingState.IDLE)
    val state: StateFlow<WebRtcSignalingState> = _state.asStateFlow()

    private val _incomingSignals = kotlinx.coroutines.flow.MutableSharedFlow<WebRtcSignalMessage>(extraBufferCapacity = 64)
    val incomingSignals: kotlinx.coroutines.flow.SharedFlow<WebRtcSignalMessage> = _incomingSignals.asSharedFlow()

    private val mutex = Mutex()
    private val processedMessageIds = mutableSetOf<String>()
    private var job: Job? = null
    private var startRequestJob: Job? = null
    
    suspend fun start() {
        mutex.withLock {
            if (_state.value != WebRtcSignalingState.IDLE) return
            Log.i(tag, "Controller signaling session starting: sessionId=$sessionId")
            _state.value = WebRtcSignalingState.WAITING_FOR_TRACKER
            
            try {
                transport.connect(sessionId)
                job = scope.launch {
                    transport.receiveSignals().collect { msg ->
                        handleIncomingMessage(msg)
                    }
                }
                startRequestJob?.cancel()
                startRequestJob = scope.launch {
                    while (_state.value == WebRtcSignalingState.WAITING_FOR_TRACKER) {
                        sendSignal(SignalType.START_REQUEST)
                        kotlinx.coroutines.delay(1000)
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error starting transport", e)
                _state.value = WebRtcSignalingState.ERROR
            }
        }
    }
    
    
    suspend fun reconnect() {
        mutex.withLock {
            if (_state.value == WebRtcSignalingState.CLOSED) return
            Log.i(tag, "Reconnecting controller signaling session")
            startRequestJob?.cancel()
            startRequestJob = null
            job?.cancel()
            try {
                transport.invalidate()
                transport.connect(sessionId)
                job = scope.launch {
                    transport.receiveSignals().collect { msg ->
                        handleIncomingMessage(msg)
                    }
                }
                if (_state.value == WebRtcSignalingState.WAITING_FOR_TRACKER) {
                    startRequestJob = scope.launch {
                        while (_state.value == WebRtcSignalingState.WAITING_FOR_TRACKER) {
                            sendSignal(SignalType.START_REQUEST)
                            kotlinx.coroutines.delay(1000)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error reconnecting transport", e)
            }
        }
    }
    suspend fun stop() {
        mutex.withLock {
            if (_state.value == WebRtcSignalingState.CLOSED) return
            Log.i(tag, "Controller signaling session closing")
            
            startRequestJob?.cancel()
            startRequestJob = null
            
            try {
                sendSignal(SignalType.STOP_REQUEST)
                transport.disconnect()
            } catch (e: Exception) {
                Log.e(tag, "Error disconnecting transport", e)
            }
            
            _state.value = WebRtcSignalingState.CLOSED
            processedMessageIds.clear()
            job?.cancel()
            job = null
        }
    }
    
    private suspend fun handleIncomingMessage(msg: WebRtcSignalMessage) {
        if (msg.sessionId != sessionId) {
            Log.w(tag, "Wrong session signal rejected: expected=$sessionId, actual=${msg.sessionId}")
            return
        }
        
        // Ignore messages we sent
        if (msg.deviceId == deviceId) {
            return
        }
        
        if (!processedMessageIds.add(msg.messageId)) {
            Log.d(tag, "Duplicate signal ignored: messageId=${msg.messageId}")
            return
        }
        
        if (processedMessageIds.size > 100) {
            val iterator = processedMessageIds.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }

        Log.i(tag, "Signal received: type=${msg.type}")
        
        when (msg.type) {
            SignalType.READY, SignalType.OFFER -> {
                _incomingSignals.tryEmit(msg)
                if (_state.value == WebRtcSignalingState.WAITING_FOR_TRACKER) {
                    _state.value = WebRtcSignalingState.ACTIVE
                }
                startRequestJob?.cancel()
                startRequestJob = null
            }
            SignalType.SESSION_ENDED -> {
                _incomingSignals.tryEmit(msg)
                stop()
            }
            SignalType.ICE_CANDIDATE -> {
                _incomingSignals.tryEmit(msg)
            }
            else -> {
                _incomingSignals.tryEmit(msg)
            }
        }
    }

    suspend fun sendSignal(type: SignalType, payload: JsonElement? = null) {
        if (_state.value == WebRtcSignalingState.CLOSED) return
        val msg = WebRtcSignalMessage(
            sessionId = sessionId,
            deviceId = deviceId,
            type = type,
            timestamp = System.currentTimeMillis(),
            messageId = java.util.UUID.randomUUID().toString(),
            payload = payload
        )
        try {
            transport.sendSignal(msg)
            Log.i(tag, "Signal sent: type=${msg.type}")
        } catch (e: Exception) {
            Log.e(tag, "Error sending signal", e)
        }
    }
}

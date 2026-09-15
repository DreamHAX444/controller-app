package com.aistudio.missioncontrol.pxytwe.webrtc

import android.content.Context
import android.util.Log
import com.aistudio.missioncontrol.pxytwe.webrtc.signaling.OfferPayload
import com.aistudio.missioncontrol.pxytwe.webrtc.signaling.AnswerPayload
import com.aistudio.missioncontrol.pxytwe.webrtc.signaling.IceCandidatePayload
import com.aistudio.missioncontrol.pxytwe.webrtc.signaling.SignalingMessageGuard
import com.aistudio.missioncontrol.pxytwe.webrtc.signaling.SignalType
import com.aistudio.missioncontrol.pxytwe.webrtc.signaling.ControllerWebRtcSignalingSession
import com.aistudio.missioncontrol.pxytwe.webrtc.signaling.WebRtcSignalingTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack

enum class ControllerSessionState {
    IDLE, STARTING, WAITING_FOR_READY, CONNECTING, CONNECTED, DEGRADED, RECOVERING, STOPPING, ENDED, FAILED
}

interface ControllerWebRtcManagerListener {
    fun onRemoteVideoTrackReceived(track: VideoTrack)
    fun onRemoteAudioTrackReceived(track: org.webrtc.AudioTrack)
}

class ControllerWebRtcManager(
    private val context: Context,
    private val sessionId: String,
    private val deviceId: String,
    private val transport: WebRtcSignalingTransport,
    private val scope: CoroutineScope,
    private val listener: ControllerWebRtcManagerListener
) {
    private val tag = "ControllerWebRtcManager"
    
    private val _state = MutableStateFlow(ControllerSessionState.IDLE)
    val state: StateFlow<ControllerSessionState> = _state.asStateFlow()

    private var signalingSession: ControllerWebRtcSignalingSession? = null
    private var webRtcSession: ControllerWebRtcSession? = null
    
    private var audioJitterBuffer: com.aistudio.missioncontrol.pxytwe.webrtc.audio.AudioJitterBuffer? = null
    private var audioTrackPlayer: com.aistudio.missioncontrol.pxytwe.webrtc.audio.AudioTrackPlayer? = null
    private var avSyncController: com.aistudio.missioncontrol.pxytwe.webrtc.sync.AvSyncController? = null
    
    private val recoveryMutex = Mutex()
    private var generation = -1L
    private val messageGuard = SignalingMessageGuard()
        
    suspend fun start() {
        if (_state.value != ControllerSessionState.IDLE) return
        _state.value = ControllerSessionState.STARTING
        
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        audioManager.isSpeakerphoneOn = true
        
        avSyncController = com.aistudio.missioncontrol.pxytwe.webrtc.sync.AvSyncController(sessionId)
        avSyncController?.onSessionStart()
        
        audioJitterBuffer = com.aistudio.missioncontrol.pxytwe.webrtc.audio.AudioJitterBuffer()
        audioTrackPlayer = com.aistudio.missioncontrol.pxytwe.webrtc.audio.AudioTrackPlayer(audioJitterBuffer!!, avSyncController!!, scope)
        audioTrackPlayer?.start()
        
        WebRtcPeerConnectionFactory.init(context.applicationContext)
        
        signalingSession = ControllerWebRtcSignalingSession(sessionId, deviceId, transport, scope)
        
        scope.launch {
            signalingSession?.incomingSignals?.collect { msg ->
                handleSignal(msg)
            }
        }
        
        signalingSession?.start()
        _state.value = ControllerSessionState.WAITING_FOR_READY
    }
    
    private suspend fun handleSignal(msg: com.aistudio.missioncontrol.pxytwe.webrtc.signaling.WebRtcSignalMessage) {
        when (msg.type) {
            SignalType.READY -> {
                val payloadGeneration = try {
                    msg.payload?.let {
                        Json.decodeFromJsonElement<com.aistudio.missioncontrol.pxytwe.webrtc.signaling.ReadyPayload>(it).generation
                    } ?: (generation + 1).coerceAtLeast(0L)
                } catch (e: Exception) {
                    (generation + 1).coerceAtLeast(0L)
                }
                recoveryMutex.withLock {
                    if (_state.value == ControllerSessionState.STOPPING || _state.value == ControllerSessionState.ENDED) return@withLock
                    if (!messageGuard.shouldAcceptReady(payloadGeneration)) {
                        android.util.Log.w(tag, "Ignoring duplicate or stale READY from gen $payloadGeneration")
                        return@withLock
                    }
                    generation = payloadGeneration
                    startSessionGeneration(generation)
                }
            }
            SignalType.OFFER -> {
                msg.payload?.let {
                    val payload = Json.decodeFromJsonElement<OfferPayload>(it)
                    recoveryMutex.withLock {
                        if (_state.value == ControllerSessionState.WAITING_FOR_READY || webRtcSession == null) {
                            Log.i(tag, "OFFER arrived before READY or session uninitialized; starting generation ${payload.generation}")
                            generation = payload.generation
                            startSessionGeneration(generation)
                        }
                    }
                    if (!messageGuard.shouldAcceptOffer(payload.generation)) return@let
                    webRtcSession?.handleOffer(SessionDescription(SessionDescription.Type.OFFER, payload.sdp))
                }
            }
            SignalType.ICE_CANDIDATE -> {
                msg.payload?.let {
                    val payload = Json.decodeFromJsonElement<IceCandidatePayload>(it)
                    val iceId = payload.candidate
                    if (!messageGuard.shouldAcceptIceCandidate(payload.generation, iceId)) return@let
                    val candidate = IceCandidate(payload.sdpMid, payload.sdpMLineIndex, payload.candidate)
                    webRtcSession?.addIceCandidate(candidate)
                }
            }
            SignalType.SESSION_ENDED -> {
                stop()
            }
            else -> {}
        }
    }

    private suspend fun startSessionGeneration(currentGeneration: Long) {
        messageGuard.startNewGeneration(currentGeneration)
        Log.i(tag, "Starting Controller session generation $currentGeneration")
        _state.value = ControllerSessionState.CONNECTING
        
        webRtcSession?.stop()
        webRtcSession = null
        
        // Reset buffers but keep players alive so we don't recreate threads unnecessarily
        audioJitterBuffer?.clear()
        
        val factory = WebRtcPeerConnectionFactory.getFactory()
        
        val webrtcListener = object : WebRtcSessionListener {
            override fun onIceCandidate(candidate: IceCandidate) {
                if (generation != currentGeneration) return
                scope.launch {
                    val payload = IceCandidatePayload(candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex, generation = currentGeneration, candidateId = java.util.UUID.randomUUID().toString())
                    signalingSession?.sendSignal(SignalType.ICE_CANDIDATE, Json.encodeToJsonElement(payload))
                }
            }
            override fun onSdpAnswer(sdp: SessionDescription) {
                if (generation != currentGeneration) return
                scope.launch {
                    val payload = AnswerPayload(sdp.description, generation = currentGeneration, answerId = java.util.UUID.randomUUID().toString())
                    signalingSession?.sendSignal(SignalType.ANSWER, Json.encodeToJsonElement(payload))
                }
            }
            override fun onSdpOffer(sdp: SessionDescription) {}
            override fun onConnectionStateChange(state: PeerConnection.PeerConnectionState) {
                if (generation != currentGeneration) return
                when(state) {
                    PeerConnection.PeerConnectionState.CONNECTED -> {
                        _state.value = ControllerSessionState.CONNECTED
                    }
                    PeerConnection.PeerConnectionState.DISCONNECTED -> {
                        _state.value = ControllerSessionState.DEGRADED
                    }
                    PeerConnection.PeerConnectionState.FAILED -> {
                        _state.value = ControllerSessionState.FAILED
                        // Tracker handles recovery and sends READY to trigger generation bump.
                    }
                    else -> {}
                }
            }
            override fun onRemoteVideoTrack(track: VideoTrack) {
                if (generation != currentGeneration) return
                track.setEnabled(true)
                track.addSink { frame ->
                    avSyncController?.onVideoFrameArrived(frame)
                }
                listener.onRemoteVideoTrackReceived(track)
            }
            override fun onRemoteAudioTrack(track: org.webrtc.AudioTrack) {
                if (generation != currentGeneration) return
                listener.onRemoteAudioTrackReceived(track)
            }
            override fun onDataChannelReceived(channel: org.webrtc.DataChannel) {
                if (generation != currentGeneration) return
                if (channel.label() == "playback-audio") {
                    Log.i(tag, "Received playback-audio DataChannel")
                    channel.registerObserver(object : org.webrtc.DataChannel.Observer {
                        override fun onBufferedAmountChange(p0: Long) {}
                        override fun onStateChange() {}
                        override fun onMessage(buffer: org.webrtc.DataChannel.Buffer) {
                            if (generation != currentGeneration) return
                            if (buffer.binary) {
                                try {
                                    val packet = com.aistudio.missioncontrol.pxytwe.webrtc.audio.PlaybackAudioPacketSerializer.decode(buffer.data)
                                    if (packet.sessionId == sessionId) {
                                        avSyncController?.onAudioPacketArrived(packet)
                                        audioJitterBuffer?.push(packet)
                                    } else {
                                        Log.w(tag, "Rejected audio packet for wrong session: " + packet.sessionId)
                                    }
                                } catch (e: Exception) {
                                    Log.e(tag, "Failed to decode audio packet", e)
                                }
                            }
                        }
                    })
                }
            }
        }
        
        webRtcSession = ControllerWebRtcSession(sessionId, factory, scope, webrtcListener)
        webRtcSession?.start()
    }

    suspend fun sendVideoProfile(mode: String, profileName: String?) {
        val payload = com.aistudio.missioncontrol.pxytwe.webrtc.signaling.VideoProfilePayload(mode, profileName)
        signalingSession?.sendSignal(
            com.aistudio.missioncontrol.pxytwe.webrtc.signaling.SignalType.VIDEO_PROFILE,
            kotlinx.serialization.json.Json.encodeToJsonElement(payload)
        )
    }

    
    suspend fun handleRealtimeReconnect() {
        Log.i(tag, "Handling Realtime reconnect")
        signalingSession?.reconnect()
    }
    suspend fun stop() {
        Log.i(tag, "Stopping WebRTC Manager")
        _state.value = ControllerSessionState.STOPPING
        
        recoveryMutex.withLock {
            generation++
            
            avSyncController?.onSessionStop()
            avSyncController = null
            audioTrackPlayer?.stop()
            audioTrackPlayer = null
            audioJitterBuffer = null
            
            webRtcSession?.stop()
            webRtcSession = null
            
            signalingSession?.stop()
            _state.value = ControllerSessionState.ENDED
        }
    }
}

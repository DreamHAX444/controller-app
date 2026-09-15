package com.aistudio.missioncontrol.pxytwe.webrtc

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack

interface WebRtcSessionListener {
    fun onIceCandidate(candidate: IceCandidate)
    fun onSdpOffer(sdp: SessionDescription)
    fun onSdpAnswer(sdp: SessionDescription)
    fun onConnectionStateChange(state: PeerConnection.PeerConnectionState)
    fun onRemoteVideoTrack(track: VideoTrack)
    fun onRemoteAudioTrack(track: org.webrtc.AudioTrack)
    fun onDataChannelReceived(channel: DataChannel)
}

class ControllerWebRtcSession(
    val sessionId: String,
    private val factory: PeerConnectionFactory,
    private val scope: CoroutineScope,
    private val listener: WebRtcSessionListener
) {
    private val tag = "ControllerWebRtcSession"
    
    private var peerConnection: PeerConnection? = null
    @Volatile private var isClosed = false
    private val mutex = Mutex()
    
    private val pendingIceCandidates = java.util.concurrent.CopyOnWriteArrayList<IceCandidate>()

    suspend fun start() {
        mutex.withLock {
            if (isClosed) {
                Log.w(tag, "Cannot start a CLOSED session")
                return@withLock
            }

            Log.i(tag, "Session created: sessionId=$sessionId")
            
            try {
                val iceServers = listOf(
                    PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
                )
                val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
                val observer = object : PeerConnection.Observer {
                    override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
                    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
                    override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                        if (newState != null) {
                            listener.onConnectionStateChange(newState)
                        }
                    }
                    override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
                    override fun onIceCandidate(candidate: IceCandidate?) {
                        candidate?.let { listener.onIceCandidate(it) }
                    }
                    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                    override fun onAddStream(stream: MediaStream?) {}
                    override fun onRemoveStream(stream: MediaStream?) {}
                    override fun onDataChannel(channel: DataChannel?) {
                        channel?.let { listener.onDataChannelReceived(it) }
                    }
                    override fun onRenegotiationNeeded() {}
                    
                    override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                        val track = receiver?.track()
                        if (track is VideoTrack) {
                            Log.i(tag, "Remote VideoTrack received!")
                            listener.onRemoteVideoTrack(track)
                        } else if (track is org.webrtc.AudioTrack) {
                            Log.i(tag, "Remote AudioTrack received!")
                            listener.onRemoteAudioTrack(track)
                        }
                    }
                }
                
                peerConnection = factory.createPeerConnection(rtcConfig, observer)
                Log.i(tag, "PeerConnection created")
            } catch (e: Exception) {
                Log.e(tag, "Error starting WebRtcSession", e)
                cleanupResources()
            }
        }
    }

    fun handleOffer(sdp: SessionDescription) {
        if (isClosed) return
        Log.i(tag, "Handling remote OFFER")
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.i(tag, "Remote description set (OFFER)")
                drainPendingIceCandidates()
                createAnswer()
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {
                Log.e(tag, "Failed to set remote description: $p0")
            }
        }, sdp)
    }

    private fun createAnswer() {
        val constraints = MediaConstraints()
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.i(tag, "Local description set successfully (ANSWER)")
                            listener.onSdpAnswer(it)
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {}
                    }, it)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {
                Log.e(tag, "Failed to create answer: $p0")
            }
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        if (isClosed) return
        if (peerConnection?.remoteDescription != null) {
            peerConnection?.addIceCandidate(candidate)
            Log.i(tag, "Added ICE candidate")
        } else {
            Log.i(tag, "Buffered ICE candidate (remote description not set)")
            pendingIceCandidates.add(candidate)
        }
    }

    private fun drainPendingIceCandidates() {
        pendingIceCandidates.forEach {
            peerConnection?.addIceCandidate(it)
        }
        if (pendingIceCandidates.isNotEmpty()) {
            Log.i(tag, "Drained ${pendingIceCandidates.size} pending ICE candidates")
            pendingIceCandidates.clear()
        }
    }

    suspend fun stop() {
        mutex.withLock {
            if (isClosed) return@withLock
            isClosed = true
            Log.i(tag, "Session stopping")
            cleanupResources()
            Log.i(tag, "WebRTC resources released, PeerConnection closed")
        }
    }

    private fun cleanupResources() {
        try {
            peerConnection?.close()
            peerConnection?.dispose()
            peerConnection = null
        } catch (e: Exception) {}
        isClosed = true
    }
}

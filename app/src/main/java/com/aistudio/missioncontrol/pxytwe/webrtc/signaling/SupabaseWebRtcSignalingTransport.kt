package com.aistudio.missioncontrol.pxytwe.webrtc.signaling

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SupabaseWebRtcSignalingTransport(
    private val clientProvider: () -> io.github.jan.supabase.SupabaseClient
) : WebRtcSignalingTransport {
    private var channel: RealtimeChannel? = null
    private val mutex = Mutex()

    override suspend fun connect(sessionId: String) {
        mutex.withLock {
            if (channel != null) return
            val channelName = "webrtc-$sessionId"
            val newChannel = clientProvider().channel(channelName)
            newChannel.subscribe(blockUntilSubscribed = true)
            channel = newChannel
        }
    }

    
    override suspend fun invalidate() {
        mutex.withLock {
            channel?.let { clientProvider().realtime.removeChannel(it) }
            channel = null
        }
    }
    override suspend fun disconnect() {
        mutex.withLock {
            channel?.let { clientProvider().realtime.removeChannel(it) }
            channel = null
        }
    }

    override fun receiveSignals(): Flow<WebRtcSignalMessage> {
        val currentChannel = channel ?: throw IllegalStateException("Must connect before receiving signals")
        return currentChannel.broadcastFlow<WebRtcSignalMessage>(event = "signal")
    }

    override suspend fun sendSignal(message: WebRtcSignalMessage) {
        val currentChannel = channel ?: throw IllegalStateException("Must connect before sending signals")
        currentChannel.broadcast(event = "signal", message = message)
    }
}

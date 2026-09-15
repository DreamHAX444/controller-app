package com.aistudio.missioncontrol.pxytwe.webrtc.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

import com.aistudio.missioncontrol.pxytwe.webrtc.sync.AvSyncController
import com.aistudio.missioncontrol.pxytwe.webrtc.sync.AudioScheduleDecision

class AudioTrackPlayer(
    private val jitterBuffer: AudioJitterBuffer,
    private val syncController: AvSyncController,
    private val scope: CoroutineScope
) {
    private val tag = "AudioTrackPlayer"
    private var audioTrack: AudioTrack? = null
    private var playJob: Job? = null

    fun start() {
        if (playJob != null) return

        val minBufferSize = AudioTrack.getMinBufferSize(
            48000,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(48000)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()

        playJob = scope.launch(Dispatchers.IO) {
            Log.i(tag, "AudioTrackPlayer started")
            while (isActive) {
                val packet = jitterBuffer.pullNextFrame()
                if (packet != null) {
                    var handled = false
                    while (isActive && !handled) {
                        val decision = syncController.getScheduleDecision(packet)
                        when (decision) {
                            is AudioScheduleDecision.PlayNow -> {
                                audioTrack?.write(packet.payload, 0, packet.payload.size)
                                handled = true
                            }
                            is AudioScheduleDecision.WaitNs -> {
                                val delayMs = decision.waitNs / 1_000_000
                                if (delayMs > 0) {
                                    delay(delayMs)
                                }
                            }
                            is AudioScheduleDecision.Drop -> {
                                handled = true // discarded
                            }
                        }
                    }
                } else {
                    if (jitterBuffer.state == JitterBufferState.STARVED) {
                        syncController.onStarved()
                    }
                    delay(10)
                }
                
                // When we transition from starved to buffering/playing, notify sync
                if (jitterBuffer.state != JitterBufferState.STARVED) {
                    syncController.onRecovered()
                }
            }
        }
    }

    fun stop() {
        playJob?.cancel()
        playJob = null
        jitterBuffer.stop()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(tag, "Error releasing AudioTrack", e)
        }
        audioTrack = null
        Log.i(tag, "AudioTrackPlayer stopped")
    }
}

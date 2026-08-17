package com.aistudio.missioncontrol.pxytwe.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * PCM player that lets the operator hear what the selected tracker is picking
 * up. Tracker streams 16 kHz / 16-bit mono PCM chunks; we feed them straight
 * into an [AudioTrack] in streaming mode for instant, low-latency playback.
 *
 * Adds a small jitter queue so a slow Storage download on one chunk doesn't
 * audibly stutter playback.
 */
class AmbientAudioPlayer(
    private val sampleRateHz: Int = 8_000
) {
    private var track: AudioTrack? = null
    private val playing = AtomicBoolean(false)
    private val _isPlaying = MutableSharedFlow<Boolean>(replay = 1)
    val isPlayingFlow: SharedFlow<Boolean> = _isPlaying.asSharedFlow()

    // Bounded queue of PCM byte chunks we'll write out in order.
    // 4 buffers * 250ms ≈ 1s of audio tolerance to network jitter.
    private val queue = ArrayBlockingQueue<ByteArray>(4)
    private var pumpThread: Thread? = null

    fun start() {
        if (playing.get()) return
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track?.play()
        playing.set(true)
        _isPlaying.tryEmit(true)

        // Drain queue on dedicated thread so writes don't compete with feed()
        pumpThread = Thread({ pumpLoop() }, "AmbientAudioPlayer-pump").apply {
            priority = Thread.MAX_PRIORITY
            isDaemon = true
            start()
        }
    }

    fun feed(pcm16Le: ByteArray, offset: Int = 0, length: Int = pcm16Le.size) {
        if (!playing.get()) return
        val copy = ByteArray(length)
        System.arraycopy(pcm16Le, offset, copy, 0, length)
        // Drop oldest if we're backed up (controller clearly can't keep up)
        if (!queue.offer(copy)) {
            queue.poll()
            queue.offer(copy)
        }
    }

    fun stop() {
        if (!playing.get()) return
        playing.set(false)
        _isPlaying.tryEmit(false)
        try { track?.stop() } catch (_: IllegalStateException) { /* already stopped */ }
        track?.release()
        track = null
        queue.clear()
        pumpThread?.interrupt()
        pumpThread = null
    }

    private fun pumpLoop() {
        val t = track ?: return
        while (playing.get()) {
            val chunk = try { queue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS) }
                       catch (_: InterruptedException) { break }
                       ?: continue
            try {
                t.write(chunk, 0, chunk.size)
            } catch (t2: Throwable) {
                Log.w(TAG, "Audio write failed — stopping pump", t2)
                break
            }
        }
    }

    private companion object { const val TAG = "AmbientAudioPlayer" }
}

package com.aistudio.missioncontrol.pxytwe.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Computes the peak amplitude (0..1) and RMS (0..1) of a 16-bit signed PCM
 * little-endian buffer. Used to drive the waveform graph in the UI without
 * needing the actual bytes downloaded for visualization.
 */
object AmplitudeAnalyzer {
    fun analyze(pcm16Le: ByteArray): Pair<Float, Float> {
        if (pcm16Le.isEmpty() || pcm16Le.size < 2) return 0f to 0f
        var peak = 0
        var sumSquares = 0.0
        var sampleCount = 0
        var i = 0
        while (i < pcm16Le.size - 1) {
            // little endian signed 16-bit
            val low = pcm16Le[i].toInt() and 0xff
            val high = pcm16Le[i + 1].toInt()
            val sample = ((high shl 8) or low).toShort().toInt()
            val mag = abs(sample)
            if (mag > peak) peak = mag
            sumSquares += (sample * sample).toDouble()
            sampleCount++
            i += 2
        }
        val peakNorm = (peak.toFloat() / Short.MAX_VALUE.toFloat()).coerceIn(0f, 1f)
        val rms = if (sampleCount > 0) {
            sqrt(sumSquares / sampleCount).toFloat() / Short.MAX_VALUE.toFloat()
        } else 0f
        return peakNorm.coerceAtMost(1f) to rms.coerceIn(0f, 1f)
    }

    fun normalizeForUi(peak: Float): Float = max(0f, peak.coerceAtMost(1f))
}

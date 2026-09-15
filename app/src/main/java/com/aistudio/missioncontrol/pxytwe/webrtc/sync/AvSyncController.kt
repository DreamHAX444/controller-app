package com.aistudio.missioncontrol.pxytwe.webrtc.sync

import android.util.Log
import com.aistudio.missioncontrol.pxytwe.webrtc.audio.PlaybackAudioPacket
import org.webrtc.VideoFrame

class AvSyncController(
    val sessionId: String,
    val config: AvSyncConfig = AvSyncConfig(),
    private val clock: () -> Long = { System.nanoTime() }
) {
    private val tag = "AvSyncController"
    val stats = AvSyncStats()

    private val audioOffsetFilter = MedianFilter(30)
    private val videoDelayFilter = MedianFilter(30)
    
    private var syncWaitStartNs = 0L

    fun onSessionStart() {
        stats.currentState = AvSyncState.IDLE
        Log.i(tag, "AV_SYNC_SESSION_START sessionId=$sessionId")
    }

    fun onVideoFrameArrived(frame: VideoFrame) {
        val now = clock()
        val delayNs = frame.timestampNs - now
        videoDelayFilter.add(delayNs)
        checkReadyState(now)
    }

    fun onAudioPacketArrived(packet: PlaybackAudioPacket) {
        val now = clock()
        val offsetNs = now - packet.captureTimestampNs
        audioOffsetFilter.add(offsetNs)
        checkReadyState(now)
    }

    private fun checkReadyState(now: Long) {
        if (stats.currentState == AvSyncState.IDLE) {
            stats.currentState = AvSyncState.BUFFERING
            syncWaitStartNs = now
            Log.i(tag, "AV_SYNC_BUFFERING sessionId=$sessionId")
        }
        if (stats.currentState == AvSyncState.BUFFERING && isReady()) {
            stats.currentState = AvSyncState.SYNC_ESTIMATED
            Log.i(tag, "AV_SYNC_SYNC_ESTIMATED sessionId=$sessionId")
        }
    }

    private fun isReady() = audioOffsetFilter.isReady() && videoDelayFilter.isReady()

    fun getScheduleDecision(packet: PlaybackAudioPacket): AudioScheduleDecision {
        val now = clock()
        
        checkReadyState(now)
        
        if (!isReady()) {
            val elapsedMs = (now - syncWaitStartNs) / 1_000_000
            if (elapsedMs > config.initialSyncTimeoutMs) {
                if (stats.currentState != AvSyncState.LIMITED) {
                    stats.currentState = AvSyncState.LIMITED
                    Log.w(tag, "AV_SYNC_LIMITED (Timeout waiting for streams) sessionId=$sessionId")
                }
                return AudioScheduleDecision.PlayNow // Fallback
            } else {
                return AudioScheduleDecision.WaitNs(10_000_000L) // Wait 10ms for buffers
            }
        }
        
        // EstimatedClockOffset maps Tracker capture time to Local presentation time
        val estimatedClockOffsetNs = audioOffsetFilter.median() + videoDelayFilter.median()
        val targetPresentationNs = packet.captureTimestampNs + estimatedClockOffsetNs
        
        val waitNs = targetPresentationNs - now
        val waitMs = waitNs / 1_000_000
        
        updateStats(waitMs)
        
        if (waitMs <= 0) {
            // Audio is behind schedule or exactly on time
            if (waitMs < -config.criticalDriftThresholdMs) {
                stats.droppedAudioFrames++
                Log.w(tag, "AV_SYNC_CORRECTION Drop audio frame (too late by ${-waitMs}ms)")
                return AudioScheduleDecision.Drop
            }
            return AudioScheduleDecision.PlayNow
        }
        
        // Audio is ahead of schedule
        return AudioScheduleDecision.WaitNs(waitNs)
    }

    private fun updateStats(offsetMs: Long) {
        stats.sampleCount++
        stats.currentOffsetMs = offsetMs
        stats.estimatedOffsetMs = (stats.estimatedOffsetMs * 0.9 + offsetMs * 0.1).toLong()
        
        if (offsetMs < stats.offsetMinMs) stats.offsetMinMs = offsetMs
        if (offsetMs > stats.offsetMaxMs) stats.offsetMaxMs = offsetMs
        
        val absOffset = Math.abs(offsetMs)
        
        val prevState = stats.currentState
        val newState = when {
            prevState == AvSyncState.LIMITED -> AvSyncState.LIMITED
            absOffset > config.largeDriftThresholdMs -> AvSyncState.CORRECTING
            absOffset > config.driftThresholdMs -> AvSyncState.DRIFTING
            else -> AvSyncState.SYNC_ESTIMATED
        }
        
        if (prevState != newState && prevState != AvSyncState.STARVED && prevState != AvSyncState.LIMITED) {
            stats.currentState = newState
            Log.i(tag, "AV_SYNC_DRIFT State changed $prevState -> $newState (offset=${offsetMs}ms)")
        }
    }

    fun onStarved() {
        if (stats.currentState != AvSyncState.STARVED) {
            stats.currentState = AvSyncState.STARVED
            stats.starvationEvents++
            Log.w(tag, "AV_SYNC_STARVED sessionId=$sessionId")
        }
    }

    fun onRecovered() {
        if (stats.currentState == AvSyncState.STARVED) {
            stats.currentState = AvSyncState.BUFFERING
            Log.i(tag, "AV_SYNC_RECOVERED sessionId=$sessionId")
            
            audioOffsetFilter.clear()
            videoDelayFilter.clear()
        }
    }

    fun onSessionStop() {
        Log.i(tag, "AV_SYNC_SESSION_STOP sessionId=$sessionId")
        stats.currentState = AvSyncState.IDLE
        audioOffsetFilter.clear()
        videoDelayFilter.clear()
    }
}

class MedianFilter(private val windowSize: Int) {
    private val samples = LongArray(windowSize)
    private var count = 0
    private var index = 0

    fun add(value: Long) {
        if (count > windowSize / 2) {
            val med = median()
            if (Math.abs(value - med) > 500_000_000L) {
                return // reject obvious outlier (> 500ms spike)
            }
        }
        if (count < windowSize) count++
        samples[index] = value
        index = (index + 1) % windowSize
    }

    fun median(): Long {
        if (count == 0) return 0L
        val sorted = samples.take(count).sorted()
        return if (count % 2 == 0) {
            (sorted[count / 2 - 1] + sorted[count / 2]) / 2
        } else {
            sorted[count / 2]
        }
    }
    
    fun isReady(): Boolean = count > 0
    
    fun clear() {
        count = 0
        index = 0
    }
}

sealed class AudioScheduleDecision {
    object PlayNow : AudioScheduleDecision()
    data class WaitNs(val waitNs: Long) : AudioScheduleDecision()
    object Drop : AudioScheduleDecision()
}

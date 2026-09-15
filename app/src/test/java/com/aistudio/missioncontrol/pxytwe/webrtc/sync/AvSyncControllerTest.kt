package com.aistudio.missioncontrol.pxytwe.webrtc.sync

import com.aistudio.missioncontrol.pxytwe.webrtc.audio.PlaybackAudioPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.webrtc.VideoFrame
import org.webrtc.NV21Buffer

class AvSyncControllerTest {

    private var mockTimeNs = 200_000_000L
    private val clock = { mockTimeNs }

    private fun createAudioPacket(seq: Long, captureTs: Long): PlaybackAudioPacket {
        return PlaybackAudioPacket(
            sessionId = "s",
            deviceId = "d",
            sequence = seq,
            captureTimestampNs = captureTs,
            payload = ByteArray(1920)
        )
    }

    private fun createVideoFrame(presentationTs: Long): VideoFrame {
        val buffer = NV21Buffer(ByteArray(4), 2, 2, null)
        return VideoFrame(buffer, 0, presentationTs)
    }

    @Test
    fun `A - Startup synchronization and B - Estimated clock mapping`() {
        val controller = AvSyncController("s", AvSyncConfig(), clock)
        
        controller.onVideoFrameArrived(createVideoFrame(mockTimeNs)) 
        assertEquals(AvSyncState.BUFFERING, controller.stats.currentState)
        
        mockTimeNs += 1_000_000L // 1ms later
        controller.onAudioPacketArrived(createAudioPacket(1, 50_000_000L)) // Captured at 50ms
        
        val decision = controller.getScheduleDecision(createAudioPacket(1, 50_000_000L))
        assertEquals(AvSyncState.SYNC_ESTIMATED, controller.stats.currentState)
    }

    @Test
    fun `C - Audio +10ms (Audio Ahead)`() {
        val controller = AvSyncController("s", AvSyncConfig(), clock)
        
        val trackerTime = 50_000_000L
        
        // Arrivals are perfectly synced
        controller.onVideoFrameArrived(createVideoFrame(mockTimeNs))
        controller.onAudioPacketArrived(createAudioPacket(1, trackerTime))
        
        mockTimeNs += 10_000_000L // 10ms passed in real time
        
        // packet is 20ms advanced in capture time (so it's 10ms early relative to the 10ms real time passed)
        val packet2 = createAudioPacket(2, trackerTime + 20_000_000L)
        val decision = controller.getScheduleDecision(packet2)
        
        assertTrue(decision is AudioScheduleDecision.WaitNs)
        val waitDecision = decision as AudioScheduleDecision.WaitNs
        assertEquals(10_000_000L, waitDecision.waitNs)
    }

    @Test
    fun `D - Audio -10ms (Audio Behind)`() {
        val controller = AvSyncController("s", AvSyncConfig(), clock)
        
        val trackerTime = 50_000_000L
        
        controller.onVideoFrameArrived(createVideoFrame(mockTimeNs))
        controller.onAudioPacketArrived(createAudioPacket(1, trackerTime))
        
        mockTimeNs += 30_000_000L // 30ms passed in real time
        
        // packet is only 20ms advanced (so it's 10ms late)
        val packet2 = createAudioPacket(2, trackerTime + 20_000_000L)
        val decision = controller.getScheduleDecision(packet2)
        
        assertTrue(decision is AudioScheduleDecision.PlayNow)
    }

    @Test
    fun `L - Critical negative drift`() {
        val controller = AvSyncController("s", AvSyncConfig(), clock)
        
        val trackerTime = 50_000_000L
        
        controller.onVideoFrameArrived(createVideoFrame(mockTimeNs))
        controller.onAudioPacketArrived(createAudioPacket(1, trackerTime))
        
        mockTimeNs += 500_000_000L // 500ms passed in real time!
        
        val packet2 = createAudioPacket(2, trackerTime + 20_000_000L) // 20ms advanced (so it's 480ms late!)
        val decision = controller.getScheduleDecision(packet2)
        
        assertTrue(decision is AudioScheduleDecision.Drop)
        assertEquals(1, controller.stats.droppedAudioFrames)
    }

    @Test
    fun `O - Starvation and P - Recovery`() {
        val controller = AvSyncController("s", AvSyncConfig(), clock)
        
        controller.onSessionStart()
        controller.onStarved()
        assertEquals(AvSyncState.STARVED, controller.stats.currentState)
        assertEquals(1, controller.stats.starvationEvents)
        
        controller.onRecovered()
        assertEquals(AvSyncState.BUFFERING, controller.stats.currentState)
    }

    @Test
    fun `P - Session isolation and R - Cleanup`() {
        val controller = AvSyncController("s", AvSyncConfig(), clock)
        controller.onSessionStart()
        assertEquals(AvSyncState.IDLE, controller.stats.currentState)
        
        controller.onVideoFrameArrived(createVideoFrame(mockTimeNs))
        assertEquals(AvSyncState.BUFFERING, controller.stats.currentState)
        
        controller.onSessionStop()
        assertEquals(AvSyncState.IDLE, controller.stats.currentState)
    }
}

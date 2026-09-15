package com.aistudio.missioncontrol.pxytwe.audio

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.delay

@OptIn(ExperimentalCoroutinesApi::class)
class AudioBufferManagerTest {

    private val sessionId = "session-1"

    private fun frame(seq: Long, sid: String = sessionId): EncodedAudioFrame {
        return EncodedAudioFrame(
            sessionId = sid,
            deviceId = "device-1",
            seq = seq,
            timestamp = 1000L,
            encoding = "PCM_16BIT_8000HZ_MONO",
            durationMs = 250,
            payload = "fake-$seq"
        )
    }

    // ─── Test A ────────────────────────────────────────────────────────────────
    @Test
    fun testA_sequentialFrames() = runTest(UnconfinedTestDispatcher()) {
        val manager = AudioBufferManager(sessionId, this)
        val incoming = MutableSharedFlow<EncodedAudioFrame>(extraBufferCapacity = 20)
        
        val emitted = mutableListOf<EncodedAudioFrame>()
        val job = launch { manager.outputFlow.collect { emitted.add(it) } }

        manager.start(incoming)

        incoming.emit(frame(0))
        incoming.emit(frame(1))
        incoming.emit(frame(2))
        incoming.emit(frame(3)) // Triggers STARTUP_TARGET_FRAMES (4)

        assertEquals(4, emitted.size)
        assertEquals(0L, emitted[0].seq)
        assertEquals(1L, emitted[1].seq)
        assertEquals(2L, emitted[2].seq)
        assertEquals(3L, emitted[3].seq)

        manager.close()
        job.cancel()
    }

    // ─── Test B ────────────────────────────────────────────────────────────────
    @Test
    fun testB_outOfOrderFrames() = runTest(UnconfinedTestDispatcher()) {
        val manager = AudioBufferManager(sessionId, this)
        val incoming = MutableSharedFlow<EncodedAudioFrame>(extraBufferCapacity = 20)
        
        val emitted = mutableListOf<EncodedAudioFrame>()
        val job = launch { manager.outputFlow.collect { emitted.add(it) } }

        manager.start(incoming)

        incoming.emit(frame(0))
        incoming.emit(frame(2))
        incoming.emit(frame(1))
        incoming.emit(frame(3)) // Reaches 4 frames

        assertEquals(4, emitted.size)
        assertEquals(0L, emitted[0].seq)
        assertEquals(1L, emitted[1].seq)
        assertEquals(2L, emitted[2].seq)
        assertEquals(3L, emitted[3].seq)

        manager.close()
        job.cancel()
    }

    // ─── Test C ────────────────────────────────────────────────────────────────
    @Test
    fun testC_duplicateFrames() = runTest(UnconfinedTestDispatcher()) {
        val manager = AudioBufferManager(sessionId, this)
        val incoming = MutableSharedFlow<EncodedAudioFrame>(extraBufferCapacity = 20)
        
        val emitted = mutableListOf<EncodedAudioFrame>()
        val job = launch { manager.outputFlow.collect { emitted.add(it) } }

        manager.start(incoming)

        incoming.emit(frame(0))
        incoming.emit(frame(1))
        incoming.emit(frame(1)) // Duplicate
        incoming.emit(frame(2))
        incoming.emit(frame(3)) // Reaches 4 unique frames

        assertEquals(4, emitted.size)
        assertEquals(0L, emitted[0].seq)
        assertEquals(1L, emitted[1].seq)
        assertEquals(2L, emitted[2].seq)
        assertEquals(3L, emitted[3].seq)

        manager.close()
        job.cancel()
    }

    // ─── Test D ────────────────────────────────────────────────────────────────
    @Test
    fun testD_lateFrames() = runTest(UnconfinedTestDispatcher()) {
        val manager = AudioBufferManager(sessionId, this)
        val incoming = MutableSharedFlow<EncodedAudioFrame>(extraBufferCapacity = 20)
        
        val emitted = mutableListOf<EncodedAudioFrame>()
        val job = launch { manager.outputFlow.collect { emitted.add(it) } }

        manager.start(incoming)

        incoming.emit(frame(0))
        incoming.emit(frame(1))
        incoming.emit(frame(2))
        incoming.emit(frame(3)) // Outputs 0,1,2,3

        incoming.emit(frame(1)) // Late frame, already emitted

        assertEquals(4, emitted.size) // No extra emission

        manager.close()
        job.cancel()
    }

    // ─── Test E ────────────────────────────────────────────────────────────────
    @Test
    fun testE_futureFrames() = runTest(UnconfinedTestDispatcher()) {
        val manager = AudioBufferManager(sessionId, this)
        val incoming = MutableSharedFlow<EncodedAudioFrame>(extraBufferCapacity = 20)
        
        val emitted = mutableListOf<EncodedAudioFrame>()
        val job = launch { manager.outputFlow.collect { emitted.add(it) } }

        manager.start(incoming)

        incoming.emit(frame(0))
        incoming.emit(frame(2))
        incoming.emit(frame(3))
        incoming.emit(frame(4)) // 4 unique frames: 0, 2, 3, 4. Outputs 0. 1 is missing.
        
        assertEquals(1, emitted.size)
        assertEquals(0L, emitted[0].seq)

        incoming.emit(frame(1)) // Missing frame arrives

        // Now outputs 1, 2, 3, 4
        assertEquals(5, emitted.size)
        assertEquals(4L, emitted[4].seq)

        manager.close()
        job.cancel()
    }

    // ─── Test F ────────────────────────────────────────────────────────────────
    @Test
    fun testF_missingFrame() = runTest(UnconfinedTestDispatcher()) {
        val manager = AudioBufferManager(sessionId, this)
        val incoming = MutableSharedFlow<EncodedAudioFrame>(extraBufferCapacity = 20)
        
        val emitted = mutableListOf<EncodedAudioFrame>()
        val job = launch { manager.outputFlow.collect { emitted.add(it) } }

        manager.start(incoming)

        incoming.emit(frame(0))
        incoming.emit(frame(1))
        incoming.emit(frame(3))
        incoming.emit(frame(4)) // Reaches 4 frames. State -> PLAYING. Outputs 0, 1.
        runCurrent()

        assertEquals(2, emitted.size)
        assertEquals(0L, emitted[0].seq)
        assertEquals(1L, emitted[1].seq)
        
        // Wait for the missing frame timeout to expire
        advanceTimeBy(600L)
        runCurrent()
        
        // Output should now skip 2 and output 3, 4
        assertEquals(4, emitted.size)
        assertEquals(3L, emitted[2].seq)
        assertEquals(4L, emitted[3].seq)

        manager.close()
        job.cancel()
    }

    // ─── Test G ────────────────────────────────────────────────────────────────
    @Test
    fun testG_maximumBuffer() = runTest(UnconfinedTestDispatcher()) {
        val manager = AudioBufferManager(sessionId, this)
        val incoming = MutableSharedFlow<EncodedAudioFrame>(extraBufferCapacity = 20)
        
        manager.start(incoming)

        // Do not collect output, simulate blocked consumer.
        // Or send far future frames
        for (i in 0..15) {
            incoming.emit(frame(i.toLong() + 100)) // sequences 100-115
        }

        // Buffer size must be capped at 10.
        // It drops oldest (100, 101, 102...) and keeps newest 10 (106..115)
        assertEquals(AudioBufferState.PLAYING, manager.state)
        
        // Check nextSequenceToEmit has advanced past dropped frames
        assertTrue(manager.nextSequenceToEmit != null && manager.nextSequenceToEmit!! >= 106L)

        manager.close()
    }

    // ─── Test H ────────────────────────────────────────────────────────────────
    @Test
    fun testH_startupBuffer() = runTest(UnconfinedTestDispatcher()) {
        val manager = AudioBufferManager(sessionId, this)
        val incoming = MutableSharedFlow<EncodedAudioFrame>(extraBufferCapacity = 20)
        
        val emitted = mutableListOf<EncodedAudioFrame>()
        val job = launch { manager.outputFlow.collect { emitted.add(it) } }

        manager.start(incoming)

        incoming.emit(frame(0))
        incoming.emit(frame(1))
        incoming.emit(frame(2))

        // State is BUFFERING, 3 frames in buffer, no emission
        assertEquals(AudioBufferState.BUFFERING, manager.state)
        assertEquals(0, emitted.size)

        incoming.emit(frame(3))

        // State becomes PLAYING, 4 frames emitted
        assertEquals(AudioBufferState.PLAYING, manager.state)
        assertEquals(4, emitted.size)

        manager.close()
        job.cancel()
    }

    // ─── Test I & J ────────────────────────────────────────────────────────────
    @Test
    fun testIJ_sessionIsolation() = runTest(UnconfinedTestDispatcher()) {
        val managerA = AudioBufferManager("sessionA", this)
        val managerB = AudioBufferManager("sessionB", this)
        
        val incomingA = MutableSharedFlow<EncodedAudioFrame>(extraBufferCapacity = 20)
        val incomingB = MutableSharedFlow<EncodedAudioFrame>(extraBufferCapacity = 20)

        val emittedA = mutableListOf<EncodedAudioFrame>()
        val emittedB = mutableListOf<EncodedAudioFrame>()
        
        val jobA = launch { managerA.outputFlow.collect { emittedA.add(it) } }
        val jobB = launch { managerB.outputFlow.collect { emittedB.add(it) } }

        managerA.start(incomingA)
        managerB.start(incomingB)

        // Session A frames into incomingA
        for (i in 0..3) incomingA.emit(frame(i.toLong(), "sessionA"))
        assertEquals(4, emittedA.size)

        // Session A frame into incomingB (must reject!)
        incomingB.emit(frame(0L, "sessionA"))
        assertEquals(AudioBufferState.BUFFERING, managerB.state)
        assertEquals(0, emittedB.size)

        // Session B frames into incomingB
        for (i in 0..3) incomingB.emit(frame(i.toLong(), "sessionB"))
        assertEquals(4, emittedB.size)

        managerA.close()
        managerB.close()
        jobA.cancel()
        jobB.cancel()
    }

    // ─── Test K ────────────────────────────────────────────────────────────────
    @Test
    fun testK_payloadPreservation() = runTest(UnconfinedTestDispatcher()) {
        val manager = AudioBufferManager(sessionId, this)
        val incoming = MutableSharedFlow<EncodedAudioFrame>(extraBufferCapacity = 20)
        
        val emitted = mutableListOf<EncodedAudioFrame>()
        val job = launch { manager.outputFlow.collect { emitted.add(it) } }

        manager.start(incoming)

        for (i in 0..3) incoming.emit(frame(i.toLong()))

        val emittedFrame = emitted[0]
        assertEquals("fake-0", emittedFrame.payload)
        assertEquals("PCM_16BIT_8000HZ_MONO", emittedFrame.encoding)
        assertEquals(250, emittedFrame.durationMs)

        manager.close()
        job.cancel()
    }

    // ─── Test L ────────────────────────────────────────────────────────────────
    @Test
    fun testL_closeClearsEverything() = runTest(UnconfinedTestDispatcher()) {
        val manager = AudioBufferManager(sessionId, this)
        val incoming = MutableSharedFlow<EncodedAudioFrame>(extraBufferCapacity = 20)
        
        val emitted = mutableListOf<EncodedAudioFrame>()
        val job = launch { manager.outputFlow.collect { emitted.add(it) } }

        manager.start(incoming)

        incoming.emit(frame(0))
        incoming.emit(frame(1))
        
        assertEquals(AudioBufferState.BUFFERING, manager.state)

        manager.close()
        assertEquals(AudioBufferState.CLOSED, manager.state)

        // Emit more frames
        incoming.emit(frame(2))
        incoming.emit(frame(3))
        
        assertEquals(0, emitted.size) // No output
        
        job.cancel()
    }

    // ─── Test M ────────────────────────────────────────────────────────────────
    @Test
    fun testM_concurrentInput() = runTest(UnconfinedTestDispatcher()) {
        val manager = AudioBufferManager(sessionId, this)
        val incoming = MutableSharedFlow<EncodedAudioFrame>(extraBufferCapacity = 100)
        
        val emitted = mutableListOf<EncodedAudioFrame>()
        val job = launch { manager.outputFlow.collect { emitted.add(it) } }

        manager.start(incoming)

        // Simulate concurrent emissions
        launch { incoming.emit(frame(1)) }
        launch { incoming.emit(frame(3)) }
        launch { incoming.emit(frame(0)) }
        launch { incoming.emit(frame(2)) }
        launch { incoming.emit(frame(4)) } // Reaches startup (4) and then some

        // Deterministic ordered output
        assertEquals(5, emitted.size)
        assertEquals(0L, emitted[0].seq)
        assertEquals(1L, emitted[1].seq)
        assertEquals(2L, emitted[2].seq)
        assertEquals(3L, emitted[3].seq)
        assertEquals(4L, emitted[4].seq)

        manager.close()
        job.cancel()
    }
}

package com.aistudio.missioncontrol.pxytwe.webrtc.signaling

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Step 4B JVM tests for ControllerWebRtcSignalingSession.
 * Uses UnconfinedTestDispatcher for eager, deterministic coroutine execution.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ControllerWebRtcNegotiationTest {

    // ─── A. start() sends START_REQUEST to Tracker ────────────────────────────

    @Test
    fun `start sends START_REQUEST signal`() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeWebRtcSignalingTransport()
        val session = ControllerWebRtcSignalingSession("sess-A", "ctrl-dev", transport, backgroundScope)
        session.start()

        assertEquals(WebRtcSignalingState.WAITING_FOR_TRACKER, session.state.value)
        assertTrue("start() must send START_REQUEST", transport.sentMessages.any { it.type == SignalType.START_REQUEST })
    }

    // ─── B. READY from Tracker transitions to ACTIVE ──────────────────────────

    @Test
    fun `READY from Tracker transitions session to ACTIVE`() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeWebRtcSignalingTransport()
        val session = ControllerWebRtcSignalingSession("sess-A", "ctrl-dev", transport, backgroundScope)
        session.start()

        transport.injectRemoteSignal(
            WebRtcSignalMessage("sess-A", "tracker-dev", SignalType.READY, 0L, "m1")
        )

        assertEquals(WebRtcSignalingState.ACTIVE, session.state.value)
    }

    // ─── C. Duplicate messageId is deduped ────────────────────────────────────

    @Test
    fun `duplicate READY is deduped by messageId`() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeWebRtcSignalingTransport()
        val session = ControllerWebRtcSignalingSession("sess-A", "ctrl-dev", transport, backgroundScope)
        session.start()

        val received = mutableListOf<WebRtcSignalMessage>()
        backgroundScope.launch { session.incomingSignals.collect { received.add(it) } }

        val ready = WebRtcSignalMessage("sess-A", "tracker-dev", SignalType.READY, 0L, "fixed-id")
        transport.injectRemoteSignal(ready)
        transport.injectRemoteSignal(ready) // exact duplicate

        assertEquals("Duplicate READY must appear exactly once", 1,
            received.count { it.type == SignalType.READY })
    }

    // ─── D. Wrong session ID rejected ─────────────────────────────────────────

    @Test
    fun `wrong session ID is rejected`() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeWebRtcSignalingTransport()
        val session = ControllerWebRtcSignalingSession("sess-A", "ctrl-dev", transport, backgroundScope)
        session.start()

        transport.injectRemoteSignal(
            WebRtcSignalMessage("sess-WRONG", "tracker-dev", SignalType.READY, 0L, "m1")
        )

        assertEquals("Wrong sessionId must be rejected", WebRtcSignalingState.WAITING_FOR_TRACKER, session.state.value)
    }

    // ─── E. Own deviceId rejected (echo prevention) ───────────────────────────

    @Test
    fun `own deviceId is rejected (echo prevention)`() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeWebRtcSignalingTransport()
        val session = ControllerWebRtcSignalingSession("sess-A", "ctrl-dev", transport, backgroundScope)
        session.start()

        transport.injectRemoteSignal(
            WebRtcSignalMessage("sess-A", "ctrl-dev", SignalType.READY, 0L, "m1")
        )

        assertEquals("Echo from self must be rejected", WebRtcSignalingState.WAITING_FOR_TRACKER, session.state.value)
    }

    // ─── F. OFFER and ICE_CANDIDATE forwarded to incomingSignals ──────────────

    @Test
    fun `OFFER and ICE_CANDIDATE are forwarded via incomingSignals`() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeWebRtcSignalingTransport()
        val session = ControllerWebRtcSignalingSession("sess-A", "ctrl-dev", transport, backgroundScope)
        session.start()

        val received = mutableListOf<WebRtcSignalMessage>()
        backgroundScope.launch { session.incomingSignals.collect { received.add(it) } }

        transport.injectRemoteSignal(WebRtcSignalMessage("sess-A", "tracker-dev", SignalType.OFFER, 0L, "m1"))
        transport.injectRemoteSignal(WebRtcSignalMessage("sess-A", "tracker-dev", SignalType.ICE_CANDIDATE, 0L, "m2"))

        val types = received.map { it.type }
        assertTrue("OFFER must be forwarded", SignalType.OFFER in types)
        assertTrue("ICE_CANDIDATE must be forwarded", SignalType.ICE_CANDIDATE in types)
    }

    // ─── G. SESSION_ENDED closes session ──────────────────────────────────────

    @Test
    fun `SESSION_ENDED closes the session`() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeWebRtcSignalingTransport()
        val session = ControllerWebRtcSignalingSession("sess-A", "ctrl-dev", transport, backgroundScope)
        session.start()

        transport.injectRemoteSignal(WebRtcSignalMessage("sess-A", "tracker-dev", SignalType.READY, 0L, "m1"))
        assertEquals(WebRtcSignalingState.ACTIVE, session.state.value)

        transport.injectRemoteSignal(WebRtcSignalMessage("sess-A", "tracker-dev", SignalType.SESSION_ENDED, 0L, "m2"))
        assertEquals(WebRtcSignalingState.CLOSED, session.state.value)
    }

    // ─── H. Closed session ignores signals ────────────────────────────────────

    @Test
    fun `closed session ignores further signals`() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeWebRtcSignalingTransport()
        val session = ControllerWebRtcSignalingSession("sess-A", "ctrl-dev", transport, backgroundScope)
        session.start()
        session.stop()
        assertEquals(WebRtcSignalingState.CLOSED, session.state.value)

        transport.injectRemoteSignal(WebRtcSignalMessage("sess-A", "tracker-dev", SignalType.READY, 0L, "m1"))
        assertEquals("Closed session must stay CLOSED", WebRtcSignalingState.CLOSED, session.state.value)
    }

    // ─── I. Session isolation ─────────────────────────────────────────────────

    @Test
    fun `session A signals do not affect session B`() = runTest(UnconfinedTestDispatcher()) {
        val transportA = FakeWebRtcSignalingTransport()
        val transportB = FakeWebRtcSignalingTransport()
        val sessionA = ControllerWebRtcSignalingSession("sess-A", "ctrl-dev", transportA, backgroundScope)
        val sessionB = ControllerWebRtcSignalingSession("sess-B", "ctrl-dev", transportB, backgroundScope)
        sessionA.start()
        sessionB.start()

        transportA.injectRemoteSignal(WebRtcSignalMessage("sess-A", "tracker-dev", SignalType.READY, 0L, "m1"))

        assertEquals("Session A must be ACTIVE", WebRtcSignalingState.ACTIVE, sessionA.state.value)
        assertEquals("Session B must remain WAITING", WebRtcSignalingState.WAITING_FOR_TRACKER, sessionB.state.value)
    }

    // ─── J. sendSignal blocked when CLOSED ────────────────────────────────────

    @Test
    fun `sendSignal blocked when session is CLOSED`() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeWebRtcSignalingTransport()
        val session = ControllerWebRtcSignalingSession("sess-A", "ctrl-dev", transport, backgroundScope)
        session.start()
        session.stop()

        val sentBefore = transport.sentMessages.size
        session.sendSignal(SignalType.ICE_CANDIDATE)
        assertEquals("Closed session must not send", sentBefore, transport.sentMessages.size)
    }

    // ─── K. OFFER transitions session from WAITING_FOR_TRACKER to ACTIVE ──────

    @Test
    fun `OFFER transitions session from WAITING_FOR_TRACKER to ACTIVE`() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeWebRtcSignalingTransport()
        val session = ControllerWebRtcSignalingSession("sess-A", "ctrl-dev", transport, backgroundScope)
        session.start()

        assertEquals(WebRtcSignalingState.WAITING_FOR_TRACKER, session.state.value)

        transport.injectRemoteSignal(
            WebRtcSignalMessage("sess-A", "tracker-dev", SignalType.OFFER, 0L, "offer-1")
        )

        assertEquals(WebRtcSignalingState.ACTIVE, session.state.value)
    }

    // ─── L. READY with ReadyPayload transitions session to ACTIVE ─────────────

    @Test
    fun `READY with ReadyPayload transitions session to ACTIVE`() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeWebRtcSignalingTransport()
        val session = ControllerWebRtcSignalingSession("sess-A", "ctrl-dev", transport, backgroundScope)
        session.start()

        val payload = kotlinx.serialization.json.Json.encodeToJsonElement(ReadyPayload.serializer(), ReadyPayload(1L))
        transport.injectRemoteSignal(
            WebRtcSignalMessage("sess-A", "tracker-dev", SignalType.READY, 0L, "ready-1", payload = payload)
        )

        assertEquals(WebRtcSignalingState.ACTIVE, session.state.value)
    }
}

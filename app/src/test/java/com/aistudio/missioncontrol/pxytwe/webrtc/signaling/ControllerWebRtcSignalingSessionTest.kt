package com.aistudio.missioncontrol.pxytwe.webrtc.signaling

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.test.UnconfinedTestDispatcher

class ControllerWebRtcSignalingSessionTest {
    private val sessionId = "test-session-id"
    private val deviceId = "controller-device-id"

    @Test
    fun `session connects and sends START_REQUEST`() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeWebRtcSignalingTransport()
        val session = ControllerWebRtcSignalingSession(sessionId, deviceId, transport, backgroundScope)
        session.start()
        assertEquals(WebRtcSignalingState.WAITING_FOR_TRACKER, session.state.value)
        assertTrue(transport.sentMessages.any { it.type == SignalType.START_REQUEST })
    }

    @Test
    fun `session transitions to ACTIVE on READY`() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeWebRtcSignalingTransport()
        val session = ControllerWebRtcSignalingSession(sessionId, deviceId, transport, backgroundScope)
        session.start()
        
        val readyMsg = WebRtcSignalMessage(
            sessionId = sessionId,
            deviceId = "tracker-device-id",
            type = SignalType.READY,
            timestamp = 12345L,
            messageId = "msg-1"
        )
        transport.injectRemoteSignal(readyMsg)
        
        assertEquals(WebRtcSignalingState.ACTIVE, session.state.value)
    }

    @Test
    fun `rejects messages with wrong session ID`() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeWebRtcSignalingTransport()
        val session = ControllerWebRtcSignalingSession(sessionId, deviceId, transport, backgroundScope)
        session.start()
        
        val readyMsg = WebRtcSignalMessage(
            sessionId = "wrong-session-id",
            deviceId = "tracker-device-id",
            type = SignalType.READY,
            timestamp = 12345L,
            messageId = "msg-1"
        )
        transport.injectRemoteSignal(readyMsg)
        
        assertEquals(WebRtcSignalingState.WAITING_FOR_TRACKER, session.state.value)
    }

    @Test
    fun `ignores duplicate messages`() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeWebRtcSignalingTransport()
        val session = ControllerWebRtcSignalingSession(sessionId, deviceId, transport, backgroundScope)
        session.start()
        
        val msg1 = WebRtcSignalMessage(sessionId, "tracker", SignalType.READY, 0L, "msg-1")
        val msg2 = WebRtcSignalMessage(sessionId, "tracker", SignalType.SESSION_ENDED, 0L, "msg-1")
        
        transport.injectRemoteSignal(msg1)
        assertEquals(WebRtcSignalingState.ACTIVE, session.state.value)
        
        transport.injectRemoteSignal(msg2)
        assertEquals(WebRtcSignalingState.ACTIVE, session.state.value)
    }

    @Test
    fun `session ended transitions to closed`() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeWebRtcSignalingTransport()
        val session = ControllerWebRtcSignalingSession(sessionId, deviceId, transport, backgroundScope)
        session.start()
        val endMsg = WebRtcSignalMessage(sessionId, "tracker", SignalType.SESSION_ENDED, 0L, "msg-1")
        transport.injectRemoteSignal(endMsg)
        
        assertEquals(WebRtcSignalingState.CLOSED, session.state.value)
    }
}

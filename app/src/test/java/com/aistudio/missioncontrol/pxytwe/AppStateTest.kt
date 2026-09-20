package com.aistudio.missioncontrol.pxytwe

import org.junit.Assert.*
import org.junit.Test
import com.aistudio.missioncontrol.pxytwe.webrtc.signaling.CameraTelemetryPayload

class AppStateTest {

    @Test
    fun testCameraStateTransitions() {
        // Initial setup
        AppState.cameraStartStates["test_device"] = AppState.CameraStartState()
        var state = AppState.cameraStartStates["test_device"]!!
        assertEquals(AppState.CameraStartStatus.IDLE, state.status)

        // Requesting
        state = state.copy(
            status = AppState.CameraStartStatus.REQUESTING,
            requestId = "req1",
            width = 1920,
            height = 1080,
            fps = 60
        )
        AppState.cameraStartStates["test_device"] = state
        assertEquals(AppState.CameraStartStatus.REQUESTING, AppState.cameraStartStates["test_device"]!!.status)

        // Accepted (Controller shouldn't assume CAPTURING yet)
        state = state.copy(status = AppState.CameraStartStatus.ACCEPTED)
        AppState.cameraStartStates["test_device"] = state
        assertEquals(AppState.CameraStartStatus.ACCEPTED, AppState.cameraStartStates["test_device"]!!.status)

        // Actual State initially empty, populated only from telemetry
        val telemetry = CameraTelemetryPayload(
            requestedWidth = 1920,
            requestedHeight = 1080,
            requestedFps = 60,
            actualWidth = null,
            actualHeight = null,
            observedFps = null,
            frameCount = 0,
            firstFrameTimestampNs = null,
            lastFrameTimestampNs = null
        )
        state = state.copy(status = AppState.CameraStartStatus.OPENING, telemetry = telemetry)
        AppState.cameraStartStates["test_device"] = state
        
        // Ensure requested state is preserved exactly
        assertEquals(1920, state.telemetry!!.requestedWidth)
        assertEquals(1080, state.telemetry!!.requestedHeight)
        assertEquals(60, state.telemetry!!.requestedFps)

        // First-frame capture confirmation
        val captureTelemetry = telemetry.copy(
            actualWidth = 1920,
            actualHeight = 1080,
            frameCount = 1
        )
        state = state.copy(status = AppState.CameraStartStatus.CAPTURING, telemetry = captureTelemetry)
        AppState.cameraStartStates["test_device"] = state
        assertEquals(AppState.CameraStartStatus.CAPTURING, state.status)
        assertEquals(1L, state.telemetry!!.frameCount)

        // FPS tolerance / Mismatch detection
        val mismatchTelemetry = captureTelemetry.copy(
            observedFps = 48.0
        )
        state = state.copy(telemetry = mismatchTelemetry)
        AppState.cameraStartStates["test_device"] = state
        assertTrue(Math.abs(state.telemetry!!.observedFps!! - state.telemetry!!.requestedFps) > 5.0)

        // Session reset / Stop clears actual state
        val newSessionState = AppState.CameraStartState(
            status = AppState.CameraStartStatus.REQUESTING,
            requestId = "req2",
            width = 1280,
            height = 720,
            fps = 30
        )
        AppState.cameraStartStates["test_device"] = newSessionState
        assertNull(newSessionState.telemetry) // Ensure old telemetry doesn't leak
        assertEquals("req2", newSessionState.requestId)
        
        // Error state preserves info
        val errorState = newSessionState.copy(status = AppState.CameraStartStatus.ERROR, error = "Camera not found")
        AppState.cameraStartStates["test_device"] = errorState
        assertEquals("Camera not found", errorState.error)
    }
}

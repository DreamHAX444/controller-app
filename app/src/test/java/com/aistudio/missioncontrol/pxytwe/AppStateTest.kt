package com.aistudio.missioncontrol.pxytwe

import com.aistudio.missioncontrol.pxytwe.webrtc.signaling.CameraTelemetryPayload
import com.aistudio.missioncontrol.pxytwe.CommandPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlinx.serialization.json.Json

@OptIn(ExperimentalCoroutinesApi::class)
class AppStateTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        AppState.cameraStartStates.clear()
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testStaleTelemetryRejected() = runTest {
        val deviceId = "device_123"
        // Setup existing state with activeGeneration = 3
        AppState.cameraStartStates[deviceId] = AppState.CameraStartState(
            activeGeneration = 3L
        )

        // Receive telemetry with generation 2
        val oldTelemetry = CameraTelemetryPayload(
            requestedWidth = 1920, requestedHeight = 1080, requestedFps = 30,
            actualWidth = 1920, actualHeight = 1080, observedFps = 30.0, frameCount = 10,
            firstFrameTimestampNs = 0L, lastFrameTimestampNs = 0L,
            cameraGeneration = 2L
        )
        val cmd2 = CommandPayload(
            command = "camera_capture_telemetry",
            device_id = deviceId,
            params = Json.encodeToString(CameraTelemetryPayload.serializer(), oldTelemetry)
        )
        AppState.handleIncomingCommand(cmd2)
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify it was ignored
        assertNull(AppState.cameraStartStates[deviceId]?.telemetry)
    }

    @Test
    fun testFutureTelemetryRejected() = runTest {
        val deviceId = "device_123"
        AppState.cameraStartStates[deviceId] = AppState.CameraStartState(
            activeGeneration = 3L
        )

        // Receive telemetry with generation 4 (future)
        val futureTelemetry = CameraTelemetryPayload(
            requestedWidth = 1920, requestedHeight = 1080, requestedFps = 30,
            actualWidth = 1920, actualHeight = 1080, observedFps = 30.0, frameCount = 10,
            firstFrameTimestampNs = 0L, lastFrameTimestampNs = 0L,
            cameraGeneration = 4L
        )
        val cmd = CommandPayload(
            command = "camera_capture_telemetry",
            device_id = deviceId,
            params = Json.encodeToString(CameraTelemetryPayload.serializer(), futureTelemetry)
        )
        AppState.handleIncomingCommand(cmd)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(AppState.cameraStartStates[deviceId]?.telemetry)
    }

    @Test
    fun testCurrentTelemetryAccepted() = runTest {
        val deviceId = "device_123"
        AppState.cameraStartStates[deviceId] = AppState.CameraStartState(
            activeGeneration = 3L
        )

        // Receive telemetry with generation 3 (current)
        val currentTelemetry = CameraTelemetryPayload(
            requestedWidth = 1920, requestedHeight = 1080, requestedFps = 30,
            actualWidth = 1920, actualHeight = 1080, observedFps = 30.0, frameCount = 10,
            firstFrameTimestampNs = 0L, lastFrameTimestampNs = 0L,
            cameraGeneration = 3L
        )
        val cmd = CommandPayload(
            command = "camera_capture_telemetry",
            device_id = deviceId,
            params = Json.encodeToString(CameraTelemetryPayload.serializer(), currentTelemetry)
        )
        AppState.handleIncomingCommand(cmd)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(AppState.cameraStartStates[deviceId]?.telemetry)
        assertEquals(3L, AppState.cameraStartStates[deviceId]?.telemetry?.cameraGeneration)
    }
}
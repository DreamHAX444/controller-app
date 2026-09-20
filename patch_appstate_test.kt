    @Test
    fun testSwitchCameraCommandTransitions() = kotlinx.coroutines.test.runTest {
        val appState = AppState()
        // Simulate already capturing
        appState.updateCameraStartStatus("device1", "req1", CameraStartStatus.ACCEPTED, null)
        appState.requestSwitchCamera("device1", "1", 1920, 1080, 30)

        val status = appState.cameraStartStates.value["device1"]?.status
        org.junit.Assert.assertEquals(CameraStartStatus.SWITCHING, status)
    }

    fun requestSwitchCamera(
        deviceId: String,
        cameraId: String,
        width: Int,
        height: Int,
        fps: Int
    ) {
        val currentState = cameraStartStates[deviceId] ?: return
        if (currentState.status == CameraStartStatus.SWITCHING) return

        appScope.launch {
            // Local validation first
            val caps = cameraCapabilities[deviceId]
            val supported = caps != null && com.aistudio.missioncontrol.pxytwe.camera.isConfigurationSupported(
                caps, cameraId, width, height, fps
            )
            
            val reqId = java.util.UUID.randomUUID().toString()
            
            if (!supported) {
                withContext(Dispatchers.Main) {
                    cameraStartStates[deviceId] = currentState.copy(
                        status = CameraStartStatus.REJECTED,
                        requestId = reqId,
                        error = "TARGET_CONFIGURATION_NOT_SUPPORTED"
                    )
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                cameraStartStates[deviceId] = currentState.copy(
                    status = CameraStartStatus.SWITCHING,
                    requestId = reqId,
                    cameraId = cameraId,
                    width = width,
                    height = height,
                    fps = fps,
                    telemetry = null, // clear old telemetry!
                    error = null
                )
            }
            
            val cmdParams = kotlinx.serialization.json.Json.encodeToString(
                com.aistudio.missioncontrol.pxytwe.camera.StartCameraParams(
                    requestId = reqId,
                    cameraId = cameraId,
                    width = width,
                    height = height,
                    fps = fps
                )
            )
            
            try {
                supabase.postgrest["commands"].insert(
                    com.aistudio.missioncontrol.pxytwe.webrtc.signaling.ControllerCommand(
                        deviceId = deviceId,
                        command = "switch_camera",
                        params = cmdParams
                    )
                )
            } catch (e: Exception) {
                Log.e("AppState", "Failed to send switch_camera command", e)
                withContext(Dispatchers.Main) {
                    cameraStartStates[deviceId] = cameraStartStates[deviceId]?.copy(
                        status = CameraStartStatus.FAILED,
                        error = "Network failure"
                    ) ?: return@withContext
                }
            }
        }
    }

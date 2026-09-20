    enum class CameraStartStatus {
        IDLE, REQUESTING, ACCEPTED, REJECTED, FAILED, OPENING, CAPTURING, STOPPING, STOPPED, ERROR
    }

    data class CameraStartState(
        val status: CameraStartStatus = CameraStartStatus.IDLE,
        val requestId: String? = null,
        val cameraId: String? = null,
        val width: Int? = null,
        val height: Int? = null,
        val fps: Int? = null,
        val error: String? = null
    )

    val cameraStartStates = mutableStateMapOf<String, CameraStartState>()

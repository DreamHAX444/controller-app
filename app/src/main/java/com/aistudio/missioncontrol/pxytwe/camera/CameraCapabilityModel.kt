package com.aistudio.missioncontrol.pxytwe.camera

import kotlinx.serialization.Serializable

@Serializable
enum class CameraFacing {
    FRONT, BACK, EXTERNAL, UNKNOWN
}

@Serializable
enum class CameraLensType {
    PRIMARY, ULTRA_WIDE, TELEPHOTO, MACRO, LOGICAL, OTHER
}

enum class CameraQualityMode(val displayName: String, val description: String) {
    RAW("RAW / Quality", "Maximum resolution, highest compatible FPS"),
    BALANCED("Balanced", "Balanced image quality and motion"),
    NETWORK_SAVER("Network Saver", "Lower resolution and FPS, reduced bandwidth"),
    SMOOTH("Smooth", "Highest compatible FPS, prioritizes motion"),
    CUSTOM("Custom", "Manual camera, resolution, and FPS selection")
}

@Serializable
data class FpsRange(val min: Int, val max: Int) {
    override fun toString(): String {
        return if (min == max) "$max" else "$min-$max"
    }
}

@Serializable
data class CameraConfiguration(
    val width: Int,
    val height: Int,
    val supportedFpsRanges: List<FpsRange>
)

@Serializable
data class CameraDeviceInfo(
    val cameraId: String,
    val facing: CameraFacing,
    val lensType: CameraLensType,
    val displayName: String,
    val sensorOrientation: Int,
    val hardwareLevel: Int,
    val isLogicalMultiCamera: Boolean,
    val physicalCameraIds: Set<String>,
    val focalLengths: List<Float>,
    val configurations: List<CameraConfiguration>
)

@Serializable
data class CameraCapabilitiesResponse(
    val requestId: String?,
    val success: Boolean,
    val error: String?,
    val cameras: List<CameraDeviceInfo>? = null
)

@Serializable
data class StartCameraParams(
    val requestId: String,
    val cameraId: String,
    val width: Int,
    val height: Int,
    val fps: Int
)

@Serializable
data class StartCameraResponse(
    val requestId: String,
    val success: Boolean,
    val error: String? = null,
    val cameraId: String,
    val width: Int,
    val height: Int,
    val fps: Int
)

@Serializable
data class CameraCaptureConfiguration(
    val cameraId: String,
    val width: Int,
    val height: Int,
    val fps: Int
)

fun isConfigurationSupported(
    capabilities: List<CameraDeviceInfo>,
    cameraId: String,
    width: Int,
    height: Int,
    fps: Int
): Boolean {
    val camera = capabilities.find { it.cameraId == cameraId } ?: return false
    val config = camera.configurations.find { it.width == width && it.height == height } ?: return false
    // A target FPS is supported if there is a capability range whose max FPS matches it.
    // We treat the max of a range as the explicit target FPS.
    return config.supportedFpsRanges.any { it.max == fps }
}

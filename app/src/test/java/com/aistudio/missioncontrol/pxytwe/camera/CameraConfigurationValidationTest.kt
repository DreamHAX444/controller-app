package com.aistudio.missioncontrol.pxytwe.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraConfigurationValidationTest {

    private fun createCapabilities(): List<CameraDeviceInfo> {
        return listOf(
            CameraDeviceInfo(
                cameraId = "0",
                facing = CameraFacing.BACK,
                lensType = CameraLensType.PRIMARY,
                displayName = "Back Camera",
                sensorOrientation = 90,
                hardwareLevel = 1,
                isLogicalMultiCamera = false,
                physicalCameraIds = emptySet(),
                focalLengths = emptyList(),
                configurations = listOf(
                    CameraConfiguration(1920, 1080, listOf(FpsRange(30, 30), FpsRange(60, 60))),
                    CameraConfiguration(1280, 720, listOf(FpsRange(60, 60)))
                )
            )
        )
    }

    @Test
    fun testSupportedConfigurationIsAccepted() {
        val caps = createCapabilities()
        assertTrue(isConfigurationSupported(caps, "0", 1920, 1080, 30))
        assertTrue(isConfigurationSupported(caps, "0", 1920, 1080, 60))
    }

    @Test
    fun testUnsupportedFpsIsRejected() {
        val caps = createCapabilities()
        assertFalse(isConfigurationSupported(caps, "0", 1920, 1080, 120))
    }

    @Test
    fun testUnsupportedResolutionIsRejected() {
        val caps = createCapabilities()
        assertFalse(isConfigurationSupported(caps, "0", 3840, 2160, 30))
    }

    @Test
    fun testWrongCameraIdIsRejected() {
        val caps = createCapabilities()
        assertFalse(isConfigurationSupported(caps, "1", 1920, 1080, 30))
    }

    @Test
    fun testExactCombinationIsStrictlyValidated() {
        val caps = listOf(
            CameraDeviceInfo(
                cameraId = "0",
                facing = CameraFacing.BACK,
                lensType = CameraLensType.PRIMARY,
                displayName = "Back Camera",
                sensorOrientation = 90,
                hardwareLevel = 1,
                isLogicalMultiCamera = false,
                physicalCameraIds = emptySet(),
                focalLengths = emptyList(),
                configurations = listOf(
                    CameraConfiguration(1920, 1080, listOf(FpsRange(30, 30))),
                    CameraConfiguration(1280, 720, listOf(FpsRange(60, 60)))
                )
            )
        )
        // 60 exists for 720p, but NOT for 1080p
        assertFalse(isConfigurationSupported(caps, "0", 1920, 1080, 60))
    }
}

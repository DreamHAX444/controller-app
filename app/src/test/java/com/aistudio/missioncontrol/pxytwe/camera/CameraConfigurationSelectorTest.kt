package com.aistudio.missioncontrol.pxytwe.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraConfigurationSelectorTest {

    private fun createCamera(configs: List<CameraConfiguration>): CameraDeviceInfo {
        return CameraDeviceInfo(
            cameraId = "0",
            facing = CameraFacing.BACK,
            lensType = CameraLensType.PRIMARY,
            displayName = "Test Camera",
            sensorOrientation = 90,
            hardwareLevel = 1,
            isLogicalMultiCamera = false,
            physicalCameraIds = emptySet(),
            focalLengths = emptyList(),
            configurations = configs
        )
    }

    private fun config(w: Int, h: Int, vararg fps: Int): CameraConfiguration {
        return CameraConfiguration(
            width = w,
            height = h,
            supportedFpsRanges = fps.map { FpsRange(it, it) }
        )
    }

    @Test
    fun `RAW mode selects highest resolution then highest FPS`() {
        val camera = createCamera(listOf(
            config(1920, 1080, 60),
            config(3840, 2160, 30),
            config(1280, 720, 60)
        ))

        val result = CameraConfigurationSelector.select(camera, CameraQualityMode.RAW)

        assertEquals(3840, result?.width)
        assertEquals(2160, result?.height)
        assertEquals(30, result?.fps)
    }

    @Test
    fun `SMOOTH mode selects highest FPS then highest practical resolution`() {
        val camera = createCamera(listOf(
            config(3840, 2160, 30),
            config(1920, 1080, 60),
            config(1280, 720, 60)
        ))

        val result = CameraConfigurationSelector.select(camera, CameraQualityMode.SMOOTH)

        assertEquals(1920, result?.width)
        assertEquals(1080, result?.height)
        assertEquals(60, result?.fps)
    }

    @Test
    fun `NETWORK_SAVER mode selects lowest practical resolution and FPS`() {
        val camera = createCamera(listOf(
            config(1920, 1080, 30),
            config(1280, 720, 30),
            config(640, 480, 15)
        ))

        val result = CameraConfigurationSelector.select(camera, CameraQualityMode.NETWORK_SAVER)

        assertEquals(640, result?.width)
        assertEquals(480, result?.height)
        assertEquals(15, result?.fps)
    }

    @Test
    fun `SMOOTH mode resolution tie`() {
        val camera = createCamera(listOf(
            config(1920, 1080, 60),
            config(1280, 720, 60)
        ))

        val result = CameraConfigurationSelector.select(camera, CameraQualityMode.SMOOTH)

        assertEquals(1920, result?.width)
        assertEquals(1080, result?.height)
        assertEquals(60, result?.fps)
    }

    @Test
    fun `BALANCED mode test A`() {
        val camera = createCamera(listOf(
            config(3840, 2160, 30),
            config(1920, 1080, 60),
            config(1280, 720, 60)
        ))
        val result = CameraConfigurationSelector.select(camera, CameraQualityMode.BALANCED)
        // Expected: 1080p@60
        assertEquals(1920, result?.width)
        assertEquals(1080, result?.height)
        assertEquals(60, result?.fps)
    }

    @Test
    fun `BALANCED mode test B`() {
        val camera = createCamera(listOf(
            config(3840, 2160, 60),
            config(1920, 1080, 30),
            config(640, 480, 15)
        ))
        val result = CameraConfigurationSelector.select(camera, CameraQualityMode.BALANCED)
        // Expected: 1080p@30
        assertEquals(1920, result?.width)
        assertEquals(1080, result?.height)
        assertEquals(30, result?.fps)
    }

    @Test
    fun `BALANCED mode test C - deterministic`() {
        val camera = createCamera(listOf(
            config(1920, 1080, 30),
            config(1920, 1080, 60),
            config(1280, 720, 60)
        ))
        val result1 = CameraConfigurationSelector.select(camera, CameraQualityMode.BALANCED)
        val result2 = CameraConfigurationSelector.select(camera, CameraQualityMode.BALANCED)
        val result3 = CameraConfigurationSelector.select(camera, CameraQualityMode.BALANCED)
        
        assertEquals(result1, result2)
        assertEquals(result2, result3)
    }

    @Test
    fun `BALANCED mode test D - empty`() {
        val camera = createCamera(emptyList())
        val result = CameraConfigurationSelector.select(camera, CameraQualityMode.BALANCED)
        assertNull(result)
    }

    @Test
    fun `BALANCED mode test E - invalid`() {
        // Create a camera with configurations but no valid FPS ranges
        val camera = CameraDeviceInfo(
            cameraId = "0",
            facing = CameraFacing.BACK,
            lensType = CameraLensType.PRIMARY,
            displayName = "Test Camera",
            sensorOrientation = 90,
            hardwareLevel = 1,
            isLogicalMultiCamera = false,
            physicalCameraIds = emptySet(),
            focalLengths = emptyList(),
            configurations = listOf(
                CameraConfiguration(1920, 1080, emptyList())
            )
        )
        val result = CameraConfigurationSelector.select(camera, CameraQualityMode.BALANCED)
        assertNull(result)
    }

    @Test
    fun `CUSTOM mode returns null in automatic selector`() {
        val camera = createCamera(listOf(config(1920, 1080, 30)))
        val result = CameraConfigurationSelector.select(camera, CameraQualityMode.CUSTOM)
        assertNull(result)
    }
}

package com.aistudio.missioncontrol.pxytwe.camera

import kotlin.math.abs

object CameraConfigurationSelector {

    /**
     * Automatically selects the best camera capture configuration based on the requested quality mode.
     */
    fun select(
        camera: CameraDeviceInfo,
        mode: CameraQualityMode
    ): CameraCaptureConfiguration? {
        if (camera.configurations.isEmpty()) return null

        // Flatten all available configurations to explicit target capabilities
        val allOptions = camera.configurations.flatMap { config ->
            config.supportedFpsRanges.map { range ->
                CameraCaptureConfiguration(
                    cameraId = camera.cameraId,
                    width = config.width,
                    height = config.height,
                    fps = range.max
                )
            }
        }.distinct()

        if (allOptions.isEmpty()) return null

        return when (mode) {
            CameraQualityMode.RAW -> {
                // Priority 1: Highest available resolution
                // Priority 2: Highest stable/valid FPS for that resolution
                allOptions.maxWithOrNull(
                    compareBy<CameraCaptureConfiguration> { it.width * it.height }
                        .thenBy { it.fps }
                )
            }
            CameraQualityMode.SMOOTH -> {
                // Priority 1: Highest available FPS
                // Priority 2: Highest practical resolution at that FPS
                allOptions.maxWithOrNull(
                    compareBy<CameraCaptureConfiguration> { it.fps }
                        .thenBy { it.width * it.height }
                )
            }
            CameraQualityMode.NETWORK_SAVER -> {
                // Priority 1: Lowest practical resolution
                // Priority 2: Lowest practical FPS
                allOptions.minWithOrNull(
                    compareBy<CameraCaptureConfiguration> { it.width * it.height }
                        .thenBy { it.fps }
                )
            }
            CameraQualityMode.BALANCED -> {
                // Implement deterministic scoring model
                val maxArea = allOptions.maxOf { it.width * it.height }.toFloat()
                val maxFps = allOptions.maxOf { it.fps }.toFloat()
                val maxBandwidthCost = allOptions.maxOf { it.width * it.height * it.fps.toLong() }.toFloat()

                allOptions.maxWithOrNull(
                    compareBy<CameraCaptureConfiguration> { opt ->
                        val resRatio = if (maxArea > 0f) (opt.width * opt.height).toFloat() / maxArea else 0f
                        val fpsRatio = if (maxFps > 0f) opt.fps.toFloat() / maxFps else 0f
                        val bwRatio = if (maxBandwidthCost > 0f) (opt.width * opt.height * opt.fps.toLong()).toFloat() / maxBandwidthCost else 0f
                        
                        // To genuinely favor mid-tier options like 1080p over 4K without altering the required final weights:
                        // 1. Boost mid-tier resolutions using sqrt (gives 1080p a fighting chance against 4K's massive area).
                        // 2. Punish bandwidth sharply only at the absolute capability ceiling using a cubic curve.
                        val resolutionScore = kotlin.math.sqrt(resRatio.toDouble()).toFloat()
                        val fpsScore = fpsRatio
                        val bandwidthScore = Math.pow(bwRatio.toDouble(), 3.0).toFloat()
                        
                        (0.50f * resolutionScore) + (0.25f * fpsScore) - (0.50f * bandwidthScore)
                    }
                    .thenBy { it.width * it.height }
                    .thenBy { it.fps }
                    .thenBy { it.width }
                    .thenBy { it.height }
                )
            }
            CameraQualityMode.CUSTOM -> {
                // Custom mode uses explicit UI dropdowns and is not automatically resolved here.
                null
            }
        }
    }
}

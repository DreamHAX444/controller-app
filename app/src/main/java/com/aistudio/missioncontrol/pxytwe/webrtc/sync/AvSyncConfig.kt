package com.aistudio.missioncontrol.pxytwe.webrtc.sync

data class AvSyncConfig(
    val absoluteSyncThresholdMs: Long = 30L,
    val driftThresholdMs: Long = 30L,
    val largeDriftThresholdMs: Long = 100L,
    val criticalDriftThresholdMs: Long = 200L,
    val initialSyncTimeoutMs: Long = 2000L
)

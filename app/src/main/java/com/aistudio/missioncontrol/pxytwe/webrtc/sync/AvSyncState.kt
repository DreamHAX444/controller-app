package com.aistudio.missioncontrol.pxytwe.webrtc.sync

enum class AvSyncState {
    IDLE,
    BUFFERING,
    SYNC_ESTIMATED,
    SYNCED,
    DRIFTING,
    CORRECTING,
    STARVED,
    LIMITED
}

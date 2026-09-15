package com.aistudio.missioncontrol.pxytwe.webrtc.sync

class AvSyncStats {
    var currentState = AvSyncState.IDLE
    var currentOffsetMs = 0L
    var estimatedOffsetMs = 0L
    var offsetMinMs = Long.MAX_VALUE
    var offsetMaxMs = Long.MIN_VALUE
    var sampleCount = 0L
    var correctionsApplied = 0L
    var droppedAudioFrames = 0L
    var startupBufferingDurationMs = 0L
    var starvationEvents = 0L
}

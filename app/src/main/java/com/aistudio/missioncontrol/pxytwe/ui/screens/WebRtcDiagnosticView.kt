package com.aistudio.missioncontrol.pxytwe.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.aistudio.missioncontrol.pxytwe.webrtc.WebRtcPeerConnectionFactory
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

private const val SINK_TAG_KEY = 0x7F0A0001 // Consistent tag key for tracking attached VideoTrack

@Composable
fun WebRtcDiagnosticView(
    videoTrack: VideoTrack?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                SurfaceViewRenderer(context).apply {
                    val sharedContext = WebRtcPeerConnectionFactory.eglBase.eglBaseContext
                    init(sharedContext, object : RendererCommon.RendererEvents {
                        override fun onFirstFrameRendered() {
                            Log.i("WebRtcDiagnosticView", "First video frame rendered on screen!")
                        }

                        override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {
                            Log.i("WebRtcDiagnosticView", "Video resolution changed: ${videoWidth}x${videoHeight}, rot=$rotation")
                        }
                    })
                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                    setEnableHardwareScaler(true)
                    setZOrderMediaOverlay(true)
                }
            },
            update = { renderer ->
                val previousTrack = renderer.getTag(SINK_TAG_KEY) as? VideoTrack
                if (previousTrack !== videoTrack) {
                    if (previousTrack != null) {
                        Log.i("WebRtcDiagnosticView", "Detaching old VideoTrack: $previousTrack")
                        try {
                            previousTrack.removeSink(renderer)
                        } catch (e: Exception) {
                            Log.w("WebRtcDiagnosticView", "Error removing old sink: ${e.message}")
                        }
                    }
                    if (videoTrack != null) {
                        Log.i("WebRtcDiagnosticView", "Attaching new VideoTrack to SurfaceViewRenderer: $videoTrack")
                        try {
                            videoTrack.addSink(renderer)
                        } catch (e: Exception) {
                            Log.e("WebRtcDiagnosticView", "Error adding sink: ${e.message}", e)
                        }
                    }
                    renderer.setTag(SINK_TAG_KEY, videoTrack)
                }
            },
            onRelease = { renderer ->
                val attachedTrack = renderer.getTag(SINK_TAG_KEY) as? VideoTrack
                if (attachedTrack != null) {
                    Log.i("WebRtcDiagnosticView", "Releasing SurfaceViewRenderer, detaching track: $attachedTrack")
                    try {
                        attachedTrack.removeSink(renderer)
                    } catch (e: Exception) {
                        Log.w("WebRtcDiagnosticView", "Error removing sink onRelease: ${e.message}")
                    }
                    renderer.setTag(SINK_TAG_KEY, null)
                }
                try {
                    renderer.release()
                } catch (e: Exception) {
                    Log.w("WebRtcDiagnosticView", "Error releasing SurfaceViewRenderer: ${e.message}")
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

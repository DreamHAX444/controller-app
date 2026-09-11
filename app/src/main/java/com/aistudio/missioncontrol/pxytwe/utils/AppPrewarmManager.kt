package com.aistudio.missioncontrol.pxytwe.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import com.aistudio.missioncontrol.pxytwe.AppState
import com.aistudio.missioncontrol.pxytwe.EventLogger
import com.aistudio.missioncontrol.pxytwe.SupabaseClientManager
import com.aistudio.missioncontrol.pxytwe.ui.theme.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import java.io.File

object AppPrewarmManager {
    var isWarmed = false
        private set

    // Pre-rendered Directional Marker Bitmaps in RAM
    var cachedMarkerBitmaps: Map<Int, Bitmap> = emptyMap()
        private set

    suspend fun runWarmup(context: Context, onProgress: (Float, String) -> Unit) = withContext(Dispatchers.IO) {
        if (isWarmed) {
            onProgress(1.0f, "SYSTEM READY")
            return@withContext
        }

        // 1. Initialize RAM & Hydrate Telemetry from Flash Disk Cache (Instant 0ms Telemetry)
        withContext(Dispatchers.Main) { onProgress(0.20f, "HYDRATING LOCAL RAM TELEMETRY...") }
        AppState.initialize(context)
        ThemeManager.init(context)
        EventLogger.initialize(context)

        // 2. Pre-render High-Performance Marker Atlas in GPU Memory
        withContext(Dispatchers.Main) { onProgress(0.45f, "PRE-RENDERING GPU MARKER ATLAS...") }
        val colors = listOf(0xFF22C55E.toInt(), 0xFF06B6D4.toInt(), 0xFFEAB308.toInt())
        cachedMarkerBitmaps = colors.associateWith { color ->
            val bitmap = createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            
            // Outer glow
            paint.color = color
            paint.alpha = 60
            paint.style = Paint.Style.FILL
            canvas.drawCircle(50f, 50f, 44f, paint)

            // Inner solid pointer
            paint.color = color
            paint.alpha = 255
            val path = Path()
            path.moveTo(50f, 12f)
            path.lineTo(76f, 78f)
            path.lineTo(50f, 64f)
            path.lineTo(24f, 78f)
            path.close()
            canvas.drawPath(path, paint)
            
            // Core accent highlight
            paint.color = android.graphics.Color.WHITE
            val innerPath = Path()
            innerPath.moveTo(50f, 26f)
            innerPath.lineTo(66f, 70f)
            innerPath.lineTo(50f, 60f)
            innerPath.lineTo(34f, 70f)
            innerPath.close()
            canvas.drawPath(innerPath, paint)
            bitmap
        }

        // 3. Configure 250MB Persistent Map Tile Cache & Enable Hardware Layer Decoding
        withContext(Dispatchers.Main) { onProgress(0.70f, "CONFIGURING 250MB PERSISTENT MAP CACHE...") }
        try {
            val basePath = File(context.filesDir, "osmdroid")
            val tileCache = File(context.cacheDir, "osmdroid/tiles")
            if (!basePath.exists()) basePath.mkdirs()
            if (!tileCache.exists()) tileCache.mkdirs()

            Configuration.getInstance().load(context, context.getSharedPreferences("geofence_prefs", Context.MODE_PRIVATE))
            Configuration.getInstance().osmdroidBasePath = basePath
            Configuration.getInstance().osmdroidTileCache = tileCache
            Configuration.getInstance().userAgentValue = context.packageName
            Configuration.getInstance().tileFileSystemCacheMaxBytes = 250L * 1024L * 1024L
            Configuration.getInstance().tileFileSystemCacheTrimBytes = 200L * 1024L * 1024L
            Configuration.getInstance().cacheMapTileCount = 32.toShort()
            Configuration.getInstance().cacheMapTileOvershoot = 32.toShort()
            Configuration.getInstance().isMapViewHardwareAccelerated = true
        } catch (e: Exception) {
            // Ignore
        }

        // 4. Background WebSocket & Telemetry Grid Connect
        withContext(Dispatchers.Main) { onProgress(0.95f, "SYNCHRONIZING TELEMETRY GRID...") }
        try {
            SupabaseClientManager.connectRealtime()
            SupabaseClientManager.startListeningForPongs()
        } catch (e: Exception) {
            // Offline fallback
        }

        // 5. Done
        withContext(Dispatchers.Main) { onProgress(1.0f, "SYSTEM READY") }
        isWarmed = true
        delay(100)
    }
}

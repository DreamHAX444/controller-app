package com.aistudio.missioncontrol.pxytwe.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aistudio.missioncontrol.pxytwe.AppState
import com.aistudio.missioncontrol.pxytwe.DeviceTelemetry
import com.aistudio.missioncontrol.pxytwe.SupabaseClientManager
import com.aistudio.missioncontrol.pxytwe.ui.components.LocationOffBadge
import com.aistudio.missioncontrol.pxytwe.ui.theme.*
import com.aistudio.missioncontrol.pxytwe.utils.GeofenceData
import com.aistudio.missioncontrol.pxytwe.utils.AppPrewarmManager
import com.aistudio.missioncontrol.pxytwe.utils.GeofenceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

fun getCardinalDirection(degrees: Float): String {
    val normalized = ((degrees % 360) + 360) % 360
    return when {
        normalized >= 337.5 || normalized < 22.5 -> "N"
        normalized < 67.5 -> "NE"
        normalized < 112.5 -> "E"
        normalized < 157.5 -> "SE"
        normalized < 202.5 -> "S"
        normalized < 247.5 -> "SW"
        normalized < 292.5 -> "W"
        else -> "NW"
    }
}

@Composable
fun DeviceTelemetryDetailsDialog(
    telemetry: DeviceTelemetry,
    onDismiss: () -> Unit
) {
    val diff = System.currentTimeMillis() - telemetry.lastSeen
    val isLive = diff < 15000
    val lastSeenText = when {
        diff < 60000 -> "${diff / 1000}s ago"
        diff < 3600000 -> "${diff / 60000}m ago"
        else -> "${diff / 3600000}h ago"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Insights, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${telemetry.name.uppercase()} SPECS",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SpecRow("DEVICE ID", telemetry.name)
                SpecRow("STATUS", if (isLive) "Active (Live • $lastSeenText)" else "Offline ($lastSeenText)")
                SpecRow("COORDINATES", "%.5f, %.5f".format(telemetry.lat, telemetry.lon))
                SpecRow("ALTITUDE", "%.1f m MSL".format(telemetry.altitude))
                SpecRow("ACCURACY", "±%.1f m".format(telemetry.accuracy))
                SpecRow("HEADING", "%.1f° %s".format(telemetry.heading, getCardinalDirection(telemetry.heading)))
                SpecRow("TILT (PITCH/ROLL)", "%.1f° / %.1f°".format(telemetry.pitch, telemetry.roll))
                if (telemetry.pressure > 0f) {
                    SpecRow("PRESSURE", "%.1f hPa".format(telemetry.pressure))
                }
                SpecRow("NETWORK", "%d dBm • %s".format(telemetry.signal, telemetry.networkType))
                SpecRow("BATTERY", "%d%%%s".format(telemetry.battery, if (telemetry.charging) " (Charging)" else ""))
                SpecRow("PING / LATENCY", if (telemetry.ping >= 0) "${telemetry.ping} ms" else "Active")
                SpecRow("UPDATE COUNT", "${telemetry.updateCount} pings")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CLOSE", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun FleetScreen(
    onNavigateToMicMonitor: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sharedPrefs = remember { context.getSharedPreferences("geofence_prefs", android.content.Context.MODE_PRIVATE) }
    
    var isDropdownExpanded by remember { mutableStateOf(false) }
    val selectedDevice = AppState.selectedDevice.value ?: "All Devices"
    
    fun updateSelectedDevice(name: String) {
        AppState.selectedDevice.value = if (name == "All Devices") null else name
    }

    val startPoint = remember { GeoPoint(37.7749, -122.4194) }
    val isDrawingGeofence = AppState.isDrawingGeofence
    val isDebugMode = AppState.isDebugDeviceMode
    val currentGeofencePoints = remember { mutableStateListOf<GeoPoint>() }
    val savedGeofences = remember { 
        val data = sharedPrefs.getString("saved_fences", "") ?: ""
        mutableStateListOf(*GeofenceUtils.deserializeGeofences(data).toTypedArray()) 
    }
    val currentGeofenceColor = remember { mutableIntStateOf("#D4AF37".toColorInt()) }
    val currentGeofenceName = remember { mutableStateOf("") }
    val originalGeofencePoints = remember { mutableStateOf<List<GeoPoint>?>(null) }
    val originalGeofenceName = remember { mutableStateOf("") }
    val showDeleteConfirmation = remember { mutableStateOf(false) }
    val showShutdownConfirmation = remember { mutableStateOf(false) }
    val showDeviceDetailsDialog = remember { mutableStateOf(false) }
    
    data class MapDevice(val name: String, val point: GeoPoint, val color: Int, val heading: Float)

    val activeMap = AppState.activeDevices
    
    val currentTime = System.currentTimeMillis()
    val isAwake by remember(selectedDevice, activeMap.values.toList()) {
        derivedStateOf {
            val now = System.currentTimeMillis()
            if (selectedDevice == "All Devices") {
                activeMap.values.any { now - it.lastSeen < 15000 }
            } else {
                val dev = activeMap[selectedDevice]
                dev != null && (now - dev.lastSeen < 15000)
            }
        }
    }

    val activeDeviceItems = activeMap.values.toList()
    val devicesList = remember(activeDeviceItems) {
        activeDeviceItems.mapIndexed { idx, dev ->
            val color = when(idx % 3) {
                0 -> MapMarkerGreen.toArgb()
                1 -> MapMarkerCyan.toArgb()
                else -> MapMarkerYellow.toArgb()
            }
            MapDevice(dev.name, GeoPoint(dev.lat, dev.lon), color, dev.heading)
        }
    }

    val resources = context.resources
    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            setDestroyMode(false)
            setTilesScaledToDpi(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(15.0)
            controller.setCenter(startPoint)
            setTileSource(TileSourceFactory.MAPNIK)
            
            val inverseMatrix = android.graphics.ColorMatrix(floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            ))
            val destinationColors = android.graphics.ColorMatrix().apply { setSaturation(0f) }
            destinationColors.postConcat(inverseMatrix)
            overlayManager.tilesOverlay.setColorFilter(android.graphics.ColorMatrixColorFilter(destinationColors))
        }
    }

    // Lifecycle observer
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    // Overlays
    val savedFencesOverlay = remember { FolderOverlay() }
    val drawingOverlay = remember { FolderOverlay() }
    val historyOverlay = remember { FolderOverlay() }
    val devicesOverlay = remember { FolderOverlay() }
    
    // Pre-rendered Directional Marker Bitmaps (Loaded from AppPrewarmManager)
    val markerBitmapsByColor = remember {
        if (AppPrewarmManager.cachedMarkerBitmaps.isNotEmpty()) {
            AppPrewarmManager.cachedMarkerBitmaps
        } else {
            val colors = listOf(MapMarkerGreen.toArgb(), MapMarkerCyan.toArgb(), MapMarkerYellow.toArgb())
            colors.associateWith { color ->
                val bitmap = createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                paint.color = color
                paint.alpha = 60
                paint.style = android.graphics.Paint.Style.FILL
                canvas.drawCircle(50f, 50f, 44f, paint)
                paint.color = color
                paint.alpha = 255
                val path = android.graphics.Path()
                path.moveTo(50f, 12f)
                path.lineTo(76f, 78f)
                path.lineTo(50f, 64f)
                path.lineTo(24f, 78f)
                path.close()
                canvas.drawPath(path, paint)
                paint.color = android.graphics.Color.WHITE
                val innerPath = android.graphics.Path()
                innerPath.moveTo(50f, 26f)
                innerPath.lineTo(66f, 70f)
                innerPath.lineTo(50f, 60f)
                innerPath.lineTo(34f, 70f)
                innerPath.close()
                canvas.drawPath(innerPath, paint)
                bitmap
            }
        }
    }
    val cachedMarkers = remember { mutableMapOf<String, Marker>() }
    val cachedPolylines = remember { mutableMapOf<String, Polyline>() }

    val drawingPointBitmap = remember(currentGeofenceColor.intValue) {
        val color = currentGeofenceColor.intValue
        val bitmap = createBitmap(36, 36, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.color = color
        paint.alpha = 60
        canvas.drawCircle(18f, 18f, 18f, paint)
        paint.alpha = 255
        canvas.drawCircle(18f, 18f, 8f, paint)
        bitmap
    }

    val createTextBitmap: (String, Int) -> android.graphics.Bitmap = { text, color ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.textSize = 28f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        paint.color = color
        
        val textBounds = android.graphics.Rect()
        paint.getTextBounds(text, 0, text.length, textBounds)
        
        val paddingX = 20
        val paddingY = 12
        val width = textBounds.width() + paddingX * 2
        val height = textBounds.height() + paddingY * 2
        
        val bitmap = createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        bgPaint.color = android.graphics.Color.argb(220, 20, 20, 20)
        val rect = android.graphics.RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, height / 2f, height / 2f, bgPaint)
        
        val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        borderPaint.style = android.graphics.Paint.Style.STROKE
        borderPaint.strokeWidth = 3f
        borderPaint.color = color
        canvas.drawRoundRect(rect, height / 2f, height / 2f, borderPaint)
        
        canvas.drawText(text, paddingX.toFloat() - textBounds.left, paddingY.toFloat() - textBounds.top, paint)
        bitmap
    }

    val geofencePointsList = remember(currentGeofencePoints.size, currentGeofencePoints.toList()) { currentGeofencePoints.toList() }
    val savedFencesList = remember(savedGeofences.size, savedGeofences.toList()) { savedGeofences.toList() }

    val geofenceLabelBitmaps = remember(savedFencesList) {
        val cache = mutableMapOf<String, android.graphics.Bitmap>()
        savedFencesList.forEach { fence ->
            cache[fence.name] = createTextBitmap(fence.name, fence.colorArgb)
        }
        cache
    }

    fun frameMapSelection(targetDevice: String) {
        mapView.post {
            if (targetDevice == "All Devices") {
                if (devicesList.isEmpty()) {
                    mapView.controller.animateTo(startPoint, 15.0, 800L)
                } else if (devicesList.size == 1) {
                    mapView.controller.animateTo(devicesList.first().point, 17.0, 800L)
                } else {
                    val points = devicesList.map { it.point }
                    val boundingBox = BoundingBox.fromGeoPoints(points)
                    if (boundingBox.latNorth == boundingBox.latSouth && boundingBox.lonEast == boundingBox.lonWest) {
                        mapView.controller.animateTo(points.first(), 17.0, 800L)
                    } else {
                        val latDiff = boundingBox.latNorth - boundingBox.latSouth
                        val lonDiff = boundingBox.lonEast - boundingBox.lonWest
                        val paddingFactor = 0.25
                        val paddedBox = BoundingBox(
                            boundingBox.latNorth + latDiff * paddingFactor,
                            boundingBox.lonEast + lonDiff * paddingFactor,
                            boundingBox.latSouth - latDiff * paddingFactor,
                            boundingBox.lonWest - lonDiff * paddingFactor
                        )
                        mapView.zoomToBoundingBox(paddedBox, true, 80)
                    }
                }
            } else {
                val device = devicesList.find { it.name == targetDevice }
                if (device != null) {
                    mapView.controller.animateTo(device.point, 17.0, 800L)
                }
            }
        }
    }

    var hasInitialCentered by remember { mutableStateOf(false) }
    LaunchedEffect(devicesList.isNotEmpty()) {
        if (devicesList.isNotEmpty() && !hasInitialCentered) {
            frameMapSelection(selectedDevice)
            hasInitialCentered = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Map Container
        AndroidView(
            factory = {
                mapView.apply {
                    overlays.add(savedFencesOverlay)
                    overlays.add(historyOverlay)
                    overlays.add(drawingOverlay)
                    overlays.add(devicesOverlay)
                    
                    val interactionOverlay = object : org.osmdroid.views.overlay.Overlay() {
                        var draggedIndex = -1
                        
                        override fun onTouchEvent(event: android.view.MotionEvent, mapView: MapView): Boolean {
                            if (!isDrawingGeofence.value) return super.onTouchEvent(event, mapView)
                            
                            val proj = mapView.projection
                            val tGeo = proj.fromPixels(event.x.toInt(), event.y.toInt()) as GeoPoint
                            
                            when (event.action) {
                                android.view.MotionEvent.ACTION_DOWN -> {
                                    var closestIndex = -1
                                    var minDistance = Float.MAX_VALUE
                                    val markers = drawingOverlay.items.filterIsInstance<Marker>()
                                    for (i in markers.indices) {
                                        val p = proj.toPixels(markers[i].position, null)
                                        val dx = event.x - p.x
                                        val dy = event.y - p.y
                                        val dist = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                                        if (dist < 100f && dist < minDistance) {
                                            minDistance = dist
                                            closestIndex = i
                                        }
                                    }
                                    if (closestIndex != -1) {
                                        draggedIndex = closestIndex
                                        mapView.parent?.requestDisallowInterceptTouchEvent(true)
                                        return true
                                    }
                                }
                                android.view.MotionEvent.ACTION_MOVE -> {
                                    if (draggedIndex != -1) {
                                        val markers = drawingOverlay.items.filterIsInstance<Marker>()
                                        if (draggedIndex < markers.size) {
                                            markers[draggedIndex].position = tGeo
                                            val newPoints = markers.map { it.position }
                                            drawingOverlay.items.filterIsInstance<Polygon>().firstOrNull()?.points = newPoints
                                            drawingOverlay.items.filterIsInstance<Polyline>().firstOrNull()?.setPoints(newPoints)
                                            mapView.invalidate()
                                        }
                                        return true
                                    }
                                }
                                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                                    if (draggedIndex != -1) {
                                        val markers = drawingOverlay.items.filterIsInstance<Marker>()
                                        if (draggedIndex < markers.size) {
                                            val newPos = markers[draggedIndex].position
                                            currentGeofencePoints[draggedIndex] = GeoPoint(newPos.latitude, newPos.longitude)
                                        }
                                        draggedIndex = -1
                                        mapView.parent?.requestDisallowInterceptTouchEvent(false)
                                        return true
                                    }
                                }
                            }
                            return super.onTouchEvent(event, mapView)
                        }
                    }
                    overlays.add(interactionOverlay)
                    
                    val mapEventsReceiver = object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            if (p == null) return false
                            if (isDebugMode.value) {
                                AppState.injectDebugLocation(p.latitude, p.longitude)
                                return true
                            }
                            if (isDrawingGeofence.value) {
                                currentGeofencePoints.add(p)
                                return true
                            }
                            return false
                        }
                        override fun longPressHelper(p: GeoPoint?): Boolean {
                            if (p == null) return false
                            if (!isDrawingGeofence.value) {
                                val clickedFence = savedGeofences.find { fence ->
                                    GeofenceUtils.isPointInPolygon(p, fence.points)
                                }
                                if (clickedFence != null) {
                                    originalGeofencePoints.value = clickedFence.points.toList()
                                    currentGeofenceColor.intValue = clickedFence.colorArgb
                                    currentGeofenceName.value = clickedFence.name
                                    originalGeofenceName.value = clickedFence.name
                                    savedGeofences.remove(clickedFence)
                                    sharedPrefs.edit { putString("saved_fences", GeofenceUtils.serializeGeofences(savedGeofences.toList())) }
                                    currentGeofencePoints.clear()
                                    currentGeofencePoints.addAll(clickedFence.points)
                                    isDrawingGeofence.value = true
                                    return true
                                }
                            }
                            return false
                        }
                    }
                    overlays.add(MapEventsOverlay(mapEventsReceiver))
                }
            },
            update = {},
            modifier = Modifier.fillMaxSize()
        )

        // Saved Fences sync
        LaunchedEffect(savedFencesList, isDrawingGeofence.value) {
            savedFencesOverlay.items.clear()
            savedFencesList.forEach { fenceData ->
                val polygon = Polygon()
                polygon.points = fenceData.points
                polygon.fillPaint.color = ColorUtils.setAlphaComponent(fenceData.colorArgb, 35)
                polygon.outlinePaint.color = fenceData.colorArgb
                polygon.outlinePaint.strokeWidth = 5f
                polygon.outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(24f, 12f), 0f)
                polygon.outlinePaint.setShadowLayer(12f, 0f, 0f, fenceData.colorArgb)
                savedFencesOverlay.add(polygon)

                if (!isDrawingGeofence.value) {
                    val centroid = GeofenceUtils.getCentroid(fenceData.points)
                    val labelMarker = Marker(mapView)
                    labelMarker.position = centroid
                    labelMarker.title = fenceData.name
                    labelMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    labelMarker.icon = geofenceLabelBitmaps[fenceData.name]?.toDrawable(resources)
                    labelMarker.setOnMarkerClickListener { _, _ -> true }
                    savedFencesOverlay.add(labelMarker)
                }
            }
            mapView.invalidate()
        }

        // High-Performance Devices & Breadcrumbs Sync (Zero Allocation)
        LaunchedEffect(devicesList, selectedDevice) {
            val visibleDeviceNames = if (selectedDevice == "All Devices") {
                devicesList.map { it.name }.toSet()
            } else {
                setOf(selectedDevice)
            }

            // Sync History Polylines without reconstructing objects
            val activeDevices = activeMap.values.toList()
            val currentHistoryKeys = mutableSetOf<String>()
            activeDevices.forEach { dev ->
                if (visibleDeviceNames.contains(dev.name) && dev.history.size >= 2) {
                    currentHistoryKeys.add(dev.name)
                    val points = dev.history.map { GeoPoint(it.first, it.second) }
                    val polyline = cachedPolylines.getOrPut(dev.name) {
                        Polyline().apply {
                            outlinePaint.color = ColorUtils.setAlphaComponent(MapMarkerCyan.toArgb(), 120)
                            outlinePaint.strokeWidth = 4f
                            historyOverlay.add(this)
                        }
                    }
                    polyline.setPoints(points)
                }
            }
            // Remove inactive polylines
            val polylineIterator = cachedPolylines.entries.iterator()
            while (polylineIterator.hasNext()) {
                val entry = polylineIterator.next()
                if (!currentHistoryKeys.contains(entry.key)) {
                    historyOverlay.remove(entry.value)
                    polylineIterator.remove()
                }
            }

            // Sync Device Markers (Mutate in-place to avoid GC lag)
            val currentMarkerKeys = mutableSetOf<String>()
            devicesList.forEach { device ->
                if (visibleDeviceNames.contains(device.name)) {
                    currentMarkerKeys.add(device.name)
                    val marker = cachedMarkers.getOrPut(device.name) {
                        Marker(mapView).apply {
                            title = device.name
                            icon = markerBitmapsByColor[device.color]?.toDrawable(resources)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            setOnMarkerClickListener { _, _ ->
                                updateSelectedDevice(device.name)
                                mapView.controller.animateTo(this.position)
                                true
                            }
                            devicesOverlay.add(this)
                        }
                    }
                    if (marker.position.latitude != device.point.latitude || marker.position.longitude != device.point.longitude) {
                        marker.position = device.point
                    }
                    if (marker.rotation != -device.heading) {
                        marker.rotation = -device.heading
                    }
                }
            }
            // Remove inactive markers
            val markerIterator = cachedMarkers.entries.iterator()
            while (markerIterator.hasNext()) {
                val entry = markerIterator.next()
                if (!currentMarkerKeys.contains(entry.key)) {
                    devicesOverlay.remove(entry.value)
                    markerIterator.remove()
                }
            }

            mapView.invalidate()
        }

        // Drawing Geofence sync
        LaunchedEffect(geofencePointsList, currentGeofenceColor.intValue, isDrawingGeofence.value) {
            drawingOverlay.items.clear()
            if (isDrawingGeofence.value && geofencePointsList.isNotEmpty()) {
                val activeColor = currentGeofenceColor.intValue
                if (geofencePointsList.size >= 3) {
                    val polygon = Polygon()
                    polygon.points = geofencePointsList
                    polygon.fillPaint.color = ColorUtils.setAlphaComponent(activeColor, 75)
                    polygon.outlinePaint.color = activeColor
                    polygon.outlinePaint.strokeWidth = 5f
                    drawingOverlay.add(polygon)
                } else if (geofencePointsList.size == 2) {
                    val polyline = Polyline()
                    polyline.setPoints(geofencePointsList)
                    polyline.outlinePaint.color = activeColor
                    polyline.outlinePaint.strokeWidth = 5f
                    drawingOverlay.add(polyline)
                }

                geofencePointsList.forEach { pt ->
                    val marker = Marker(mapView)
                    marker.position = pt
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    marker.icon = drawingPointBitmap.toDrawable(resources)
                    marker.isDraggable = false
                    drawingOverlay.add(marker)
                }
            }
            mapView.invalidate()
        }

        // Subtle gradient vignette overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to MaterialTheme.colorScheme.background.copy(alpha = 0.45f),
                        0.15f to Color.Transparent,
                        0.70f to Color.Transparent,
                        1.0f to MaterialTheme.colorScheme.background.copy(alpha = 0.88f)
                    )
                )
        )

        // ═════════════════════════════════════════════════════════════════════
        // 1. TOP FLOATING DYNAMIC ISLAND (Fleet Selector & Connection HUD)
        // ═════════════════════════════════════════════════════════════════════
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                .align(Alignment.TopCenter),
            contentAlignment = Alignment.TopCenter
        ) {
            AnimatedVisibility(
                visible = !isDrawingGeofence.value,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val activeDeviceTelemetry = if (selectedDevice != "All Devices") activeMap[selectedDevice] else null
                    val activeDiff = if (activeDeviceTelemetry != null) currentTime - activeDeviceTelemetry.lastSeen else 0L
                    val activeLive = activeDeviceTelemetry != null && activeDiff < 15000
                    val activeSeenText = when {
                        activeDiff < 60000 -> "${activeDiff / 1000}s ago"
                        activeDiff < 3600000 -> "${activeDiff / 60000}m ago"
                        else -> "${activeDiff / 3600000}h ago"
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                        shadowElevation = 4.dp,
                        onClick = { isDropdownExpanded = !isDropdownExpanded },
                        modifier = Modifier.widthIn(max = 340.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Pulsing live dot
                            val transition = rememberInfiniteTransition(label = "pulse_top")
                            val pulseAlpha by transition.animateFloat(
                                initialValue = 0.4f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(animation = tween(1000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
                                label = "pulse_top_alpha"
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .graphicsLayer { alpha = if (isAwake) pulseAlpha else 0.4f }
                                    .clip(CircleShape)
                                    .background(if (isAwake) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Text(
                                text = if (selectedDevice == "All Devices") "ALL TRACKERS (${devicesList.size})" else selectedDevice.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            if (activeDeviceTelemetry != null) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "• $activeSeenText",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (activeLive) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    softWrap = false
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(6.dp))
                            
                            val rotation by animateFloatAsState(targetValue = if (isDropdownExpanded) 180f else 0f, label = "dropdown_icon")
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = rotation },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Dropdown Menu Sheet
                    AnimatedVisibility(
                        visible = isDropdownExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .widthIn(max = 320.dp)
                                .padding(top = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(6.dp)) {
                                val allOptions = listOf("All Devices") + devicesList.map { it.name }
                                allOptions.forEach { name ->
                                    val isSelected = selectedDevice == name
                                    val devTelemetry = activeMap[name]
                                    val devDiff = if (devTelemetry != null) currentTime - devTelemetry.lastSeen else 0L
                                    val devLive = if (name == "All Devices") {
                                        activeMap.values.any { currentTime - it.lastSeen < 15000 }
                                    } else {
                                        devTelemetry != null && devDiff < 15000
                                    }
                                    val seenStr = when {
                                        devDiff < 60000 -> "${devDiff / 1000}s ago"
                                        devDiff < 3600000 -> "${devDiff / 60000}m ago"
                                        else -> "${devDiff / 3600000}h ago"
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                updateSelectedDevice(name)
                                                isDropdownExpanded = false
                                                frameMapSelection(name)
                                            }
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(if (devLive) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = name.uppercase(),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                softWrap = false
                                            )
                                            if (devTelemetry != null) {
                                                Text(
                                                    text = if (devLive) "Live • $seenStr" else "Offline • $seenStr",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (devLive) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                        if (devTelemetry != null && devTelemetry.battery > 0) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "${devTelemetry.battery}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontFamily = FontFamily.Monospace,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ═════════════════════════════════════════════════════════════════════
        // 2. RIGHT MICRO ACTION DOCK (Floating Tool Capsule)
        // ═════════════════════════════════════════════════════════════════════
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(y = (-30).dp)
                .padding(end = 16.dp)
        ) {
            AnimatedVisibility(
                visible = !isDrawingGeofence.value,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    shadowElevation = 6.dp
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        // Focus / Recenter Button
                        IconButton(
                            onClick = { frameMapSelection(selectedDevice) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.MyLocation,
                                contentDescription = "Center Map",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.width(20.dp).padding(vertical = 2.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )

                        // Geofence Mode Button
                        IconButton(
                            onClick = { 
                                isDrawingGeofence.value = true
                                originalGeofencePoints.value = null
                                currentGeofencePoints.clear()
                                currentGeofenceName.value = ""
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.AddLocationAlt,
                                contentDescription = "Draw Zone",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.width(20.dp).padding(vertical = 2.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )

                        // Sleep / Wake Tracker Button
                        val anyDeviceActive = if (selectedDevice == "All Devices") {
                            activeMap.values.any { currentTime - it.lastSeen < 60000 }
                        } else {
                            val lastSeen = activeMap[selectedDevice]?.lastSeen ?: 0L
                            currentTime - lastSeen < 60000
                        }
                        IconButton(
                            onClick = { showShutdownConfirmation.value = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (anyDeviceActive) Icons.Default.PowerSettingsNew else Icons.Default.WbSunny,
                                contentDescription = if (anyDeviceActive) "Sleep Trackers" else "Wake Trackers",
                                tint = if (anyDeviceActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.width(20.dp).padding(vertical = 2.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )

                        // Debug Injector Toggle
                        IconButton(
                            onClick = { isDebugMode.value = !isDebugMode.value },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.BugReport,
                                contentDescription = "Debug Mode",
                                tint = if (isDebugMode.value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // ═════════════════════════════════════════════════════════════════════
        // 3. BOTTOM COMPACT BENTO TELEMETRY HUD (When Specific Device Selected)
        // ═════════════════════════════════════════════════════════════════════
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 82.dp)
        ) {
            AnimatedVisibility(
                visible = selectedDevice != "All Devices" && !isDrawingGeofence.value,
                enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
            ) {
                val currentTelemetry = activeMap[selectedDevice]
                val diff = currentTime - (currentTelemetry?.lastSeen ?: 0L)
                val isLive = currentTelemetry != null && diff < 15000
                val lastSeenText = when {
                    diff < 60000 -> "${diff / 1000}s ago"
                    diff < 3600000 -> "${diff / 60000}m ago"
                    else -> "${diff / 3600000}h ago"
                }
                val headingVal = currentTelemetry?.heading ?: 0f
                val animatedHeading by animateFloatAsState(
                    targetValue = headingVal,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "animated_heading"
                )
                val cardinal = getCardinalDirection(headingVal)
                val headingText = "${headingVal.toInt()}° $cardinal"

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        // Top Header Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left Identity Column: Line 1 = Name, Line 2 = Status & Heading Badges
                            Column(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Row 1: Pulsing Live Dot + Device Name
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isLive) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = selectedDevice.uppercase(),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        softWrap = false
                                    )
                                }

                                // Row 2: Status Tag (Live/Seen x ago) + Compass Heading Badge + GPS Badge
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Live / Seen X ago Pill
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isLive) Color(0xFF4ADE80).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isLive) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isLive) "LIVE • $lastSeenText" else "OFFLINE • $lastSeenText",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp,
                                                color = if (isLive) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }

                                    // Heading Badge
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Navigation,
                                                contentDescription = null,
                                                modifier = Modifier.size(9.dp).graphicsLayer { rotationZ = animatedHeading },
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = headingText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }

                                    if (currentTelemetry?.isLocationOn == false) {
                                        LocationOffBadge()
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Right Action Buttons
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Details Info Sheet Button
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    onClick = { showDeviceDetailsDialog.value = true },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Info, contentDescription = "Specs", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                // Location Toggle Button
                                Surface(
                                    shape = CircleShape,
                                    color = if (currentTelemetry?.isLocationOn == false) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    onClick = {
                                        scope.launch {
                                            try {
                                                SupabaseClientManager.sendEnableLocationCommand(selectedDevice)
                                                android.widget.Toast.makeText(context, "Location signal sent to $selectedDevice", android.widget.Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(context, "Failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (currentTelemetry?.isLocationOn == false) Icons.Filled.LocationOff else Icons.Filled.LocationOn,
                                            contentDescription = "Location Toggle",
                                            tint = if (currentTelemetry?.isLocationOn == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                // Live Mic Listener Button
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    onClick = { onNavigateToMicMonitor(selectedDevice) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Mic, contentDescription = "Mic Monitor", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Compact Bento Metric Row
                        val speedText = currentTelemetry?.let { "%.1f km/h".format(it.speed) } ?: "0.0 km/h"
                        val batteryVal = currentTelemetry?.battery ?: 100
                        val batteryText = "$batteryVal%" + if (currentTelemetry?.charging == true) " ⚡" else ""
                        val signalText = currentTelemetry?.let { "${it.signal} dBm" } ?: "-85 dBm"
                        val networkType = currentTelemetry?.networkType ?: "4G"
                        val currentZoneText = currentTelemetry?.let { telemetry ->
                            val pt = GeoPoint(telemetry.lat, telemetry.lon)
                            val zone = savedFencesList.find { GeofenceUtils.isPointInPolygon(pt, it.points) }
                            zone?.name?.uppercase() ?: "NO ZONE"
                        } ?: "NO ZONE"

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            CompactMetricChip(
                                label = "BATTERY",
                                value = batteryText,
                                icon = if (currentTelemetry?.charging == true) Icons.Default.Bolt else Icons.Default.BatteryStd,
                                highlightColor = if (batteryVal < 20) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            CompactMetricChip(
                                label = "SPEED",
                                value = speedText,
                                icon = Icons.Default.Speed,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            CompactMetricChip(
                                label = networkType,
                                value = signalText,
                                icon = Icons.Default.SignalCellularAlt,
                                modifier = Modifier.weight(1.1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            CompactMetricChip(
                                label = "ZONE",
                                value = currentZoneText,
                                icon = Icons.Default.Place,
                                modifier = Modifier.weight(1.1f)
                            )
                        }
                    }
                }
            }
        }

        // ═════════════════════════════════════════════════════════════════════
        // 4. FLEET QUICK-SWITCH STRIP (When "All Devices" is Selected)
        // ═════════════════════════════════════════════════════════════════════
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 82.dp)
        ) {
            AnimatedVisibility(
                visible = selectedDevice == "All Devices" && !isDrawingGeofence.value,
                enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
            ) {
                if (devicesList.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            devicesList.forEach { dev ->
                                val telemetry = activeMap[dev.name]
                                val devDiff = if (telemetry != null) currentTime - telemetry.lastSeen else 0L
                                val devLive = telemetry != null && devDiff < 15000
                                val devSeen = when {
                                    devDiff < 60000 -> "${devDiff / 1000}s ago"
                                    devDiff < 3600000 -> "${devDiff / 60000}m ago"
                                    else -> "${devDiff / 3600000}h ago"
                                }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                                    onClick = {
                                        updateSelectedDevice(dev.name)
                                        frameMapSelection(dev.name)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (devLive) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(
                                                text = dev.name.uppercase(),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                softWrap = false
                                            )
                                            Text(
                                                text = if (devLive) "Live • $devSeen" else "Off • $devSeen",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (devLive) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 9.sp,
                                                softWrap = false
                                            )
                                        }
                                        if (telemetry != null) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "${telemetry.battery}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontFamily = FontFamily.Monospace,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (!isDebugMode.value) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Radar, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SCANNING FOR ACTIVE TRACKERS...",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // ═════════════════════════════════════════════════════════════════════
        // 5. SLIM GEOFENCE DRAWING BAR (Zone Creation / Edit Mode)
        // ═════════════════════════════════════════════════════════════════════
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            AnimatedVisibility(
                visible = isDrawingGeofence.value,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    shadowElevation = 14.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        // Header info
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (originalGeofencePoints.value != null) "EDIT ZONE" else "DRAW NEW ZONE", 
                                    style = MaterialTheme.typography.titleSmall, 
                                    color = MaterialTheme.colorScheme.onSurface, 
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (currentGeofencePoints.size >= 3) {
                                    val areaSqM = GeofenceUtils.calculateArea(currentGeofencePoints.toList())
                                    val areaFormatted = if (areaSqM > 1000000) "%.2f km²".format(areaSqM / 1000000) else "%.0f m²".format(areaSqM)
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                                    ) {
                                        Text(
                                            text = areaFormatted,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                ) {
                                    Text(
                                        text = if (currentGeofencePoints.size < 3) "TAP MAP (MIN 3)" else "${currentGeofencePoints.size} PTS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Name field and Color presets
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = currentGeofenceName.value,
                                onValueChange = { currentGeofenceName.value = it },
                                placeholder = { Text("Zone Name (e.g. Warehouse)", style = MaterialTheme.typography.bodySmall) },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                            
                            Spacer(modifier = Modifier.width(10.dp))
                            
                            val luxuryColors = listOf(
                                "#D4AF37".toColorInt(), // Gold
                                "#0F52BA".toColorInt(), // Sapphire
                                "#9B111E".toColorInt(), // Ruby
                                "#50C878".toColorInt()  // Emerald
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                luxuryColors.forEach { color ->
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(color),
                                        onClick = { currentGeofenceColor.intValue = color },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            if (currentGeofenceColor.intValue == color) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                onClick = {
                                    if (currentGeofencePoints.isNotEmpty()) currentGeofencePoints.removeAt(currentGeofencePoints.lastIndex)
                                },
                                enabled = currentGeofencePoints.isNotEmpty(),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Undo,
                                        contentDescription = "Undo",
                                        modifier = Modifier.size(18.dp),
                                        tint = if (currentGeofencePoints.isNotEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    )
                                }
                            }
                            
                            if (originalGeofencePoints.value != null) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    onClick = { showDeleteConfirmation.value = true },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                                    }
                                }
                            }
                            
                            OutlinedButton(
                                onClick = { 
                                    if (originalGeofencePoints.value != null) {
                                        savedGeofences.add(GeofenceData(originalGeofencePoints.value!!, currentGeofenceColor.intValue, originalGeofenceName.value))
                                        sharedPrefs.edit { putString("saved_fences", GeofenceUtils.serializeGeofences(savedGeofences.toList())) }
                                    }
                                    originalGeofencePoints.value = null
                                    currentGeofencePoints.clear()
                                    currentGeofenceName.value = ""
                                    isDrawingGeofence.value = false
                                },
                                modifier = Modifier.weight(1f).height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("CANCEL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            
                            Button(
                                onClick = {
                                    if (currentGeofencePoints.size >= 3) {
                                        val finalName = currentGeofenceName.value.ifBlank { "Zone ${savedGeofences.size + 1}" }
                                        savedGeofences.add(GeofenceData(currentGeofencePoints.toList(), currentGeofenceColor.intValue, finalName))
                                        sharedPrefs.edit { putString("saved_fences", GeofenceUtils.serializeGeofences(savedGeofences.toList())) }
                                        originalGeofencePoints.value = null
                                        currentGeofencePoints.clear()
                                        currentGeofenceName.value = ""
                                        isDrawingGeofence.value = false
                                    }
                                },
                                enabled = currentGeofencePoints.size >= 3,
                                modifier = Modifier.weight(1f).height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("SAVE ZONE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Technical Specs Sheet Dialog
        if (showDeviceDetailsDialog.value && selectedDevice != "All Devices") {
            val telemetry = activeMap[selectedDevice]
            if (telemetry != null) {
                DeviceTelemetryDetailsDialog(
                    telemetry = telemetry,
                    onDismiss = { showDeviceDetailsDialog.value = false }
                )
            }
        }

        // Delete Geofence Dialog
        if (showDeleteConfirmation.value) {
            AlertDialog(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                onDismissRequest = { showDeleteConfirmation.value = false },
                title = { Text("Delete Geofence?", fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to remove this safety zone?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            originalGeofencePoints.value = null
                            currentGeofencePoints.clear()
                            currentGeofenceName.value = ""
                            isDrawingGeofence.value = false
                            showDeleteConfirmation.value = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("DELETE", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmation.value = false }) {
                        Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                shape = RoundedCornerShape(24.dp)
            )
        }

        // Sleep / Wake Confirmation Dialog
        if (showShutdownConfirmation.value) {
            val anyDeviceActive = if (selectedDevice == "All Devices") {
                activeMap.values.any { currentTime - it.lastSeen < 60000 }
            } else {
                val lastSeen = activeMap[selectedDevice]?.lastSeen ?: 0L
                currentTime - lastSeen < 60000
            }
            AlertDialog(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                onDismissRequest = { showShutdownConfirmation.value = false },
                title = {
                    Text(
                        if (anyDeviceActive) "Sleep ${if (selectedDevice == "All Devices") "All Trackers" else selectedDevice}?" 
                        else "Wake ${if (selectedDevice == "All Devices") "All Trackers" else selectedDevice}?",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        if (anyDeviceActive) "Put trackers to low-power sleep mode to save battery."
                        else "Send wake-up signal to resume live real-time tracking."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                if (selectedDevice != "All Devices") {
                                    val previousLastSeen = activeMap[selectedDevice]?.lastSeen ?: 0L
                                    if (anyDeviceActive) {
                                        SupabaseClientManager.sendSleepCommand(selectedDevice)
                                    } else {
                                        SupabaseClientManager.sendWakeCommand(selectedDevice)
                                        var success = false
                                        for (i in 1..10) {
                                            delay(1000)
                                            val currentDevice = AppState.activeDevices[selectedDevice]
                                            if (currentDevice != null && currentDevice.lastSeen > previousLastSeen) {
                                                success = true
                                                break
                                            }
                                        }
                                        withContext(Dispatchers.Main) {
                                            if (success) {
                                                android.widget.Toast.makeText(context, "Successfully woke up $selectedDevice", android.widget.Toast.LENGTH_SHORT).show()
                                            } else {
                                                android.widget.Toast.makeText(context, "No response from $selectedDevice", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                } else {
                                    activeMap.values.forEach { device ->
                                        if (anyDeviceActive) {
                                            SupabaseClientManager.sendSleepCommand(device.name)
                                        } else {
                                            SupabaseClientManager.sendWakeCommand(device.name)
                                        }
                                    }
                                }
                            }
                            showShutdownConfirmation.value = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (anyDeviceActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (anyDeviceActive) "SLEEP" else "WAKE", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showShutdownConfirmation.value = false }) {
                        Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}

@Composable
private fun CompactMetricChip(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    highlightColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = highlightColor,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )
        }
    }
}

@Composable
fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false
        )
    }
}

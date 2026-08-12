package com.aistudio.missioncontrol.pxytwe.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.aistudio.missioncontrol.pxytwe.ui.theme.MapMarkerGreen
import com.aistudio.missioncontrol.pxytwe.ui.theme.MapMarkerCyan
import com.aistudio.missioncontrol.pxytwe.ui.theme.MapMarkerYellow
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.aistudio.missioncontrol.pxytwe.utils.GeofenceData
import com.aistudio.missioncontrol.pxytwe.utils.GeofenceUtils
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

@Composable
fun FleetScreen(
    onNavigateToMicMonitor: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isDropdownExpanded by remember { mutableStateOf(false) }
    val selectedDevice = com.aistudio.missioncontrol.pxytwe.AppState.selectedDevice.value ?: "All Devices"
    
    fun updateSelectedDevice(name: String) {
        com.aistudio.missioncontrol.pxytwe.AppState.selectedDevice.value = if (name == "All Devices") null else name
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val context = LocalContext.current
        val sharedPrefs = remember { context.getSharedPreferences("geofence_prefs", android.content.Context.MODE_PRIVATE) }
        
        val startPoint = remember { GeoPoint(37.7749, -122.4194) }
        val isDrawingGeofence = com.aistudio.missioncontrol.pxytwe.AppState.isDrawingGeofence
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
        
        data class MapDevice(val name: String, val point: GeoPoint, val color: Int, val heading: Float)

        val activeMap = com.aistudio.missioncontrol.pxytwe.AppState.activeDevices
        
        var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                currentTime = System.currentTimeMillis()
            }
        }
        
        val isAwake by remember(selectedDevice) {
            derivedStateOf {
                if (selectedDevice == "All Devices") {
                    activeMap.values.any { currentTime - it.lastSeen < 15000 }
                } else {
                    val dev = activeMap[selectedDevice]
                    dev != null && (currentTime - dev.lastSeen < 15000)
                }
            }
        }
        val devicesList = activeMap.values.mapIndexed { idx, dev ->
            val color = when(idx % 3) {
                0 -> MapMarkerGreen.toArgb()
                1 -> MapMarkerCyan.toArgb()
                else -> MapMarkerYellow.toArgb()
            }
            MapDevice(dev.name, GeoPoint(dev.lat, dev.lon), color, dev.heading)
        }
        val resources = context.resources
        val mapView = remember {
            MapView(context).apply {
                setMultiTouchControls(true)
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

        // Handle MapView lifecycle
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

        // Optimized Overlays - Create once and reuse
        val savedFencesOverlay = remember { org.osmdroid.views.overlay.FolderOverlay() }
        val drawingOverlay = remember { org.osmdroid.views.overlay.FolderOverlay() }
        val historyOverlay = remember { org.osmdroid.views.overlay.FolderOverlay() }
        val devicesOverlay = remember { org.osmdroid.views.overlay.FolderOverlay() }
        
        // Cache for device bitmaps
        val deviceBitmaps = remember(devicesList) {
            devicesList.associate { device ->
                val bitmap = createBitmap(120, 120, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                
                paint.color = device.color
                paint.alpha = 255
                paint.style = android.graphics.Paint.Style.FILL
                val path = android.graphics.Path()
                path.moveTo(60f, 25f)
                path.lineTo(80f, 75f)
                path.lineTo(60f, 65f)
                path.lineTo(40f, 75f)
                path.close()
                canvas.drawPath(path, paint)
                
                paint.color = android.graphics.Color.WHITE
                val innerPath = android.graphics.Path()
                innerPath.moveTo(60f, 35f)
                innerPath.lineTo(72f, 70f)
                innerPath.lineTo(60f, 62f)
                innerPath.lineTo(48f, 70f)
                innerPath.close()
                canvas.drawPath(innerPath, paint)
                
                device.name to bitmap
            }
        }

        // Cache for drawing point bitmaps
        val drawingPointBitmap = remember(currentGeofenceColor.intValue) {
            val color = currentGeofenceColor.intValue
            val bitmap = createBitmap(40, 40, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            paint.color = color
            paint.alpha = 50
            canvas.drawCircle(20f, 20f, 20f, paint)
            paint.alpha = 255
            canvas.drawCircle(20f, 20f, 10f, paint)
            bitmap
        }

        // Cache for label text bitmaps
        val createTextBitmap: (String, Int) -> android.graphics.Bitmap = { text, color ->
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            paint.textSize = 32f
            paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            paint.color = color
            
            val textBounds = android.graphics.Rect()
            paint.getTextBounds(text, 0, text.length, textBounds)
            
            val paddingX = 24
            val paddingY = 16
            val width = textBounds.width() + paddingX * 2
            val height = textBounds.height() + paddingY * 2
            
            val bitmap = createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            
            // Draw background pill
            val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            bgPaint.color = android.graphics.Color.argb(220, 25, 25, 25)
            val rect = android.graphics.RectF(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawRoundRect(rect, height / 2f, height / 2f, bgPaint)
            
            // Draw border
            val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            borderPaint.style = android.graphics.Paint.Style.STROKE
            borderPaint.strokeWidth = 4f
            borderPaint.color = color
            canvas.drawRoundRect(rect, height / 2f, height / 2f, borderPaint)
            
            // Draw text
            canvas.drawText(text, paddingX.toFloat() - textBounds.left, paddingY.toFloat() - textBounds.top, paint)
            
            bitmap
        }
        val geofencePointsList = currentGeofencePoints.toList()
        val savedFencesList = savedGeofences.toList()

        val geofenceLabelBitmaps = remember(savedFencesList) {
            val cache = mutableMapOf<String, android.graphics.Bitmap>()
            savedFencesList.forEach { fence ->
                cache[fence.name] = createTextBitmap(fence.name, fence.colorArgb)
            }
            cache
        }

        fun frameMapSelection(targetDevice: String) {
            if (targetDevice == "All Devices") {
                if (devicesList.isEmpty()) {
                    mapView.controller.animateTo(startPoint)
                    mapView.controller.setZoom(15.0)
                } else if (devicesList.size == 1) {
                    mapView.controller.animateTo(devicesList.first().point)
                    mapView.controller.setZoom(17.0)
                } else {
                    val points = devicesList.map { it.point }
                    val boundingBox = org.osmdroid.util.BoundingBox.fromGeoPoints(points)
                    if (boundingBox.latNorth == boundingBox.latSouth && boundingBox.lonEast == boundingBox.lonWest) {
                        mapView.controller.animateTo(points.first())
                        mapView.controller.setZoom(17.0)
                    } else {
                        mapView.zoomToBoundingBox(boundingBox, true, 150)
                    }
                }
            } else {
                val device = devicesList.find { it.name == targetDevice }
                if (device != null) {
                    mapView.controller.animateTo(device.point)
                    mapView.controller.setZoom(17.0)
                }
            }
        }

        // Auto-center on first device if not already centered
        var hasInitialCentered by remember { mutableStateOf(false) }
        LaunchedEffect(devicesList.isNotEmpty()) {
            if (devicesList.isNotEmpty() && !hasInitialCentered) {
                frameMapSelection(selectedDevice)
                hasInitialCentered = true
            }
        }

        AndroidView(
            factory = {
                mapView.apply {
                    overlays.add(savedFencesOverlay)
                    overlays.add(historyOverlay)
                    overlays.add(drawingOverlay)
                    overlays.add(devicesOverlay)
                    
                    // Advanced dragging overlay: instant response, prevents map pan
                    val interactionOverlay = object : org.osmdroid.views.overlay.Overlay() {
                        var draggedIndex = -1
                        
                        override fun onTouchEvent(event: android.view.MotionEvent, mapView: org.osmdroid.views.MapView): Boolean {
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
                                        if (dist < 100f && dist < minDistance) { // Generous hit radius
                                            minDistance = dist
                                            closestIndex = i
                                        }
                                    }
                                    if (closestIndex != -1) {
                                        draggedIndex = closestIndex
                                        mapView.parent?.requestDisallowInterceptTouchEvent(true)
                                        return true // Consume touch, blocks map from panning
                                    }
                                }
                                android.view.MotionEvent.ACTION_MOVE -> {
                                    if (draggedIndex != -1) {
                                        // Live update shape and marker visually without triggering Compose
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
                                            // This will trigger a recomposition and update our geofencePointsList
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
            update = { view ->
                // Empty update block to prevent Compose recompositions from tearing down the overlays during interactions.
                // Overlays are managed by LaunchedEffects below.
            },
            modifier = Modifier.fillMaxSize()
        )

        // Sync Saved Fences
        LaunchedEffect(savedFencesList, isDrawingGeofence.value) {
            savedFencesOverlay.items.clear()
            savedFencesList.forEach { fenceData ->
                val polygon = Polygon()
                polygon.points = fenceData.points
                polygon.fillPaint.color = ColorUtils.setAlphaComponent(fenceData.colorArgb, 30)
                polygon.outlinePaint.color = fenceData.colorArgb
                polygon.outlinePaint.strokeWidth = 6f
                polygon.outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(30f, 15f), 0f)
                polygon.outlinePaint.setShadowLayer(15f, 0f, 0f, fenceData.colorArgb)
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

        // Sync Devices and History
        LaunchedEffect(devicesList, selectedDevice) {
            historyOverlay.items.clear()
            devicesOverlay.items.clear()
            
            val activeDevices = activeMap.values.toList()
            activeDevices.forEach { dev ->
                if (selectedDevice == "All Devices" || selectedDevice == dev.name) {
                    if (dev.history.size >= 2) {
                        val polyline = Polyline()
                        polyline.setPoints(dev.history.map { GeoPoint(it.first, it.second) })
                        polyline.outlinePaint.color = ColorUtils.setAlphaComponent(MapMarkerCyan.toArgb(), 100)
                        polyline.outlinePaint.strokeWidth = 4f
                        historyOverlay.add(polyline)
                    }
                }
            }

            devicesList.forEach { device ->
                if (selectedDevice == "All Devices" || selectedDevice == device.name) {
                    val marker = Marker(mapView)
                    marker.position = device.point
                    marker.title = device.name
                    marker.icon = deviceBitmaps[device.name]?.toDrawable(resources)
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    marker.rotation = -device.heading
                    marker.setOnMarkerClickListener { _, _ ->
                        updateSelectedDevice(device.name)
                        mapView.controller.animateTo(device.point)
                        true
                    }
                    devicesOverlay.add(marker)
                }
            }
            mapView.invalidate()
        }

        // Sync Drawing Geofence
        LaunchedEffect(geofencePointsList, currentGeofenceColor.intValue, isDrawingGeofence.value) {
            drawingOverlay.items.clear()
            if (isDrawingGeofence.value && geofencePointsList.isNotEmpty()) {
                val activeColor = currentGeofenceColor.intValue
                if (geofencePointsList.size >= 3) {
                    val polygon = Polygon()
                    polygon.points = geofencePointsList
                    polygon.fillPaint.color = ColorUtils.setAlphaComponent(activeColor, 80)
                    polygon.outlinePaint.color = activeColor
                    polygon.outlinePaint.strokeWidth = 6f
                    drawingOverlay.add(polygon)
                } else if (geofencePointsList.size == 2) {
                    val polyline = Polyline()
                    polyline.setPoints(geofencePointsList)
                    polyline.outlinePaint.color = activeColor
                    polyline.outlinePaint.strokeWidth = 6f
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
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.6f to Color.Transparent,
                        1.0f to MaterialTheme.colorScheme.background.copy(alpha = 0.9f)
                    )
                )
        )

        if (devicesList.isEmpty() && !isDrawingGeofence.value) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Radar,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "SEARCHING FOR TARGET DEVICES...",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Open and start the Live Tracker app on a target device to connect.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Floating Header Pill (Fleet Selector)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                .align(Alignment.TopCenter),
            contentAlignment = Alignment.TopCenter
        ) {
            AnimatedVisibility(
                visible = !isDrawingGeofence.value,
                enter = slideInVertically(
                    initialOffsetY = { -it }, 
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { -it }, 
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
                ) + fadeOut()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .clickable { isDropdownExpanded = !isDropdownExpanded }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val dotColor = if (isAwake) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = if (selectedDevice == "All Devices") "ALL DEVICES (${devicesList.size})" else selectedDevice.uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f, fill = false),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val rotation by animateFloatAsState(
                                    targetValue = if (isDropdownExpanded) 180f else 0f,
                                    label = "dropdown_rotation"
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp).rotate(rotation),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }


                    }
                    
                    AnimatedVisibility(
                        visible = isDropdownExpanded,
                        enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                        exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .widthIn(max = 240.dp)
                                .padding(top = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                val devices = listOf("All Devices") + devicesList.map { it.name }
                                devices.forEach { deviceName ->
                                    val isSelected = selectedDevice == deviceName
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                updateSelectedDevice(deviceName)
                                                isDropdownExpanded = false
                                                frameMapSelection(deviceName)
                                            }
                                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha=0.5f) else Color.Transparent)
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val deviceIsLive = if (deviceName == "All Devices") {
                                            activeMap.values.any { currentTime - it.lastSeen < 15000 }
                                        } else {
                                            val dev = activeMap[deviceName]
                                            dev != null && (currentTime - dev.lastSeen < 15000)
                                        }
                                        val itemDotColor = if (deviceIsLive) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(itemDotColor)
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = deviceName.uppercase(),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        if (showDeleteConfirmation.value) {
            AlertDialog(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                onDismissRequest = { showDeleteConfirmation.value = false },
                title = { Text("Delete Geofence?") },
                text = { Text("This action cannot be undone. Are you sure you want to remove this zone?") },
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
                        Text("DELETE")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmation.value = false }) {
                        Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            AnimatedVisibility(
                visible = isDrawingGeofence.value,
                enter = slideInVertically(
                    initialOffsetY = { it }, 
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it }, 
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
                ) + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    shadowElevation = 16.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (originalGeofencePoints.value != null) "EDIT ZONE" else "NEW ZONE", 
                                    style = MaterialTheme.typography.titleSmall, 
                                    color = MaterialTheme.colorScheme.onSurface, 
                                    letterSpacing = 1.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = if (currentGeofencePoints.size < 3) "TAP MAP (MIN 3)" else "${currentGeofencePoints.size} PTS",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = currentGeofenceName.value,
                                onValueChange = { currentGeofenceName.value = it },
                                placeholder = { Text("Zone Name", style = MaterialTheme.typography.bodySmall) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val luxuryColors = listOf(
                                    "#D4AF37".toColorInt(), // Gold
                                    "#0F52BA".toColorInt(), // Sapphire
                                    "#9B111E".toColorInt(), // Ruby
                                    "#50C878".toColorInt()  // Emerald
                                )
                                luxuryColors.forEach { color ->
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clickable { currentGeofenceColor.intValue = color },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(Color(color))
                                                .border(
                                                    width = if (currentGeofenceColor.intValue == color) 2.dp else 0.dp,
                                                    color = if (currentGeofenceColor.intValue == color) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (currentGeofenceColor.intValue == color) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clickable(enabled = currentGeofencePoints.isNotEmpty()) {
                                        if (currentGeofencePoints.isNotEmpty()) currentGeofencePoints.removeAt(currentGeofencePoints.lastIndex)
                                    },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Undo,
                                        contentDescription = "Undo",
                                        modifier = Modifier.size(20.dp),
                                        tint = if (currentGeofencePoints.isNotEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    )
                                }
                            }
                            
                            if (originalGeofencePoints.value != null) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clickable { showDeleteConfirmation.value = true },
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
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
                                modifier = Modifier.weight(1f).height(40.dp),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("CANCEL", style = MaterialTheme.typography.labelMedium)
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
                                modifier = Modifier.weight(1f).height(40.dp),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("SAVE", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(y = (-64).dp)
                .padding(end = 16.dp)
        ) {
            AnimatedVisibility(
                visible = !isDrawingGeofence.value,
                enter = slideInHorizontally(
                    initialOffsetX = { it }, 
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                ) + fadeIn(),
                exit = slideOutHorizontally(
                    targetOffsetX = { it }, 
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
                ) + fadeOut()
            ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        shadowElevation = 0.dp
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                        IconButton(onClick = { 
                            isDrawingGeofence.value = true
                            originalGeofencePoints.value = null
                            currentGeofencePoints.clear()
                            currentGeofenceName.value = ""
                        }) {
                            Icon(Icons.Default.AddLocationAlt, contentDescription = "New Geofence", tint = if (isDrawingGeofence.value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                        HorizontalDivider(
                            modifier = Modifier.width(24.dp).padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                        IconButton(onClick = { 
                            frameMapSelection(selectedDevice)
                        }) {
                            Icon(Icons.Default.MyLocation, contentDescription = "My Location", tint = MaterialTheme.colorScheme.primary)
                        }
                        HorizontalDivider(
                            modifier = Modifier.width(24.dp).padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                        val anyDeviceActive = if (selectedDevice == "All Devices") {
                            activeMap.values.any { System.currentTimeMillis() - it.lastSeen < 60000 }
                        } else {
                            val lastSeen = activeMap[selectedDevice]?.lastSeen ?: 0L
                            System.currentTimeMillis() - lastSeen < 60000
                        }
                        IconButton(onClick = { 
                            showShutdownConfirmation.value = true
                        }) {
                            Icon(
                                if (anyDeviceActive) Icons.Default.PowerSettingsNew else Icons.Default.WbSunny, 
                                contentDescription = if (anyDeviceActive) "Sleep Trackers" else "Wake Trackers", 
                                tint = if (anyDeviceActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
        
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp)
                .padding(bottom = 112.dp)
        ) {
            AnimatedVisibility(
                visible = selectedDevice != "All Devices" && !isDrawingGeofence.value,
                enter = slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it / 2 },
                    animationSpec = tween(200)
                ) + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        val currentTelemetry = com.aistudio.missioncontrol.pxytwe.AppState.activeDevices[selectedDevice]
                        
                        val lastSeenText = currentTelemetry?.let {
                            val diff = currentTime - it.lastSeen
                            if (diff < 60000) "${diff / 1000}s AGO"
                            else "${diff / 60000}m AGO"
                        } ?: "UNKNOWN"

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                val isLive = currentTelemetry != null && (currentTime - currentTelemetry.lastSeen < 15000)
                                val transition = rememberInfiniteTransition(label = "pulse")
                                val dotAlpha by transition.animateFloat(
                                    initialValue = 0.4f,
                                    targetValue = if (isLive) 1f else 0.4f,
                                    animationSpec = infiniteRepeatable(animation = tween(1000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
                                    label = "dot_alpha"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (isLive) Color(0xFF4ADE80).copy(alpha = dotAlpha) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = selectedDevice.uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = lastSeenText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        val speedText = currentTelemetry?.let { String.format(java.util.Locale.US, "%.1f km/h", it.speed) } ?: "N/A"
                        val batteryText = currentTelemetry?.let { "${it.battery}%" + if (it.charging) " (AC)" else "" } ?: "N/A"
                        val signalText = currentTelemetry?.let { "${it.signal} dBm" } ?: "N/A"
                        val currentZoneText = currentTelemetry?.let { telemetry ->
                            val pt = GeoPoint(telemetry.lat, telemetry.lon)
                            val zone = savedFencesList.find { GeofenceUtils.isPointInPolygon(pt, it.points) }
                            zone?.name?.uppercase() ?: "NONE"
                        } ?: "UNKNOWN"
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            HUDDataBlock("SPEED", speedText, modifier = Modifier.weight(1f))
                            HUDDataBlock("BATTERY", batteryText, modifier = Modifier.weight(1f))
                            HUDDataBlock("ZONE", currentZoneText, modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        val pingText = currentTelemetry?.let {
                            if (it.ping >= 0) "${it.ping} ms" else "..."
                        } ?: "N/A"
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            HUDDataBlock("SIGNAL", signalText, modifier = Modifier.weight(1f))
                            HUDDataBlock("PING", pingText, modifier = Modifier.weight(1f))
                            
                            // Microphone Button
                            IconButton(
                                onClick = { onNavigateToMicMonitor(selectedDevice) },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier
                                    .size(44.dp)
                                    .shadow(8.dp, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Mic Monitor",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showShutdownConfirmation.value) {
            val anyDeviceActive = if (selectedDevice == "All Devices") {
                activeMap.values.any { System.currentTimeMillis() - it.lastSeen < 60000 }
            } else {
                val lastSeen = activeMap[selectedDevice]?.lastSeen ?: 0L
                System.currentTimeMillis() - lastSeen < 60000
            }
            AlertDialog(
                onDismissRequest = { showShutdownConfirmation.value = false },
                title = { Text(if (anyDeviceActive) "Sleep ${if (selectedDevice == "All Devices") "All Trackers" else selectedDevice}?" else "Wake ${if (selectedDevice == "All Devices") "All Trackers" else selectedDevice}?", fontWeight = FontWeight.Bold) },
                text = { Text(if (anyDeviceActive) "This will put ${if (selectedDevice == "All Devices") "all tracking devices" else selectedDevice} to sleep to save battery." else "This will wake ${if (selectedDevice == "All Devices") "all tracking devices" else selectedDevice} and resume location tracking.") },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                if (selectedDevice != "All Devices") {
                                    val previousLastSeen = activeMap[selectedDevice]?.lastSeen ?: 0L
                                    if (anyDeviceActive) {
                                        com.aistudio.missioncontrol.pxytwe.SupabaseClientManager.sendSleepCommand(selectedDevice)
                                    } else {
                                        com.aistudio.missioncontrol.pxytwe.SupabaseClientManager.sendWakeCommand(selectedDevice)
                                        var success = false
                                        for (i in 1..10) {
                                            kotlinx.coroutines.delay(1000)
                                            val currentDevice = com.aistudio.missioncontrol.pxytwe.AppState.activeDevices[selectedDevice]
                                            if (currentDevice != null && currentDevice.lastSeen > previousLastSeen) {
                                                success = true
                                                break
                                            }
                                        }
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            if (success) {
                                                android.widget.Toast.makeText(context, "Successfully woke up $selectedDevice", android.widget.Toast.LENGTH_SHORT).show()
                                            } else {
                                                android.widget.Toast.makeText(context, "Failed to wake up $selectedDevice (no response)", android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                } else {
                                    val previousLastSeenMap = activeMap.mapValues { it.value.lastSeen }
                                    activeMap.values.forEach { device ->
                                        if (anyDeviceActive) {
                                            com.aistudio.missioncontrol.pxytwe.SupabaseClientManager.sendSleepCommand(device.name)
                                        } else {
                                            com.aistudio.missioncontrol.pxytwe.SupabaseClientManager.sendWakeCommand(device.name)
                                        }
                                    }
                                    if (!anyDeviceActive) {
                                        var anySuccess = false
                                        for (i in 1..10) {
                                            kotlinx.coroutines.delay(1000)
                                            val currentAnySuccess = previousLastSeenMap.any { (name, prevLastSeen) -> 
                                                val currentDevice = com.aistudio.missioncontrol.pxytwe.AppState.activeDevices[name]
                                                currentDevice != null && currentDevice.lastSeen > prevLastSeen
                                            }
                                            if (currentAnySuccess) {
                                                anySuccess = true
                                                break
                                            }
                                        }
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            if (anySuccess) {
                                                android.widget.Toast.makeText(context, "Successfully woke up some devices", android.widget.Toast.LENGTH_SHORT).show()
                                            } else {
                                                android.widget.Toast.makeText(context, "Failed to wake up any devices (no response)", android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                }
                            }
                            showShutdownConfirmation.value = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (anyDeviceActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    ) {
                        Text(if (anyDeviceActive) "SLEEP" else "WAKE")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showShutdownConfirmation.value = false }) {
                        Text("CANCEL")
                    }
                }
            )
        }
    }
}

@Composable
fun HUDDataBlock(label: String, value: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 20.sp
            )
        } else {
            SkeletonLoader(modifier = Modifier.height(20.dp).fillMaxWidth(0.6f))
        }
    }
}

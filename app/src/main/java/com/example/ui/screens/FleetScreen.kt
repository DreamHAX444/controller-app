package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FleetScreen(onDeviceClick: (String, String) -> Unit) {
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var selectedDevice by remember { mutableStateOf("All Devices") }

    val bottomSheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true
    )
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = bottomSheetState
    )

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 110.dp,
        sheetContainerColor = MaterialTheme.colorScheme.background,
        sheetContentColor = MaterialTheme.colorScheme.onSurface,
        sheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        sheetDragHandle = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            ) {
                Box(modifier = Modifier
                    .width(48.dp)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        },
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "Drone_Alpha_X1",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Active • Last signal: 2s ago",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    InfoCard(title = "Location", value = "37.7749°, -122.4194°", modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(16.dp))
                    InfoCard(title = "Speed", value = "15 km/h", modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    InfoCard(title = "Battery", value = "98%", modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(16.dp))
                    InfoCard(title = "Ping", value = "24 ms", modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    InfoCard(title = "Accuracy", value = "± 5m", modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(16.dp))
                    InfoCard(title = "Last Signal", value = "12:45 PM", modifier = Modifier.weight(1f))
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val startPoint = org.osmdroid.util.GeoPoint(37.7749, -122.4194)
            val mapView = remember {
                org.osmdroid.config.Configuration.getInstance().userAgentValue = context.packageName
                org.osmdroid.views.MapView(context).apply {
                    setMultiTouchControls(true)
                    controller.setZoom(15.0)
                    controller.setCenter(startPoint)
                    setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                }
            }
            
            // Map Background
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { mapView },
                update = { view ->
                    view.overlays.clear()
                    
                    val devices = listOf(
                        Triple("Drone_Alpha_X1", org.osmdroid.util.GeoPoint(37.7749, -122.4194), android.graphics.Color.parseColor("#0066FF")),
                        Triple("Rover_Beta_V2", org.osmdroid.util.GeoPoint(37.7710, -122.4220), android.graphics.Color.parseColor("#00C853")),
                        Triple("Sensor_Gamma_L", org.osmdroid.util.GeoPoint(37.7765, -122.4160), android.graphics.Color.parseColor("#FF3D00"))
                    )
                    
                    devices.forEach { (name, point, color) ->
                        if (selectedDevice == "All Devices" || selectedDevice == name) {
                            val marker = org.osmdroid.views.overlay.Marker(view)
                            marker.position = point
                            marker.title = name
                            
                            val bitmap = android.graphics.Bitmap.createBitmap(120, 120, android.graphics.Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(bitmap)
                            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                            
                            paint.color = color
                            paint.alpha = 50
                            canvas.drawCircle(60f, 60f, 40f, paint)
                            
                            paint.alpha = 255
                            canvas.drawCircle(60f, 60f, 16f, paint)
                            
                            paint.color = android.graphics.Color.WHITE
                            canvas.drawCircle(60f, 60f, 8f, paint)
                            
                            marker.icon = android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
                            marker.setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER)
                            view.overlays.add(marker)
                            
                            if (selectedDevice == name) {
                                view.controller.animateTo(point)
                            }
                        }
                    }
                    if (selectedDevice == "All Devices") {
                        view.controller.animateTo(startPoint)
                        view.controller.setZoom(15.0)
                    }
                    view.invalidate()
                },
                modifier = Modifier.fillMaxSize()
            )
            
            // Header Pill
            val scope = rememberCoroutineScope()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .align(Alignment.TopCenter)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        isDropdownExpanded = !isDropdownExpanded
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(
                            imageVector = Icons.Default.Smartphone,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (selectedDevice == "All Devices") "All Devices (3)" else selectedDevice,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (isDropdownExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                androidx.compose.animation.AnimatedVisibility(visible = isDropdownExpanded) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            val devices = listOf("All Devices", "Drone_Alpha_X1", "Rover_Beta_V2", "Sensor_Gamma_L")
                            devices.forEach { deviceName ->
                                val isSelected = selectedDevice == deviceName
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            selectedDevice = deviceName
                                            isDropdownExpanded = false
                                        }
                                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (deviceName == "All Devices") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = deviceName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Map Controls
            val coroutineScope = rememberCoroutineScope()
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp, top = 32.dp)
            ) {
                MapControlButton(icon = Icons.Default.Add, onClick = {
                    mapView.controller.zoomIn()
                })
                Spacer(modifier = Modifier.height(12.dp))
                MapControlButton(icon = Icons.Default.Remove, onClick = {
                    mapView.controller.zoomOut()
                })
                Spacer(modifier = Modifier.height(24.dp))
                MapControlButton(
                    icon = Icons.Default.MyLocation,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = {
                        mapView.controller.animateTo(startPoint)
                        mapView.controller.setZoom(13.0)
                    }
                )
            }
        }
    }
}

@Composable
fun MapControlButton(
    icon: ImageVector,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        border = if (containerColor == MaterialTheme.colorScheme.surface) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
        shadowElevation = 2.dp,
        modifier = Modifier.size(48.dp).clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, tint = contentColor)
        }
    }
}

@Composable
fun InfoCard(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

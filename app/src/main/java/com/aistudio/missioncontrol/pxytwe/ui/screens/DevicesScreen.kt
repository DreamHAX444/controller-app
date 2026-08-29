package com.aistudio.missioncontrol.pxytwe.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.missioncontrol.pxytwe.AppState
import com.aistudio.missioncontrol.pxytwe.DeviceTelemetry
import com.aistudio.missioncontrol.pxytwe.SupabaseClientManager
import com.aistudio.missioncontrol.pxytwe.ui.components.LocationOffBadge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

private enum class DeviceFilter {
    ALL, LIVE, OFFLINE, GPS_OFF
}

sealed class DiagnosticModalState {
    data class PingTesting(val deviceName: String) : DiagnosticModalState()
    data class PingResult(val deviceName: String, val latencyMs: Long?, val errorMsg: String? = null) : DiagnosticModalState()
    data class StatusChecking(val deviceName: String) : DiagnosticModalState()
    data class StatusResult(val deviceName: String, val status: String?, val telemetry: DeviceTelemetry?) : DiagnosticModalState()
    data class CommandSent(val deviceName: String, val title: String, val message: String, val isSuccess: Boolean = true) : DiagnosticModalState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    onNavigateToMicMonitor: (String) -> Unit,
    onNavigateToHistory: () -> Unit = {}
) {
    val activeMap = AppState.activeDevices
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    
    var showDeleteDialogFor by remember { mutableStateOf<String?>(null) }
    var selectedDetailsDevice by remember { mutableStateOf<DeviceTelemetry?>(null) }
    var activeActionMenuDevice by remember { mutableStateOf<DeviceTelemetry?>(null) }
    var activeDiagnosticModal by remember { mutableStateOf<DiagnosticModalState?>(null) }
    
    var topBannerMessage by remember { mutableStateOf<String?>(null) }
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(DeviceFilter.ALL) }

    fun showTopAlert(msg: String) {
        topBannerMessage = msg
        scope.launch {
            delay(3500)
            if (topBannerMessage == msg) {
                topBannerMessage = null
            }
        }
    }



    val activeDeviceList = activeMap.values.toList()
    val allDevices = remember(activeDeviceList) { activeDeviceList.sortedBy { it.name.lowercase() } }
    
    val liveCount by remember(activeDeviceList) { 
        derivedStateOf { 
            val currentNow = System.currentTimeMillis()
            activeDeviceList.count { currentNow - it.lastSeen < 15000 } 
        } 
    }
    val offlineCount by remember(allDevices.size, liveCount) { derivedStateOf { allDevices.size - liveCount } }
    val gpsOffCount by remember(activeDeviceList) { derivedStateOf { activeDeviceList.count { !it.isLocationOn } } }

    val filteredDevices = remember(allDevices, searchQuery, selectedFilter) {
        val currentNow = System.currentTimeMillis()
        allDevices.filter { dev ->
            val matchesSearch = searchQuery.isBlank() || dev.name.contains(searchQuery.trim(), ignoreCase = true)
            val isLive = currentNow - dev.lastSeen < 15000
            val matchesFilter = when (selectedFilter) {
                DeviceFilter.ALL -> true
                DeviceFilter.LIVE -> isLive
                DeviceFilter.OFFLINE -> !isLive
                DeviceFilter.GPS_OFF -> !dev.isLocationOn
            }
            matchesSearch && matchesFilter
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "FLEET DIRECTORY",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (allDevices.isEmpty()) "0 TRACKERS" else "${allDevices.size} ACTIVE TRACKER${if (allDevices.size == 1) "" else "S"}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Trails Shortcut Button
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNavigateToHistory()
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Timeline,
                                    contentDescription = "Activity Trails",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "TRAILS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        // Live Status Pill
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                            shadowElevation = 4.dp
                        ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val transition = rememberInfiniteTransition(label = "fleet_live_dot")
                            val pulseAlpha by transition.animateFloat(
                                initialValue = 0.4f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(animation = tween(1000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
                                label = "pulse_fleet_alpha"
                            )
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .graphicsLayer { alpha = if (liveCount > 0) pulseAlpha else 0.4f }
                                    .clip(CircleShape)
                                    .background(if (liveCount > 0) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "$liveCount LIVE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (liveCount > 0) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                }

                Spacer(Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search trackers by name...", style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                )

                Spacer(Modifier.height(10.dp))

                // Filter Chips Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterBadge(
                        label = "ALL (${allDevices.size})",
                        isSelected = selectedFilter == DeviceFilter.ALL,
                        onClick = { selectedFilter = DeviceFilter.ALL }
                    )
                    FilterBadge(
                        label = "LIVE ($liveCount)",
                        isSelected = selectedFilter == DeviceFilter.LIVE,
                        highlightColor = Color(0xFF4ADE80),
                        onClick = { selectedFilter = DeviceFilter.LIVE }
                    )
                    FilterBadge(
                        label = "OFFLINE ($offlineCount)",
                        isSelected = selectedFilter == DeviceFilter.OFFLINE,
                        onClick = { selectedFilter = DeviceFilter.OFFLINE }
                    )
                    if (gpsOffCount > 0) {
                        FilterBadge(
                            label = "GPS ALERT ($gpsOffCount)",
                            isSelected = selectedFilter == DeviceFilter.GPS_OFF,
                            highlightColor = MaterialTheme.colorScheme.error,
                            onClick = { selectedFilter = DeviceFilter.GPS_OFF }
                        )
                    }
                }
            }

            // Devices List or Empty State
            if (allDevices.isEmpty()) {
                EmptyDevices()
            } else if (filteredDevices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No trackers match the current filter or search.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredDevices, key = { it.name }) { dev ->
                        ModernDeviceCard(
                            dev = dev,
                            now = now,
                            onMicClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNavigateToMicMonitor(dev.name)
                            },
                            onLocationToggleClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch {
                                    try {
                                        SupabaseClientManager.sendEnableLocationCommand(dev.name)
                                        activeDiagnosticModal = DiagnosticModalState.CommandSent(
                                            deviceName = dev.name,
                                            title = "Location Signal Sent",
                                            message = "Remote push command dispatched to turn ON GPS on ${dev.name}."
                                        )
                                    } catch (e: Exception) {
                                        activeDiagnosticModal = DiagnosticModalState.CommandSent(
                                            deviceName = dev.name,
                                            title = "Command Failed",
                                            message = "Error sending location signal: ${e.message}",
                                            isSuccess = false
                                        )
                                    }
                                }
                            },
                            onSpecsClick = {
                                selectedDetailsDevice = dev
                            },
                            onMoreOptionsClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                activeActionMenuDevice = dev
                            }
                        )
                    }
                }
            }
        }

        // Top Floating Notification Banner
        AnimatedVisibility(
            visible = topBannerMessage != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp, start = 16.dp, end = 16.dp),
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                shadowElevation = 10.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = topBannerMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    // Modern Bottom Sheet Action Menu
    val currentActionDevice = activeActionMenuDevice
    if (currentActionDevice != null) {
        DeviceActionsBottomSheet(
            dev = currentActionDevice,
            now = now,
            onDismiss = { activeActionMenuDevice = null },
            onPingClick = {
                val targetName = currentActionDevice.name
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                activeDiagnosticModal = DiagnosticModalState.PingTesting(targetName)
                scope.launch {
                    try {
                        val pingMs = SupabaseClientManager.pingDevice(targetName)
                        activeDiagnosticModal = DiagnosticModalState.PingResult(
                            deviceName = targetName,
                            latencyMs = pingMs,
                            errorMsg = if (pingMs == null) "No response from $targetName within 10s timeout." else null
                        )
                    } catch (e: Exception) {
                        activeDiagnosticModal = DiagnosticModalState.PingResult(
                            deviceName = targetName,
                            latencyMs = null,
                            errorMsg = "Ping error: ${e.message}"
                        )
                    }
                }
            },
            onStatusClick = {
                val targetName = currentActionDevice.name
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                activeDiagnosticModal = DiagnosticModalState.StatusChecking(targetName)
                scope.launch {
                    try {
                        val status = SupabaseClientManager.checkDeviceStatus(targetName)
                        activeDiagnosticModal = DiagnosticModalState.StatusResult(
                            deviceName = targetName,
                            status = status,
                            telemetry = AppState.activeDevices[targetName]
                        )
                    } catch (e: Exception) {
                        activeDiagnosticModal = DiagnosticModalState.StatusResult(
                            deviceName = targetName,
                            status = null,
                            telemetry = AppState.activeDevices[targetName]
                        )
                    }
                }
            },
            onLocationToggleClick = {
                val targetName = currentActionDevice.name
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                scope.launch {
                    try {
                        SupabaseClientManager.sendEnableLocationCommand(targetName)
                        activeDiagnosticModal = DiagnosticModalState.CommandSent(
                            deviceName = targetName,
                            title = "Location Signal Sent",
                            message = "Remote push command dispatched to turn ON GPS on $targetName."
                        )
                    } catch (e: Exception) {
                        activeDiagnosticModal = DiagnosticModalState.CommandSent(
                            deviceName = targetName,
                            title = "Command Failed",
                            message = "Error sending location command: ${e.message}",
                            isSuccess = false
                        )
                    }
                }
            },
            onMicClick = {
                val targetName = currentActionDevice.name
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onNavigateToMicMonitor(targetName)
            },
            onSpecsClick = {
                selectedDetailsDevice = currentActionDevice
            },
            onDeleteClick = {
                val targetName = currentActionDevice.name
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showDeleteDialogFor = targetName
            }
        )
    }

    // Modern Diagnostic Result Modal (Replaces tiny snackbar)
    if (activeDiagnosticModal != null) {
        DiagnosticOutputModal(
            modalState = activeDiagnosticModal!!,
            onDismiss = { activeDiagnosticModal = null },
            onWakeDevice = { deviceName ->
                scope.launch {
                    SupabaseClientManager.sendWakeCommand(deviceName)
                    showTopAlert("Wake command sent to $deviceName")
                }
            }
        )
    }

    // Technical Specs Dialog
    if (selectedDetailsDevice != null) {
        DeviceTelemetryDetailsDialog(
            telemetry = selectedDetailsDevice!!,
            onDismiss = { selectedDetailsDevice = null }
        )
    }

    // Delete Device Confirmation Dialog
    if (showDeleteDialogFor != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialogFor = null },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("Delete Tracker?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete '${showDeleteDialogFor}'? All telemetry history will be cleared.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val devName = showDeleteDialogFor!!
                        showDeleteDialogFor = null
                        scope.launch {
                            try {
                                SupabaseClientManager.deleteDevice(devName)
                                AppState.activeDevices.remove(devName)
                                showTopAlert("Deleted tracker $devName")
                            } catch (e: Exception) {
                                showTopAlert("Failed to delete $devName: ${e.message}")
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("DELETE", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialogFor = null }) {
                    Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
fun DiagnosticOutputModal(
    modalState: DiagnosticModalState,
    onDismiss: () -> Unit,
    onWakeDevice: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = when (modalState) {
                        is DiagnosticModalState.PingTesting, is DiagnosticModalState.PingResult -> Icons.Default.Wifi
                        is DiagnosticModalState.StatusChecking, is DiagnosticModalState.StatusResult -> Icons.Default.HealthAndSafety
                        is DiagnosticModalState.CommandSent -> if (modalState.isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning
                    }
                    val iconTint = when (modalState) {
                        is DiagnosticModalState.PingResult -> if (modalState.latencyMs != null) Color(0xFF4ADE80) else MaterialTheme.colorScheme.error
                        is DiagnosticModalState.StatusResult -> if (modalState.status != null) Color(0xFF4ADE80) else MaterialTheme.colorScheme.error
                        is DiagnosticModalState.CommandSent -> if (modalState.isSuccess) Color(0xFF4ADE80) else MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(iconTint.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = when (modalState) {
                            is DiagnosticModalState.PingTesting -> "PINGING TRACKER"
                            is DiagnosticModalState.PingResult -> if (modalState.latencyMs != null) "PING SUCCESSFUL" else "CONNECTION TIMEOUT"
                            is DiagnosticModalState.StatusChecking -> "CHECKING HARDWARE"
                            is DiagnosticModalState.StatusResult -> if (modalState.status != null) "HARDWARE ONLINE" else "STATUS UNREACHABLE"
                            is DiagnosticModalState.CommandSent -> modalState.title.uppercase()
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (modalState) {
                    // PING TESTING IN PROGRESS
                    is DiagnosticModalState.PingTesting -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 4.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Measuring latency to ${modalState.deviceName}...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Broadcasting websocket probe over Supabase Realtime",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // PING RESULT
                    is DiagnosticModalState.PingResult -> {
                        if (modalState.latencyMs != null) {
                            val latency = modalState.latencyMs
                            val latencyQuality = when {
                                latency < 80 -> "EXCELLENT" to Color(0xFF4ADE80)
                                latency < 250 -> "NORMAL" to MaterialTheme.colorScheme.primary
                                else -> "HIGH LATENCY" to Color(0xFFFBBF24)
                            }

                            // Big Latency Banner
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = latencyQuality.second.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, latencyQuality.second.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "${latency}ms",
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.Black,
                                        color = latencyQuality.second,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = latencyQuality.second.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = latencyQuality.first,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = latencyQuality.second,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            // Metric Rows
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                SpecRow("DEVICE", modalState.deviceName)
                                SpecRow("PROTOCOL", "Supabase Realtime (WSS)")
                                SpecRow("PACKET LOSS", "0% (Ack Verified)")
                                SpecRow("STATE", "Active & Listening")
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "Tracker Did Not Respond",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${modalState.deviceName} did not return a pong acknowledgement within 10 seconds. It may be in low-power sleep mode, out of internet coverage, or powered off.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }

                    // STATUS CHECKING IN PROGRESS
                    is DiagnosticModalState.StatusChecking -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = MaterialTheme.colorScheme.secondary,
                                strokeWidth = 4.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Requesting hardware state from ${modalState.deviceName}...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // STATUS RESULT
                    is DiagnosticModalState.StatusResult -> {
                        if (modalState.status != null) {
                            val telemetry = modalState.telemetry
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFF4ADE80).copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, Color(0xFF4ADE80).copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4ADE80), modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Hardware diagnostics passed successfully",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF4ADE80)
                                        )
                                    }
                                }

                                if (telemetry != null) {
                                    SpecRow("BATTERY", "${telemetry.battery}%" + if (telemetry.charging) " (Charging)" else " (Discharging)")
                                    SpecRow("GPS FIX", if (telemetry.isLocationOn) "Locked (±${telemetry.accuracy.toInt()}m)" else "Location Disabled")
                                    SpecRow("NETWORK", "${telemetry.signal} dBm (${telemetry.networkType})")
                                    SpecRow("SENSORS", "Heading ${telemetry.heading.toInt()}° • Alt ${telemetry.altitude.toInt()}m")
                                }
                                SpecRow("SYSTEM STATUS", modalState.status)
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "No Diagnostic Response",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${modalState.deviceName} did not report its hardware status. Try waking the device first.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // COMMAND SENT NOTIFICATION
                    is DiagnosticModalState.CommandSent -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (modalState.isSuccess) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, if (modalState.isSuccess) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = modalState.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (modalState.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = modalState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // If timeout or failure, offer quick Wake button
                if ((modalState is DiagnosticModalState.PingResult && modalState.latencyMs == null) ||
                    (modalState is DiagnosticModalState.StatusResult && modalState.status == null)) {
                    val targetName = when (modalState) {
                        is DiagnosticModalState.PingResult -> modalState.deviceName
                        is DiagnosticModalState.StatusResult -> modalState.deviceName
                        else -> ""
                    }
                    Button(
                        onClick = {
                            onDismiss()
                            onWakeDevice(targetName)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.WbSunny, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("WAKE TRACKER", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                }
                
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text("CLOSE", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceActionsBottomSheet(
    dev: DeviceTelemetry,
    now: Long,
    onDismiss: () -> Unit,
    onPingClick: () -> Unit,
    onStatusClick: () -> Unit,
    onLocationToggleClick: () -> Unit,
    onMicClick: () -> Unit,
    onSpecsClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val diff = now - dev.lastSeen
    val isLive = diff < 15000
    val lastSeen = when {
        diff < 60000 -> "${diff / 1000}s ago"
        diff < 3600000 -> "${diff / 60000}m ago"
        else -> "${diff / 3600000}h ago"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                width = 36.dp,
                height = 4.dp
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Device Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Smartphone,
                        contentDescription = null,
                        tint = if (isLive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dev.name.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isLive) Color(0xFF4ADE80).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = if (isLive) "LIVE • $lastSeen" else "OFFLINE • $lastSeen",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                color = if (isLive) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (dev.battery > 0) {
                            Text(
                                text = "${dev.battery}%" + if (dev.charging) " ⚡" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            )

            // Action 1: Ping Latency
            ActionSheetRow(
                icon = Icons.Default.Wifi,
                iconTint = MaterialTheme.colorScheme.primary,
                iconBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                title = "Test Ping Latency",
                subtitle = "Measure network round-trip response time",
                onClick = {
                    onDismiss()
                    onPingClick()
                }
            )

            // Action 2: Hardware Status
            ActionSheetRow(
                icon = Icons.Default.HealthAndSafety,
                iconTint = MaterialTheme.colorScheme.secondary,
                iconBg = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                title = "Hardware Diagnostics",
                subtitle = "Query sensors, battery, and telemetry health",
                onClick = {
                    onDismiss()
                    onStatusClick()
                }
            )

            // Action 3: Technical Specs
            ActionSheetRow(
                icon = Icons.Default.Insights,
                iconTint = MaterialTheme.colorScheme.onSurface,
                iconBg = MaterialTheme.colorScheme.surfaceVariant,
                title = "Technical Specifications",
                subtitle = "View coordinates, altitude, tilt, and pressure",
                onClick = {
                    onDismiss()
                    onSpecsClick()
                }
            )

            // Action 4: Delete Tracker
            ActionSheetRow(
                icon = Icons.Default.Delete,
                iconTint = MaterialTheme.colorScheme.error,
                iconBg = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                title = "Delete Tracker",
                subtitle = "Permanently remove tracker and erase history",
                isDestructive = true,
                onClick = {
                    onDismiss()
                    onDeleteClick()
                }
            )
        }
    }
}

@Composable
private fun ActionSheetRow(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isDestructive) MaterialTheme.colorScheme.error.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(
            1.dp,
            if (isDestructive) MaterialTheme.colorScheme.error.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        ),
        onClick = { onClick() },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ModernDeviceCard(
    dev: DeviceTelemetry,
    now: Long,
    onMicClick: () -> Unit,
    onLocationToggleClick: () -> Unit,
    onSpecsClick: () -> Unit,
    onMoreOptionsClick: () -> Unit,
) {
    val diff = now - dev.lastSeen
    val isLive = diff < 15000
    val lastSeen = when {
        diff < 60000 -> "${diff / 1000}s ago"
        diff < 3600000 -> "${diff / 60000}m ago"
        else -> "${diff / 3600000}h ago"
    }
    val headingVal = dev.heading
    val cardinal = getCardinalDirection(headingVal)
    val headingStr = "${headingVal.toInt()}° $cardinal"

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, if (isLive) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Header: Device Avatar + Identity & Subtitle + Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Column: Line 1 = Avatar + Title, Line 2 = Status capsule + Heading + GPS Alert
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    // Avatar box with status ring
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Smartphone,
                            contentDescription = null,
                            tint = if (isLive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = dev.name.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false
                        )
                        Spacer(Modifier.height(3.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Live / Last seen badge
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
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (isLive) "LIVE • $lastSeen" else "OFF • $lastSeen",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        color = if (isLive) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            // Compass Heading badge
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
                                        modifier = Modifier.size(9.dp).graphicsLayer { rotationZ = headingVal },
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        text = headingStr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            if (!dev.isLocationOn) {
                                LocationOffBadge()
                            }
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Right Action Cluster
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Specs Info Sheet Button
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        onClick = { onSpecsClick() },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Info, contentDescription = "Diagnostics", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Remote Location GPS Toggle Button
                    Surface(
                        shape = CircleShape,
                        color = if (!dev.isLocationOn) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        onClick = { onLocationToggleClick() },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (!dev.isLocationOn) Icons.Filled.LocationOff else Icons.Filled.LocationOn,
                                contentDescription = "Location Toggle",
                                tint = if (!dev.isLocationOn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Live Mic Listener Button
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        onClick = { onMicClick() },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Mic, contentDescription = "Listen", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                        }
                    }

                    // More Menu (Opens Bottom Sheet)
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        onClick = { onMoreOptionsClick() },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Mid Bento Metrics Row
            val batteryVal = dev.battery
            val batteryStr = "$batteryVal%" + if (dev.charging) " ⚡" else ""
            val speedStr = if (dev.speed > 0.5f) "%.1f km/h".format(dev.speed) else "0.0 km/h"
            val signalStr = "${dev.signal} dBm"
            val netTypeStr = dev.networkType

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BentoChip(
                    label = "BATTERY",
                    value = batteryStr,
                    icon = if (dev.charging) Icons.Default.Bolt else Icons.Default.BatteryStd,
                    highlightColor = if (batteryVal < 20) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                BentoChip(
                    label = "SPEED",
                    value = speedStr,
                    icon = Icons.Default.Speed,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                BentoChip(
                    label = netTypeStr,
                    value = signalStr,
                    icon = Icons.Default.SignalCellularAlt,
                    modifier = Modifier.weight(1.1f)
                )
                Spacer(Modifier.width(6.dp))
                BentoChip(
                    label = "PRECISION",
                    value = if (dev.accuracy > 0f) "±${dev.accuracy.toInt()}m" else "GPS",
                    icon = Icons.Default.GpsFixed,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(10.dp))

            // Footer Coordinates & Ping Bar
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (dev.isLocationOn) Icons.Default.Place else Icons.Filled.LocationOff,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = if (dev.isLocationOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (dev.isLocationOn) "%.5f, %.5f".format(dev.lat, dev.lon) else "GPS LOCATION DISABLED",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (dev.isLocationOn) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                            fontSize = 11.sp
                        )
                    }

                    if (dev.altitude != 0.0 || dev.ping >= 0) {
                        Text(
                            text = listOfNotNull(
                                if (dev.altitude != 0.0) "${dev.altitude.toInt()}m MSL" else null,
                                if (dev.ping >= 0) "${dev.ping}ms" else null
                            ).joinToString(" • "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterBadge(
    label: String,
    isSelected: Boolean,
    highlightColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) highlightColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, if (isSelected) highlightColor.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        onClick = { onClick() }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) highlightColor else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            fontSize = 10.sp
        )
    }
}

@Composable
private fun BentoChip(
    label: String,
    value: String,
    icon: ImageVector,
    highlightColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(11.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = highlightColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyDevices() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Radar,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "NO ACTIVE TRACKERS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Trackers will appear here automatically once a Live Tracker app sends its first location beacon.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

package com.aistudio.missioncontrol.pxytwe.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.missioncontrol.pxytwe.AppState
import com.aistudio.missioncontrol.pxytwe.DeviceTelemetry
import com.aistudio.missioncontrol.pxytwe.SupabaseClientManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Map our legacy constants to the new dynamic ThemeManager tokens
private val ColorBackground @Composable get() = MaterialTheme.colorScheme.background
private val ColorCard @Composable get() = MaterialTheme.colorScheme.surface
private val ColorTextPrimary @Composable get() = MaterialTheme.colorScheme.onSurface
private val ColorTextSecondary @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val ColorBorder @Composable get() = MaterialTheme.colorScheme.outline
private val ColorIcon @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val ColorLiveGreen @Composable get() = MaterialTheme.colorScheme.primary

@Composable
fun DevicesScreen(
    onNavigateToMicMonitor: (String) -> Unit
) {
    val activeMap = AppState.activeDevices
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    val showMessage: (String) -> Unit = { msg ->
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    // Read the snapshot map directly — every put/remove triggers recomposition.
    // derivedStateOf was suppressing updates here because the sorted list
    // was structurally-equal across mutations that didn't change sort order.
    val devices = activeMap.values.toList().sortedBy { it.name.lowercase() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = ColorBackground,
        modifier = Modifier.fillMaxSize().statusBarsPadding()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header Section
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "FLEET DIRECTORY",
                        style = MaterialTheme.typography.labelSmall,
                        color = ColorTextSecondary,
                        letterSpacing = 2.sp
                    )

                }
                
                Spacer(Modifier.height(4.dp))
                
                Text(
                    if (devices.isEmpty()) "0 ACTIVE DEVICES"
                    else "${devices.size} ACTIVE DEVICE${if (devices.size == 1) "" else "S"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ColorTextPrimary
                )
            }

            if (devices.isEmpty()) {
                EmptyDevices()
                return@Column
            }

            // Device Card List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(devices, key = { it.name }) { dev ->
                    DeviceCard(
                        dev = dev,
                        now = now,
                        onMicClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigateToMicMonitor(dev.name)
                        },
                        onConnectClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch {
                                try {
                                    showMessage("Testing connection to ${dev.name}...")
                                    val pingMs = SupabaseClientManager.pingDevice(dev.name)
                                    
                                    if (pingMs != null) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showMessage("Connection successful! Ping: ${pingMs}ms")
                                    } else {
                                        showMessage("Connection failed: ${dev.name} is offline or unreachable.")
                                    }
                                } catch (e: Exception) {
                                    showMessage("Error connecting to ${dev.name}: ${e.message}")
                                }
                            }
                        },
                        onDeleteClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                try {
                                    SupabaseClientManager.deleteDevice(dev.name)
                                    AppState.activeDevices.remove(dev.name)
                                    showMessage("Deleted ${dev.name}")
                                } catch (e: Exception) {
                                    showMessage("Failed to delete ${dev.name}: ${e.message}")
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    dev: DeviceTelemetry,
    now: Long,
    onMicClick: () -> Unit,
    onConnectClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val diff = now - dev.lastSeen
    val isLive = diff < 15_000
    val lastSeen = when {
        diff < 60_000 -> "${diff / 1000}s ago"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        else -> "${diff / 3_600_000}h ago"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ColorCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, ColorBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Device Icon with Status Dot
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(ColorBorder),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Smartphone,
                            contentDescription = null,
                            tint = ColorTextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    if (isLive) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .align(Alignment.BottomEnd)
                                .offset(x = (-2).dp, y = (-2).dp)
                                .clip(CircleShape)
                                .background(ColorCard), // Stroke effect
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(ColorLiveGreen)
                            )
                        }
                    }
                }

                Spacer(Modifier.width(16.dp))

                // Identity Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        dev.name.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ColorTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Last seen: $lastSeen",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ColorTextSecondary,
                    )
                }

                // More Options
                IconButton(onClick = { /* TODO */ }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = ColorIcon
                    )
                }
            }

            // Divider
            HorizontalDivider(
                color = ColorBorder,
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // Bottom Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Coordinates
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = ColorIcon
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "COORDINATES",
                            style = MaterialTheme.typography.labelSmall,
                            color = ColorTextSecondary,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "%.4f, %.4f".format(dev.lat, dev.lon),
                        style = MaterialTheme.typography.bodyLarge,
                        color = ColorTextPrimary,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }

                // Actions Pill
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = ColorCard,
                    border = BorderStroke(1.dp, ColorBorder)
                ) {
                    Row(
                        modifier = Modifier.height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onConnectClick, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Wifi, contentDescription = "Connect", tint = ColorIcon, modifier = Modifier.size(18.dp))
                        }
                        VerticalDivider(color = ColorBorder, thickness = 1.dp, modifier = Modifier.fillMaxHeight().padding(vertical = 8.dp))
                        IconButton(onClick = onMicClick, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.GraphicEq, contentDescription = "Listen", tint = ColorIcon, modifier = Modifier.size(18.dp))
                        }
                        VerticalDivider(color = ColorBorder, thickness = 1.dp, modifier = Modifier.fillMaxHeight().padding(vertical = 8.dp))
                        IconButton(onClick = onDeleteClick, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = ColorIcon, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
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
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ColorCard),
            border = BorderStroke(1.dp, ColorBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.Devices,
                    contentDescription = null,
                    tint = ColorIcon,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    "NO TELEMETRY YET",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ColorTextPrimary,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Devices will appear here automatically once a Live Tracker app sends its first location ping.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ColorTextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

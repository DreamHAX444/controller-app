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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import com.aistudio.missioncontrol.pxytwe.AppState
import com.aistudio.missioncontrol.pxytwe.DeviceTelemetry
import com.aistudio.missioncontrol.pxytwe.SupabaseClientManager
import com.aistudio.missioncontrol.pxytwe.audio.AudioMonitorRepository
import com.aistudio.missioncontrol.pxytwe.audio.AudioMonitorRepository.MonitorStatus
import com.aistudio.missioncontrol.pxytwe.audio.AudioMonitorViewModel
import com.aistudio.missioncontrol.pxytwe.ui.components.LocationOffBadge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

private enum class MicFilter {
    ALL, STREAMING, ONLINE
}

@Composable
fun MicHomeScreen(
    onNavigateToMicMonitor: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val vm = remember {
        AudioMonitorViewModel(context.applicationContext as android.app.Application)
    }

    val activeDeviceId by AudioMonitorRepository.activeSession.collectAsState()
    val monitorStatus by AudioMonitorRepository.status.collectAsState()
    
    val activeMap = AppState.activeDevices
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(MicFilter.ALL) }
    
    var selectedAudioSpecsDevice by remember { mutableStateOf<DeviceTelemetry?>(null) }
    var activeDiagnosticModal by remember { mutableStateOf<DiagnosticModalState?>(null) }
    var topBannerMessage by remember { mutableStateOf<String?>(null) }

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
    val isStreamingActive = activeDeviceId != null && monitorStatus == MonitorStatus.Live

    val filteredDevices = remember(allDevices, searchQuery, selectedFilter, activeDeviceId, isStreamingActive) {
        val currentNow = System.currentTimeMillis()
        allDevices.filter { dev ->
            val matchesSearch = searchQuery.isBlank() || dev.name.contains(searchQuery.trim(), ignoreCase = true)
            val isLive = currentNow - dev.lastSeen < 15000
            val isStreaming = activeDeviceId == dev.name && isStreamingActive
            val matchesFilter = when (selectedFilter) {
                MicFilter.ALL -> true
                MicFilter.STREAMING -> isStreaming
                MicFilter.ONLINE -> isLive
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
                            text = "ACOUSTIC SURVEILLANCE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (isStreamingActive) "AUDIO UPLINK ACTIVE" else "MIC CONTROLLER",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = if (isStreamingActive) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // Live Stream Status Pill
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        border = BorderStroke(
                            1.dp,
                            if (isStreamingActive) Color(0xFF4ADE80).copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        ),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val transition = rememberInfiniteTransition(label = "mic_live_dot")
                            val pulseAlpha by transition.animateFloat(
                                initialValue = 0.4f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(animation = tween(1000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
                                label = "pulse_mic_alpha"
                            )
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .graphicsLayer { alpha = if (isStreamingActive) pulseAlpha else 1f }
                                    .clip(CircleShape)
                                    .background(
                                        if (isStreamingActive) Color(0xFF4ADE80)
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isStreamingActive) "STREAMING" else "STANDBY",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isStreamingActive) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search audio targets by name...", style = MaterialTheme.typography.bodySmall) },
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
                        isSelected = selectedFilter == MicFilter.ALL,
                        onClick = { selectedFilter = MicFilter.ALL }
                    )
                    if (isStreamingActive) {
                        FilterBadge(
                            label = "STREAMING (1)",
                            isSelected = selectedFilter == MicFilter.STREAMING,
                            highlightColor = Color(0xFF4ADE80),
                            onClick = { selectedFilter = MicFilter.STREAMING }
                        )
                    }
                    FilterBadge(
                        label = "ONLINE (${allDevices.count { now - it.lastSeen < 15000 }})",
                        isSelected = selectedFilter == MicFilter.ONLINE,
                        onClick = { selectedFilter = MicFilter.ONLINE }
                    )
                }
            }

            if (allDevices.isEmpty()) {
                EmptyMicState()
            } else if (filteredDevices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No audio targets match the current filter or search.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Active Streaming Hero Banner
                    if (activeDeviceId != null) {
                        item(key = "active_hero_banner") {
                            ActiveStreamingHeroCard(
                                deviceId = activeDeviceId!!,
                                status = monitorStatus,
                                onOpenConsole = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onNavigateToMicMonitor(activeDeviceId!!)
                                },
                                onStop = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    scope.launch {
                                        vm.stop()
                                        showTopAlert("Audio session closed on $activeDeviceId")
                                    }
                                }
                            )
                        }
                    }

                    // Device Mic Cards
                    items(filteredDevices, key = { it.name }) { dev ->
                        val isThisDeviceActive = activeDeviceId == dev.name
                        ModernMicCard(
                            dev = dev,
                            now = now,
                            isActive = isThisDeviceActive,
                            status = if (isThisDeviceActive) monitorStatus else null,
                            onTogglePower = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch {
                                    if (isThisDeviceActive) {
                                        vm.stop()
                                        showTopAlert("Stopped listening to ${dev.name}")
                                    } else {
                                        vm.start(dev.name)
                                        showTopAlert("Initiating live audio feed on ${dev.name}...")
                                    }
                                }
                            },
                            onOpenConsole = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNavigateToMicMonitor(dev.name)
                            },
                            onAudioSpecsClick = {
                                selectedAudioSpecsDevice = dev
                            },
                            onTestPingClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                activeDiagnosticModal = DiagnosticModalState.PingTesting(dev.name)
                                scope.launch {
                                    try {
                                        val pingMs = SupabaseClientManager.pingDevice(dev.name)
                                        activeDiagnosticModal = DiagnosticModalState.PingResult(
                                            deviceName = dev.name,
                                            latencyMs = pingMs,
                                            errorMsg = if (pingMs == null) "Audio pipeline probe timed out." else null
                                        )
                                    } catch (e: Exception) {
                                        activeDiagnosticModal = DiagnosticModalState.PingResult(
                                            deviceName = dev.name,
                                            latencyMs = null,
                                            errorMsg = "Probe error: ${e.message}"
                                        )
                                    }
                                }
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
                        Icons.Default.GraphicEq,
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

    // Audio Codec & Pipeline Specifications Dialog
    if (selectedAudioSpecsDevice != null) {
        val dev = selectedAudioSpecsDevice!!
        val isDevActive = activeDeviceId == dev.name && isStreamingActive
        AudioSpecsDialog(
            telemetry = dev,
            isStreaming = isDevActive,
            onDismiss = { selectedAudioSpecsDevice = null }
        )
    }

    // Diagnostic Output Modal for Audio Ping
    if (activeDiagnosticModal != null) {
        DiagnosticOutputModal(
            modalState = activeDiagnosticModal!!,
            onDismiss = { activeDiagnosticModal = null },
            onWakeDevice = { deviceName ->
                scope.launch {
                    SupabaseClientManager.sendWakeCommand(deviceName)
                    showTopAlert("Wake signal dispatched to $deviceName")
                }
            }
        )
    }
}

@Composable
fun AudioSpecsDialog(
    telemetry: DeviceTelemetry,
    isStreaming: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "${telemetry.name.uppercase()} AUDIO SPECS",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SpecRow("TARGET TRACKER", telemetry.name)
                SpecRow("AUDIO ENCODING", "PCM 16-bit Mono (LE)")
                SpecRow("SAMPLE RATE", "8,000 Hz (Voice Optimized)")
                SpecRow("STREAM CHANNEL", "Supabase Broadcast (Inline B64)")
                SpecRow("JITTER BUFFER", "4 Frames (~1000ms Tolerance)")
                SpecRow("LATENCY", if (telemetry.ping >= 0) "${telemetry.ping} ms" else "Realtime Active")
                SpecRow("CELLULAR LINK", "${telemetry.signal} dBm • ${telemetry.networkType}")
                SpecRow("BATTERY LEVEL", "${telemetry.battery}%" + if (telemetry.charging) " (Charging)" else "")
                SpecRow("SESSION STATE", if (isStreaming) "Active Transmission" else "Standby / Ready")
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text("CLOSE", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        }
    )
}

@Composable
private fun ActiveStreamingHeroCard(
    deviceId: String,
    status: MonitorStatus,
    onOpenConsole: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, Color(0xFF4ADE80).copy(alpha = 0.45f)),
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4ADE80).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = Color(0xFF4ADE80),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "LIVE AUDIO BROADCAST",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4ADE80),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = deviceId.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Animated Equalizer Bars
                AnimatedEqualizerBars()
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenConsole,
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Insights, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("WAVEFORM CONSOLE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onStop,
                    modifier = Modifier.height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("DISCONNECT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AnimatedEqualizerBars() {
    val infiniteTransition = rememberInfiniteTransition(label = "eq_bars")
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse), label = "b1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "b2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(280, easing = LinearEasing), RepeatMode.Reverse), label = "b3"
    )
    val bar4 by infiniteTransition.animateFloat(
        initialValue = 0.25f, targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(480, easing = LinearEasing), RepeatMode.Reverse), label = "b4"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(24.dp).padding(end = 4.dp)
    ) {
        val bars = listOf(bar1, bar2, bar3, bar4)
        bars.forEach { fraction ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF4ADE80))
            )
        }
    }
}

@Composable
private fun ModernMicCard(
    dev: DeviceTelemetry,
    now: Long,
    isActive: Boolean,
    status: MonitorStatus?,
    onTogglePower: () -> Unit,
    onOpenConsole: () -> Unit,
    onAudioSpecsClick: () -> Unit,
    onTestPingClick: () -> Unit,
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
        border = BorderStroke(
            1.dp,
            if (isActive) Color(0xFF4ADE80).copy(alpha = 0.6f)
            else if (isLive) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        shadowElevation = if (isActive) 8.dp else 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Avatar + Title & Badges + Unique Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Identity
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) Color(0xFF4ADE80).copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isActive) Icons.Default.GraphicEq else Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (isActive) Color(0xFF4ADE80) else if (isLive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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

                // Distinct Header Actions: Audio Pipeline Ping Probe & Codec Diagnostics
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Button: Audio Ping Probe
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(34.dp).clickable { onTestPingClick() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Wifi, contentDescription = "Audio Ping", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Button: Audio Codec & Jitter Specs Sheet
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(34.dp).clickable { onAudioSpecsClick() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Info, contentDescription = "Audio Specs", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Mid Bento Metrics Row
            val batteryVal = dev.battery
            val batteryStr = "$batteryVal%" + if (dev.charging) " ⚡" else ""
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
                    label = netTypeStr,
                    value = signalStr,
                    icon = Icons.Default.SignalCellularAlt,
                    modifier = Modifier.weight(1.1f)
                )
                Spacer(Modifier.width(6.dp))
                BentoChip(
                    label = "MIC STATE",
                    value = if (isActive) status?.name ?: "ACTIVE" else "STANDBY",
                    icon = if (isActive) Icons.Default.GraphicEq else Icons.Default.MicNone,
                    highlightColor = if (isActive) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1.1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            // Bottom Action Strip: 1-Tap Live Uplink + Dedicated Visualizer Console
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Button 1: Start / Stop Live Audio Transmission
                Button(
                    onClick = onTogglePower,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (isActive) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isActive) "STOP LISTENING" else "START AUDIO UPLINK",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Button 2: Open Dedicated Waveform & Oscilloscope Console
                OutlinedButton(
                    onClick = onOpenConsole,
                    modifier = Modifier.height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Icon(Icons.Default.Insights, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "CONSOLE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
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
private fun EmptyMicState() {
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
                        Icons.Default.MicOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "NO TRACKERS CONNECTED",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Once an active Live Tracker connects and sends telemetry, audio listening streams can be initiated here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

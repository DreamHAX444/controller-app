package com.aistudio.missioncontrol.pxytwe.ui.screens
import androidx.compose.ui.graphics.graphicsLayer


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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

private enum class SignalFilter {
    ALL, LIVE, ASLEEP, GPS_OFF
}

@Composable
fun SignalsScreen() {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val activeDevices = AppState.activeDevices
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(SignalFilter.ALL) }
    
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



    val allDevices = activeDevices.values.toList().sortedBy { it.name.lowercase() }
    val liveCount = allDevices.count { now - it.lastSeen < 15000 }
    val asleepCount = allDevices.size - liveCount
    val gpsOffCount = allDevices.count { !it.isLocationOn }
    val anyAwake = liveCount > 0

    val filteredDevices = allDevices.filter { dev ->
        val matchesSearch = searchQuery.isBlank() || dev.name.contains(searchQuery.trim(), ignoreCase = true)
        val isLive = now - dev.lastSeen < 15000
        val matchesFilter = when (selectedFilter) {
            SignalFilter.ALL -> true
            SignalFilter.LIVE -> isLive
            SignalFilter.ASLEEP -> !isLive
            SignalFilter.GPS_OFF -> !dev.isLocationOn
        }
        matchesSearch && matchesFilter
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
                            text = "COMMAND CENTER",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "FLEET SIGNALS",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // Live Fleet Pill
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        border = BorderStroke(
                            1.dp,
                            if (anyAwake) Color(0xFF4ADE80).copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        ),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val transition = rememberInfiniteTransition(label = "signal_live_dot")
                            val pulseAlpha by transition.animateFloat(
                                initialValue = 0.4f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(animation = tween(1000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
                                label = "pulse_signal_alpha"
                            )
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (anyAwake) Color(0xFF4ADE80).copy(alpha = pulseAlpha)
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (anyAwake) "$liveCount AWAKE" else "ALL ASLEEP",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (anyAwake) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurfaceVariant,
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
                    placeholder = { Text("Search trackers to send commands...", style = MaterialTheme.typography.bodySmall) },
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
                    SignalFilterChip(
                        label = "ALL (${allDevices.size})",
                        isSelected = selectedFilter == SignalFilter.ALL,
                        onClick = { selectedFilter = SignalFilter.ALL }
                    )
                    SignalFilterChip(
                        label = "LIVE ($liveCount)",
                        isSelected = selectedFilter == SignalFilter.LIVE,
                        highlightColor = Color(0xFF4ADE80),
                        onClick = { selectedFilter = SignalFilter.LIVE }
                    )
                    SignalFilterChip(
                        label = "ASLEEP ($asleepCount)",
                        isSelected = selectedFilter == SignalFilter.ASLEEP,
                        onClick = { selectedFilter = SignalFilter.ASLEEP }
                    )
                    if (gpsOffCount > 0) {
                        SignalFilterChip(
                            label = "GPS ALERT ($gpsOffCount)",
                            isSelected = selectedFilter == SignalFilter.GPS_OFF,
                            highlightColor = MaterialTheme.colorScheme.error,
                            onClick = { selectedFilter = SignalFilter.GPS_OFF }
                        )
                    }
                }
            }

            if (allDevices.isEmpty()) {
                EmptySignalsState()
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Global Fleet-Wide Hero Command Card
                    item(key = "fleet_command_hero") {
                        FleetCommandCenterHero(
                            anyAwake = anyAwake,
                            liveCount = liveCount,
                            totalCount = allDevices.size,
                            onWakeAll = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch {
                                    allDevices.forEach { SupabaseClientManager.sendWakeCommand(it.name) }
                                    showTopAlert("Broadcast: Wake signal dispatched to all ${allDevices.size} trackers")
                                }
                            },
                            onSleepAll = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch {
                                    allDevices.forEach { SupabaseClientManager.sendSleepCommand(it.name) }
                                    showTopAlert("Broadcast: Sleep signal dispatched to all ${allDevices.size} trackers")
                                }
                            },
                            onAutoHealAll = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch {
                                    allDevices.forEach { SupabaseClientManager.sendAutoHealCommand(it.name) }
                                    showTopAlert("Broadcast: Auto-heal signal dispatched to all ${allDevices.size} trackers")
                                }
                            }
                        )
                    }

                    // Section Divider Label
                    item(key = "section_label") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TARGETED REMOTE CONTROLS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${filteredDevices.size} TARGETS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Individual Tracker Bento Command Cards
                    items(filteredDevices, key = { it.name }) { dev ->
                        val isLive = now - dev.lastSeen < 15000
                        ModernSignalCard(
                            dev = dev,
                            now = now,
                            isLive = isLive,
                            onWake = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch {
                                    SupabaseClientManager.sendWakeCommand(dev.name)
                                    showTopAlert("Wake signal sent to ${dev.name}")
                                }
                            },
                            onSleep = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch {
                                    SupabaseClientManager.sendSleepCommand(dev.name)
                                    showTopAlert("Sleep signal sent to ${dev.name}")
                                }
                            },
                            onToggleLocation = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch {
                                    SupabaseClientManager.sendEnableLocationCommand(dev.name)
                                    showTopAlert("GPS turn-on command dispatched to ${dev.name}")
                                }
                            },
                            onPing = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                activeDiagnosticModal = DiagnosticModalState.PingTesting(dev.name)
                                scope.launch {
                                    try {
                                        val latency = SupabaseClientManager.pingDevice(dev.name)
                                        activeDiagnosticModal = DiagnosticModalState.PingResult(
                                            deviceName = dev.name,
                                            latencyMs = latency,
                                            errorMsg = if (latency == null) "Tracker did not acknowledge ping within 10s." else null
                                        )
                                    } catch (e: Exception) {
                                        activeDiagnosticModal = DiagnosticModalState.PingResult(
                                            deviceName = dev.name,
                                            latencyMs = null,
                                            errorMsg = "Ping error: ${e.message}"
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
                        Icons.Default.Sensors,
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

    // Diagnostic Latency Modal
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
}

@Composable
private fun FleetCommandCenterHero(
    anyAwake: Boolean,
    liveCount: Int,
    totalCount: Int,
    onWakeAll: () -> Unit,
    onSleepAll: () -> Unit,
    onAutoHealAll: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "fleet_hero_pulse")
    val pulseScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (anyAwake) 1.01f else 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "hero_scale"
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(
            1.dp,
            if (anyAwake) Color(0xFF4ADE80).copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        ),
        shadowElevation = if (anyAwake) 8.dp else 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            if (anyAwake) Color(0xFF4ADE80).copy(alpha = 0.08f) else Color.Transparent,
                            Color.Transparent
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Header status row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (anyAwake) Color(0xFF4ADE80).copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (anyAwake) Icons.Default.Sensors else Icons.Default.SensorsOff,
                                contentDescription = null,
                                tint = if (anyAwake) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "GLOBAL FLEET BROADCAST",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (anyAwake) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = if (anyAwake) "$liveCount / $totalCount TRACKERS ACTIVE" else "ALL $totalCount TRACKERS ASLEEP",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // WSS Channel Tag
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "public:commands",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Primary Command Broadcast Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // WAKE ALL BUTTON
                    Button(
                        onClick = onWakeAll,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.WbSunny, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "WAKE ALL",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // SLEEP ALL BUTTON
                    Button(
                        onClick = onSleepAll,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SLEEP ALL",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // AUTO-HEAL ALL BUTTON
                    OutlinedButton(
                        onClick = onAutoHealAll,
                        modifier = Modifier.height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "AUTO-HEAL",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernSignalCard(
    dev: DeviceTelemetry,
    now: Long,
    isLive: Boolean,
    onWake: () -> Unit,
    onSleep: () -> Unit,
    onToggleLocation: () -> Unit,
    onPing: () -> Unit
) {
    val diff = now - dev.lastSeen
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
            if (isLive) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Avatar + Device Name & Subtitle Badges + Fast Ping Button
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
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isLive) Icons.Default.Sensors else Icons.Default.SensorsOff,
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
                                        text = if (isLive) "LIVE • $lastSeen" else "ASLEEP • $lastSeen",
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

                // Ping Latency Probe Button
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    onClick = { onPing() },
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Wifi, contentDescription = "Ping", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                SignalBentoChip(
                    label = "BATTERY",
                    value = batteryStr,
                    icon = if (dev.charging) Icons.Default.Bolt else Icons.Default.BatteryStd,
                    highlightColor = if (batteryVal < 20) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                SignalBentoChip(
                    label = "SPEED",
                    value = speedStr,
                    icon = Icons.Default.Speed,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                SignalBentoChip(
                    label = netTypeStr,
                    value = signalStr,
                    icon = Icons.Default.SignalCellularAlt,
                    modifier = Modifier.weight(1.1f)
                )
                Spacer(Modifier.width(6.dp))
                SignalBentoChip(
                    label = "PRECISION",
                    value = if (dev.accuracy > 0f) "±${dev.accuracy.toInt()}m" else "GPS",
                    icon = Icons.Default.GpsFixed,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            // Bottom Action Strip: 1-Tap Wake/Sleep & GPS Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Button 1: Wake / Sleep Command
                Button(
                    onClick = if (isLive) onSleep else onWake,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                        contentColor = if (isLive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (isLive) Icons.Default.PowerSettingsNew else Icons.Default.WbSunny,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isLive) "ENTER SLEEP" else "WAKE TRACKER",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Button 2: Remote GPS Turn-On Toggle
                OutlinedButton(
                    onClick = onToggleLocation,
                    modifier = Modifier.height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (!dev.isLocationOn) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (!dev.isLocationOn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector = if (dev.isLocationOn) Icons.Default.LocationOn else Icons.Default.LocationOff,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (dev.isLocationOn) "GPS ON" else "TURN GPS ON",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SignalBentoChip(
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
private fun SignalFilterChip(
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
private fun EmptySignalsState() {
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
                        Icons.Default.SensorsOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "NO NETWORK TARGETS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Trackers will appear here once connected to the Supabase Realtime broadcast channel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

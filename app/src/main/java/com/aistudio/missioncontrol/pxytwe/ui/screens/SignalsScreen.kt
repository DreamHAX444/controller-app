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
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SensorsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
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

private val ColorBackground @Composable get() = MaterialTheme.colorScheme.background
private val ColorCard @Composable get() = MaterialTheme.colorScheme.surface
private val ColorTextPrimary @Composable get() = MaterialTheme.colorScheme.onSurface
private val ColorTextSecondary @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val ColorBorder @Composable get() = MaterialTheme.colorScheme.outline
private val ColorIcon @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val ColorLiveGreen @Composable get() = MaterialTheme.colorScheme.primary

@Composable
fun SignalsScreen() {
    val scope = rememberCoroutineScope()
    val activeDevices = AppState.activeDevices
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
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
            currentTime = System.currentTimeMillis()
        }
    }

    val anyAwake by remember(activeDevices.values.toList(), currentTime) {
        derivedStateOf {
            activeDevices.values.any { currentTime - it.lastSeen < 15_000 }
        }
    }

    val targets = activeDevices.values
        .sortedByDescending { it.lastSeen }

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
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Text(
                    text = "COMMAND CENTER",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorTextSecondary,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Fleet Signals",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ColorTextPrimary
                )
            }
            
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                HeroFleetCard(
                    anyAwake = anyAwake,
                    hasTargets = targets.isNotEmpty(),
                    onWakeAll = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMessage("Waking all ${targets.size} trackers...")
                        scope.launch { targets.forEach { SupabaseClientManager.sendWakeCommand(it.name) } }
                    },
                    onSleepAll = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMessage("Putting all ${targets.size} trackers to sleep...")
                        scope.launch { targets.forEach { SupabaseClientManager.sendSleepCommand(it.name) } }
                    }
                )
                
                Spacer(Modifier.height(32.dp))

                Text(
                    "INDIVIDUAL TRACKERS",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorTextSecondary,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(Modifier.height(12.dp))
            }

            if (targets.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No trackers available on the network.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ColorTextSecondary,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(targets, key = { it.name }) { dev ->
                        TrackerSignalCard(
                            device = dev,
                            currentTime = currentTime,
                            onWake = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showMessage("Waking ${dev.name}...")
                                scope.launch { SupabaseClientManager.sendWakeCommand(dev.name) }
                            },
                            onSleep = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showMessage("Sleeping ${dev.name}...")
                                scope.launch { SupabaseClientManager.sendSleepCommand(dev.name) }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HeroFleetCard(
    anyAwake: Boolean,
    hasTargets: Boolean,
    onWakeAll: () -> Unit,
    onSleepAll: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "hero_pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (anyAwake) 1.02f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val glowColor = if (anyAwake) ColorLiveGreen.copy(alpha = 0.2f) else Color.Transparent

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = ColorCard),
        border = BorderStroke(1.dp, ColorBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(glowColor, Color.Transparent)
                    )
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(if (anyAwake) ColorLiveGreen.copy(alpha = 0.1f) else ColorBorder),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (anyAwake) Icons.Filled.Sensors else Icons.Filled.SensorsOff,
                        contentDescription = null,
                        tint = if (anyAwake) ColorLiveGreen else ColorIcon,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (anyAwake) "FLEET IS ACTIVE" else "FLEET IS ASLEEP",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = ColorTextPrimary,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (anyAwake) "Telemetry is streaming live." else "Trackers are in standby mode.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ColorTextSecondary
                )
                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = onWakeAll,
                        enabled = hasTargets,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Filled.WbSunny, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(text = "WAKE ALL", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onSleepAll,
                        enabled = hasTargets,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(text = "SLEEP ALL", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun TrackerSignalCard(
    device: DeviceTelemetry,
    currentTime: Long,
    onWake: () -> Unit,
    onSleep: () -> Unit
) {
    val diff = currentTime - device.lastSeen
    val isLive = diff < 15_000
    val lastSeenStr = when {
        diff < 60_000 -> "${diff / 1000}s ago"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        else -> "${diff / 3_600_000}h ago"
    }

    val transition = rememberInfiniteTransition(label = "dot_pulse")
    val dotAlpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = if (isLive) 1f else 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ColorCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, ColorBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                            if (isLive) Icons.Filled.Sensors else Icons.Filled.SensorsOff,
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
                                    .background(ColorLiveGreen.copy(alpha = dotAlpha))
                            )
                        }
                    }
                }
                
                Spacer(Modifier.size(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ColorTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Last seen: $lastSeenStr",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isLive) ColorLiveGreen else ColorTextSecondary,
                        fontWeight = if (isLive) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
                
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = ColorCard,
                    border = BorderStroke(1.dp, ColorBorder)
                ) {
                    Row(
                        modifier = Modifier.height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onWake, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Filled.WbSunny, contentDescription = "Wake", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        VerticalDivider(color = ColorBorder, thickness = 1.dp, modifier = Modifier.fillMaxHeight().padding(vertical = 8.dp))
                        IconButton(onClick = onSleep, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Filled.PowerSettingsNew, contentDescription = "Sleep", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

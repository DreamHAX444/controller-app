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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.missioncontrol.pxytwe.AppState
import com.aistudio.missioncontrol.pxytwe.DeviceTelemetry
import com.aistudio.missioncontrol.pxytwe.SupabaseClientManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize().statusBarsPadding()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                "COMMAND CENTER",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(12.dp))

            if (targets.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No trackers available on the network.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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

    val glowColor = if (anyAwake) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (anyAwake) 12.dp else 4.dp
        )
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
                Icon(
                    imageVector = if (anyAwake) Icons.Filled.Sensors else Icons.Filled.SensorsOff,
                    contentDescription = null,
                    tint = if (anyAwake) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (anyAwake) "FLEET IS ACTIVE" else "FLEET IS ASLEEP",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (anyAwake) "Telemetry is streaming live." else "Trackers are in standby mode.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = if (anyAwake) onSleepAll else onWakeAll,
                    enabled = hasTargets,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (anyAwake) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (anyAwake) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = if (anyAwake) Icons.Filled.PowerSettingsNew else Icons.Filled.WbSunny,
                        contentDescription = null
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = if (anyAwake) "SLEEP ALL TRACKERS" else "WAKE ALL TRACKERS",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        if (isLive) MaterialTheme.colorScheme.primary.copy(alpha = dotAlpha)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
            )
            Spacer(Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Last seen: $lastSeenStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isLive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isLive) FontWeight.SemiBold else FontWeight.Normal
                )
            }
            
            FilledTonalIconButton(
                onClick = onWake,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.Filled.WbSunny, contentDescription = "Wake", modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.size(8.dp))
            FilledTonalIconButton(
                onClick = onSleep,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.Filled.PowerSettingsNew, contentDescription = "Sleep", modifier = Modifier.size(20.dp))
            }
        }
    }
}

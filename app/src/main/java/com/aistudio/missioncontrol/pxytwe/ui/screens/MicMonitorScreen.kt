package com.aistudio.missioncontrol.pxytwe.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.missioncontrol.pxytwe.audio.AudioMonitorRepository.MonitorStatus
import com.aistudio.missioncontrol.pxytwe.audio.AudioMonitorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MicMonitorScreen(
    deviceId: String,
    onBack: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val vm: AudioMonitorViewModel = viewModel(factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
        androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application
    ))
    val ui by vm.ui.collectAsStateWithLifecycle()

    var confirmExit by remember { mutableStateOf(false) }

    LaunchedEffect(deviceId) {
        if (!ui.isMonitoring) vm.start(deviceId)
    }
    DisposableEffect(Unit) {
        onDispose {
            vm.stop(sendCommand = true)
        }
    }

    BackHandler(enabled = ui.isMonitoring) {
        confirmExit = true
    }
    BackHandler(enabled = !ui.isMonitoring) {
        onBack()
    }

    if (confirmExit) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(24.dp),
            onDismissRequest = { confirmExit = false },
            title = { Text("Stop Audio Monitoring?", fontWeight = FontWeight.Bold) },
            text = { Text("The microphone on '${deviceId}' is currently streaming live. Terminate audio stream and exit?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmExit = false
                        vm.stop(sendCommand = true)
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("STOP & EXIT", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false }) { Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = deviceId.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val statusColor = when (ui.status) {
                                MonitorStatus.Live -> Color(0xFF4ADE80)
                                MonitorStatus.Failed -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.primary
                            }
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(statusColor))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = ui.statusMessage ?: ui.status.name.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (ui.isMonitoring) confirmExit = true else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Oscilloscope Waveform Visualizer
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                border = BorderStroke(
                    1.dp,
                    if (ui.isMonitoring) Color(0xFF4ADE80).copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    if (!ui.isMonitoring) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.MicOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "ACOUSTIC STREAM IDLE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                letterSpacing = 2.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (ui.peakHistory.isEmpty()) {
                        CircularProgressIndicator(
                            color = Color(0xFF4ADE80),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                    } else {
                        WaveformGraph(ui.peakHistory)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bento Telemetry Chips Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MonitorStatChip(Modifier.weight(1f), "RX CHUNKS", "${ui.chunksReceived}")
                MonitorStatChip(Modifier.weight(1f), "DECODED", "${ui.chunksDecoded}")
                MonitorStatChip(Modifier.weight(1f), "PEAK", "%.0f%%".format(ui.peak * 100f))
                MonitorStatChip(Modifier.weight(1f), "RMS", "%.0f%%".format(ui.rms * 100f))
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Central Biometric Audio Power Button with Pulsing Radar
            Surface(
                shape = CircleShape,
                color = if (ui.isMonitoring) MaterialTheme.colorScheme.error.copy(alpha = 0.15f) else Color(0xFF4ADE80).copy(alpha = 0.15f),
                border = BorderStroke(
                    2.dp,
                    if (ui.isMonitoring) MaterialTheme.colorScheme.error else Color(0xFF4ADE80)
                ),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (ui.isMonitoring) vm.stop() else vm.start(deviceId)
                },
                enabled = ui.status != MonitorStatus.Starting,
                modifier = Modifier.size(88.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val icon = if (ui.isMonitoring) Icons.Default.Stop else Icons.Default.Mic
                    val tint = if (ui.isMonitoring) MaterialTheme.colorScheme.error else Color(0xFF4ADE80)

                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(36.dp)
                    )

                    if (ui.isMonitoring) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse_radar")
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 1f, targetValue = 1.35f,
                            animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "s"
                        )
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 0.45f, targetValue = 0f,
                            animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "a"
                        )
                        Canvas(modifier = Modifier.size(88.dp)) {
                            drawCircle(color = tint, radius = (size.minDimension / 2) * scale, alpha = alpha)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = when (ui.status) {
                    MonitorStatus.Starting -> "CONNECTING UPLINK…"
                    MonitorStatus.Live -> "TAP TO STOP MONITORING"
                    MonitorStatus.Stopping -> "STOPPING STREAM…"
                    MonitorStatus.Failed -> "FAILED — TAP TO RETRY"
                    MonitorStatus.Idle -> "TAP TO START MIC UPLINK"
                },
                style = MaterialTheme.typography.labelMedium,
                color = when (ui.status) {
                    MonitorStatus.Failed -> MaterialTheme.colorScheme.error
                    MonitorStatus.Live -> Color(0xFF4ADE80)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // System Console Terminal Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "SYSTEM CONSOLE LOG",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${ui.logs.size} ENTRIES",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Console Log Terminal Box
            val listState = rememberLazyListState()
            LaunchedEffect(ui.logs.size) {
                if (ui.logs.isNotEmpty()) listState.animateScrollToItem(ui.logs.size - 1)
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    items(ui.logs) { log ->
                        val color = when (log.kind) {
                            AudioMonitorViewModel.LogEntry.Kind.Good -> Color(0xFF4ADE80)
                            AudioMonitorViewModel.LogEntry.Kind.Warn -> Color(0xFFF59E0B)
                            AudioMonitorViewModel.LogEntry.Kind.Error -> MaterialTheme.colorScheme.error
                            AudioMonitorViewModel.LogEntry.Kind.Info -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            text = "[${log.time}] ${log.text}",
                            style = MaterialTheme.typography.bodySmall,
                            color = color,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonitorStatChip(modifier: Modifier = Modifier, label: String, value: String) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
                fontSize = 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun WaveformGraph(amplitudes: List<Float>) {
    val primaryColor = Color(0xFF4ADE80)
    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp)) {
        val width = size.width
        val height = size.height
        val centerY = height / 2
        val barWidth = 4.dp.toPx()
        val spacing = 3.dp.toPx()
        val maxBars = (width / (barWidth + spacing)).toInt()

        val visibleAmplitudes = amplitudes.takeLast(maxBars)

        visibleAmplitudes.forEachIndexed { index, amp ->
            val x = width - (visibleAmplitudes.size - index) * (barWidth + spacing)
            val barHeight = (amp * height).coerceAtLeast(4.dp.toPx())

            drawLine(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.4f),
                        primaryColor,
                        primaryColor.copy(alpha = 0.4f)
                    )
                ),
                start = Offset(x, centerY - barHeight / 2),
                end = Offset(x, centerY + barHeight / 2),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

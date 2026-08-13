package com.aistudio.missioncontrol.pxytwe.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.StrokeCap
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
            vm.stop(sendCommand = false)
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
            onDismissRequest = { confirmExit = false },
            title = { Text("Stop monitoring?") },
            text = { Text("The tracker's microphone is currently streaming. Stop the uplink and leave?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmExit = false
                        vm.stop(sendCommand = true)
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("STOP & LEAVE") }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false }) { Text("STAY", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = {
                        if (ui.isMonitoring) confirmExit = true else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize().statusBarsPadding()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "MIC MONITOR",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 2.sp
                    )
                    StatusRow(ui.status, ui.statusMessage)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    deviceId.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Waveform Visualizer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!ui.isMonitoring) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.MicOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "SENSORS OFFLINE",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                letterSpacing = 2.sp
                            )
                        }
                    } else if (ui.peakHistory.isEmpty()) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(40.dp)
                        )
                    } else {
                        WaveformGraph(ui.peakHistory)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Stats strip
                AnimatedVisibility(visible = ui.isMonitoring) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatChip(Modifier.weight(1f), "RX", ui.chunksReceived.toString())
                        StatChip(Modifier.weight(1f), "DECODED", ui.chunksDecoded.toString())
                        StatChip(Modifier.weight(1f), "PEAK", "%.0f%%".format(ui.peak * 100f))
                        StatChip(Modifier.weight(1f), "RMS", "%.0f%%".format(ui.rms * 100f))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Control Button
                Surface(
                    shape = CircleShape,
                    color = if (ui.isMonitoring) MaterialTheme.colorScheme.error.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    border = BorderStroke(
                        2.dp,
                        if (ui.isMonitoring) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .size(100.dp)
                        .clickable(enabled = ui.status != MonitorStatus.Starting) {
                            if (ui.isMonitoring) vm.stop() else vm.start(deviceId)
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val icon = if (ui.isMonitoring) Icons.Default.Stop else Icons.Default.Mic
                        val tint = if (ui.isMonitoring) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        val disabledTint = tint.copy(alpha = 0.4f)

                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (ui.status == MonitorStatus.Starting) disabledTint else tint,
                            modifier = Modifier.size(40.dp)
                        )

                        if (ui.isMonitoring) {
                            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                            val scale by infiniteTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.4f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "scale"
                            )
                            val alpha by infiniteTransition.animateFloat(
                                initialValue = 0.4f,
                                targetValue = 0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "alpha"
                            )
                            Canvas(modifier = Modifier.size(100.dp)) {
                                drawCircle(
                                    color = tint,
                                    radius = (size.minDimension / 2) * scale,
                                    alpha = alpha
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = when (ui.status) {
                        MonitorStatus.Starting -> "UPLINKING…"
                        MonitorStatus.Live -> "STOP MONITORING"
                        MonitorStatus.Stopping -> "STOPPING…"
                        MonitorStatus.Failed -> "RETRY"
                        MonitorStatus.Idle -> "START MIC UPLINK"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = when (ui.status) {
                        MonitorStatus.Failed -> MaterialTheme.colorScheme.error
                        MonitorStatus.Stopping, MonitorStatus.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> if (ui.isMonitoring) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    },
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                ui.error?.takeIf { ui.status == MonitorStatus.Failed }?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = err,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Console Logs
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "SYSTEM CONSOLE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 2.sp
                    )
                    Text(
                        "${ui.logs.size} entries",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val listState = rememberLazyListState()
                LaunchedEffect(ui.logs.size) {
                    if (ui.logs.isNotEmpty()) listState.animateScrollToItem(ui.logs.size - 1)
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        items(ui.logs) { log ->
                            val color = when (log.kind) {
                                AudioMonitorViewModel.LogEntry.Kind.Good -> MaterialTheme.colorScheme.primary
                                AudioMonitorViewModel.LogEntry.Kind.Warn -> MaterialTheme.colorScheme.tertiary
                                AudioMonitorViewModel.LogEntry.Kind.Error -> MaterialTheme.colorScheme.error
                                AudioMonitorViewModel.LogEntry.Kind.Info -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Text(
                                text = "[${log.time}] ${log.text}",
                                style = MaterialTheme.typography.bodySmall,
                                color = color,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(status: MonitorStatus, message: String?) {
    val (label, color) = when (status) {
        MonitorStatus.Idle -> "IDLE" to MaterialTheme.colorScheme.onSurfaceVariant
        MonitorStatus.Starting -> "STARTING…" to MaterialTheme.colorScheme.tertiary
        MonitorStatus.Live -> "LIVE" to MaterialTheme.colorScheme.primary
        MonitorStatus.Stopping -> "STOPPING…" to MaterialTheme.colorScheme.tertiary
        MonitorStatus.Failed -> "FAILED" to MaterialTheme.colorScheme.error
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = message?.takeIf { status == MonitorStatus.Live || status == MonitorStatus.Failed } ?: label,
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.9f),
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun StatChip(modifier: Modifier = Modifier, label: String, value: String) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun WaveformGraph(amplitudes: List<Float>) {
    val primaryColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 40.dp)) {
        val width = size.width
        val height = size.height
        val centerY = height / 2
        val barWidth = 4.dp.toPx()
        val spacing = 2.dp.toPx()
        val maxBars = (width / (barWidth + spacing)).toInt()

        val visibleAmplitudes = amplitudes.takeLast(maxBars)

        visibleAmplitudes.forEachIndexed { index, amp ->
            val x = width - (visibleAmplitudes.size - index) * (barWidth + spacing)
            val barHeight = (amp * height).coerceAtLeast(4.dp.toPx())

            drawLine(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.5f),
                        primaryColor,
                        primaryColor.copy(alpha = 0.5f)
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

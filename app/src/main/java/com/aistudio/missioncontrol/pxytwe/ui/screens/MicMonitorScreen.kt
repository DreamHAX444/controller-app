package com.aistudio.missioncontrol.pxytwe.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

    // Start when we land, stop when we leave — VM owns the rest.
    LaunchedEffect(deviceId) {
        if (!ui.isMonitoring) vm.start(deviceId)
    }
    DisposableEffect(Unit) {
        onDispose {
            // Backstack pop or screen leave → cleanly tear down.
            vm.stop(sendCommand = false)
        }
    }

    // System back: if monitoring, require confirmation; otherwise just leave.
    BackHandler(enabled = ui.isMonitoring) {
        confirmExit = true
    }
    BackHandler(enabled = !ui.isMonitoring) {
        onBack()
    }

    if (confirmExit) {
        AlertDialog(
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
                TextButton(onClick = { confirmExit = false }) { Text("STAY") }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "MIC MONITOR · ${deviceId.uppercase()}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        StatusRow(ui.status, ui.statusMessage)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (ui.isMonitoring) confirmExit = true else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Waveform Visualizer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF0A0A0A))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!ui.isMonitoring) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.MicOff,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "SENSORS OFFLINE",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.2f),
                            letterSpacing = 2.sp
                        )
                    }
                } else if (ui.peakHistory.isEmpty()) {
                    CircularProgressIndicator(
                        color = Color(0xFF00E676),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(40.dp)
                    )
                } else {
                    WaveformGraph(ui.peakHistory)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats strip while monitoring
            AnimatedVisibility(visible = ui.isMonitoring) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatChip("RX", ui.chunksReceived.toString())
                    StatChip("DECODED", ui.chunksDecoded.toString())
                    StatChip("PEAK", "%.0f%%".format(ui.peak * 100f))
                    StatChip("RMS", "%.0f%%".format(ui.rms * 100f))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Control Button
            Surface(
                shape = CircleShape,
                color = if (ui.isMonitoring) Color.Red.copy(alpha = 0.2f) else Color(0xFF00E676).copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(
                    2.dp,
                    if (ui.isMonitoring) Color.Red else Color(0xFF00E676)
                ),
                modifier = Modifier
                    .size(100.dp)
                    .clickable(enabled = ui.status != MonitorStatus.Starting) {
                        if (ui.isMonitoring) vm.stop() else vm.start(deviceId)
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val icon = if (ui.isMonitoring) Icons.Default.Stop else Icons.Default.Mic
                    val tint = if (ui.isMonitoring) Color.Red else Color(0xFF00E676)
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
                                color = Color.Red,
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
                    MonitorStatus.Failed -> Color(0xFFFF5252)
                    MonitorStatus.Stopping, MonitorStatus.Idle -> Color.White.copy(alpha = 0.6f)
                    else -> if (ui.isMonitoring) Color.Red else Color(0xFF00E676)
                },
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            // Optional inline error banner
            ui.error?.takeIf { ui.status == MonitorStatus.Failed }?.let { err ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = err,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFF5252),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Console Logs
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "SYSTEM CONSOLE",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.4f),
                    letterSpacing = 1.5.sp
                )
                Text(
                    "${ui.logs.size} entries",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.3f)
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
                    .weight(1f),
                color = Color(0xFF050505),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(ui.logs) { log ->
                        val color = when (log.kind) {
                            AudioMonitorViewModel.LogEntry.Kind.Good -> Color(0xFF00E676)
                            AudioMonitorViewModel.LogEntry.Kind.Warn -> Color(0xFFFFC107)
                            AudioMonitorViewModel.LogEntry.Kind.Error -> Color(0xFFFF5252)
                            AudioMonitorViewModel.LogEntry.Kind.Info -> Color.White.copy(alpha = 0.7f)
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

@Composable
private fun StatusRow(status: MonitorStatus, message: String?) {
    val (label, color) = when (status) {
        MonitorStatus.Idle -> "IDLE" to Color.White.copy(alpha = 0.5f)
        MonitorStatus.Starting -> "STARTING…" to Color.Yellow
        MonitorStatus.Live -> "LIVE" to Color(0xFF00E676)
        MonitorStatus.Stopping -> "STOPPING…" to Color.Yellow
        MonitorStatus.Failed -> "FAILED" to Color(0xFFFF5252)
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
private fun StatChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.45f), letterSpacing = 1.sp)
            Text(value, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun WaveformGraph(amplitudes: List<Float>) {
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
                        Color(0xFF00E676).copy(alpha = 0.5f),
                        Color(0xFF00E676),
                        Color(0xFF00E676).copy(alpha = 0.5f)
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

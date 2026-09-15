package com.aistudio.missioncontrol.pxytwe.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.missioncontrol.pxytwe.webrtc.ScreenMonitorViewModel
import com.aistudio.missioncontrol.pxytwe.webrtc.ScreenMonitorUiState

@Composable
fun ScreenMonitorScreen(
    deviceId: String,
    sessionId: String,
    qualityProfile: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ScreenMonitorViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()

    var showStopDialog by remember { mutableStateOf(false) }

    // Tie WebRTC lifecycle to this composable being in the composition
    LaunchedEffect(deviceId, sessionId, qualityProfile) {
        if (state.uiState == ScreenMonitorUiState.IDLE) {
            viewModel.setDevice(deviceId)
            viewModel.startMonitoring(context, sessionId, qualityProfile)
        } else if (state.sessionId != sessionId) {
            // Edge case: re-entered with a DIFFERENT session ID.
            // We must stop the old and start the new.
            viewModel.stopMonitoring()
            viewModel.setDevice(deviceId)
            viewModel.startMonitoring(context, sessionId, qualityProfile)
        }
    }

    // Capture system back press to show confirmation
    BackHandler(enabled = true) {
        if (state.uiState == ScreenMonitorUiState.DISCONNECTED || state.uiState == ScreenMonitorUiState.ERROR) {
            onBack()
        } else {
            showStopDialog = true
        }
    }
    
    // Automatically navigate back once session is cleanly terminated
    LaunchedEffect(state.uiState) {
        if (state.uiState == ScreenMonitorUiState.DISCONNECTED && showStopDialog) {
            onBack()
        }
    }

    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text("Stop monitoring?") },
            text = { Text("The current live monitoring session will be disconnected.") },
            confirmButton = {
                TextButton(onClick = {
                    // Do not navigate back yet. Stop the session, wait for DISCONNECTED state.
                    viewModel.stopMonitoring()
                }) {
                    Text("Stop Monitoring", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { 
                    if (state.uiState == ScreenMonitorUiState.DISCONNECTED || state.uiState == ScreenMonitorUiState.ERROR) {
                        onBack()
                    } else {
                        showStopDialog = true
                    }
                }) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Monitor,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "SCREEN MONITOR",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = deviceId.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                
                var expanded by remember { mutableStateOf(false) }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val nqColor = when (state.networkQuality) {
                            "EXCELLENT" -> Color(0xFF4CAF50)
                            "GOOD" -> Color(0xFF8BC34A)
                            "FAIR" -> Color(0xFFFFC107)
                            "POOR" -> Color(0xFFFF9800)
                            "CRITICAL" -> Color(0xFFF44336)
                            else -> Color.Gray
                        }
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(nqColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(0.dp)) {
                            val modeText = if (state.isAutoMode) "AUTO (${state.selectedProfile})" else state.selectedProfile
                            Text(modeText)
                        }
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("AUTO", "QUALITY", "BALANCED", "SMOOTH", "NETWORK_SAVER").forEach { profile ->
                            DropdownMenuItem(
                                text = { Text(profile) },
                                onClick = {
                                    viewModel.setVideoProfile(profile)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            when (state.uiState) {
                ScreenMonitorUiState.IDLE, ScreenMonitorUiState.CONNECTING -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Awaiting WebRTC Connection...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                ScreenMonitorUiState.PLAYING -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            color = Color.Black
                        ) {
                            WebRtcDiagnosticView(
                                videoTrack = state.videoTrack,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            val isAudioConnected = state.audioTrack != null
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isAudioConnected) Color.Green else Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AUDIO: " + if (isAudioConnected) "Connected" else "Unavailable",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                ScreenMonitorUiState.DISCONNECTED -> {
                    Text(
                        text = "Session Disconnected",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ScreenMonitorUiState.ERROR -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Error: ${state.errorMessage}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.startMonitoring(context, sessionId, qualityProfile) }) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

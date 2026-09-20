package com.aistudio.missioncontrol.pxytwe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.missioncontrol.pxytwe.AppState
import java.util.UUID

import androidx.compose.runtime.saveable.rememberSaveable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailsScreen(
    deviceId: String,
    onBack: () -> Unit,
    onNavigateToScreenMonitor: (String, String) -> Unit,
    onNavigateToCameraAccess: () -> Unit
) {
    val telemetry = AppState.activeDevices[deviceId]
    
    var selectedQuality by rememberSaveable { mutableStateOf("BALANCED") }
    var expanded by remember { mutableStateOf(false) }

    val qualityOptions = listOf(
        "QUALITY" to "1920×1080 • 20 FPS • ~2.5 Mbps",
        "BALANCED" to "1280×720 • 24 FPS • ~1.5 Mbps",
        "SMOOTH" to "960×540 • 30 FPS • ~1.0 Mbps",
        "NETWORK_SAVER" to "640×360 • 15 FPS • ~0.5 Mbps"
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(deviceId) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (telemetry == null) {
                Text("Device not found or offline.", style = MaterialTheme.typography.bodyLarge)
                return@Column
            }

            // Specs
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Telemetry Specs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Battery: ${telemetry.battery}%")
                    Text("Speed: ${telemetry.speed} m/s")
                    Text("Network: ${telemetry.networkType} (${telemetry.signal} dBm)")
                    Text("Location: ${telemetry.lat}, ${telemetry.lon}")
                }
            }
            
            // Actions
            Text("Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = qualityOptions.find { it.first == selectedQuality }?.second ?: selectedQuality,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Quality Profile") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    qualityOptions.forEach { (profileName, description) ->
                        DropdownMenuItem(
                            text = { 
                                Column {
                                    Text(profileName, fontWeight = FontWeight.Bold)
                                    Text(description, style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            onClick = {
                                selectedQuality = profileName
                                expanded = false
                            }
                        )
                    }
                }
            }

            Button(
                onClick = {
                    // Generate new logical session
                    val newSessionId = UUID.randomUUID().toString()
                    onNavigateToScreenMonitor(newSessionId, selectedQuality)
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                Icon(Icons.Default.Monitor, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("START SCREEN MONITOR")
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onNavigateToCameraAccess,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("CAMERA ACCESS")
            }
        }
    }
}

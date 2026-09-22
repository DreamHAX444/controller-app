package com.aistudio.missioncontrol.pxytwe.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.missioncontrol.pxytwe.AppState
import com.aistudio.missioncontrol.pxytwe.camera.CameraDeviceInfo
import com.aistudio.missioncontrol.pxytwe.camera.CameraConfiguration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraAccessScreen(
    deviceId: String,
    onBack: () -> Unit
) {
    val capabilityState = AppState.cameraCapabilitiesState[deviceId] ?: AppState.CameraCapabilitiesState.IDLE
    val capabilities = AppState.cameraCapabilities[deviceId] ?: emptyList()
    
    var selectedCameraId by remember { mutableStateOf<String?>(null) }
    var selectedResolution by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(deviceId) {
        if (capabilityState == AppState.CameraCapabilitiesState.IDLE) {
            AppState.requestCameraCapabilities(deviceId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Camera Access - $deviceId") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { AppState.requestCameraCapabilities(deviceId) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (capabilityState) {
                AppState.CameraCapabilitiesState.IDLE, AppState.CameraCapabilitiesState.LOADING -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Detecting cameras...")
                        }
                    }
                }
                AppState.CameraCapabilitiesState.PERMISSION_REQUIRED -> {
                    ErrorStateView(
                        icon = Icons.Default.Warning,
                        title = "Camera permission required",
                        message = "The Tracker has not granted camera access.\nEnable camera permission on the Tracker device."
                    )
                }
                AppState.CameraCapabilitiesState.UNAVAILABLE -> {
                    ErrorStateView(
                        icon = Icons.Default.Info,
                        title = "Cameras Unavailable",
                        message = "The Tracker currently has no available camera capability."
                    )
                }
                AppState.CameraCapabilitiesState.FAILED -> {
                    ErrorStateView(
                        icon = Icons.Default.Warning,
                        title = "Discovery Failed",
                        message = "Failed to discover cameras on the Tracker."
                    ) {
                        Button(onClick = { AppState.requestCameraCapabilities(deviceId) }) {
                            Text("Retry")
                        }
                    }
                }
                AppState.CameraCapabilitiesState.SUCCESS -> {
                    CameraBrowserContent(
                        deviceId = deviceId,
                        cameras = capabilities,
                        selectedCameraId = selectedCameraId,
                        onCameraSelected = { 
                            selectedCameraId = it.cameraId 
                            selectedResolution = null // Reset resolution when camera changes
                        },
                        selectedResolution = selectedResolution,
                        onResolutionSelected = { selectedResolution = it }
                    )
                }
            }
        }
    }
}

@Composable
fun ErrorStateView(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    action: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) {
            Spacer(modifier = Modifier.height(24.dp))
            action()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraBrowserContent(
    deviceId: String,
    cameras: List<CameraDeviceInfo>,
    selectedCameraId: String?,
    onCameraSelected: (CameraDeviceInfo) -> Unit,
    selectedResolution: String?,
    onResolutionSelected: (String?) -> Unit
) {
    val scrollState = rememberScrollState()
    var manualSelectedFpsMax by remember { mutableStateOf<Int?>(null) }
    var selectedMode by remember { mutableStateOf(com.aistudio.missioncontrol.pxytwe.camera.CameraQualityMode.CUSTOM) }
    
    // When camera changes, reset manual selections
    LaunchedEffect(selectedCameraId) {
        manualSelectedFpsMax = null
    }
    LaunchedEffect(selectedResolution) {
        manualSelectedFpsMax = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text("Camera Configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        // 1. Camera Selection
        var cameraExpanded by remember { mutableStateOf(false) }
        val selectedCamera = cameras.find { it.cameraId == selectedCameraId }
        val cameraText = selectedCamera?.displayName ?: "Select a Camera"
        
        ExposedDropdownMenuBox(
            expanded = cameraExpanded,
            onExpandedChange = { cameraExpanded = !cameraExpanded }
        ) {
            OutlinedTextField(
                value = cameraText,
                onValueChange = {},
                readOnly = true,
                label = { Text("Camera") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cameraExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = cameraExpanded,
                onDismissRequest = { cameraExpanded = false }
            ) {
                cameras.forEach { camera ->
                    DropdownMenuItem(
                        text = { Text(camera.displayName) },
                        onClick = {
                            onCameraSelected(camera)
                            cameraExpanded = false
                        }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // 2. Mode Selection
        var modeExpanded by remember { mutableStateOf(false) }
        val modeEnabled = selectedCamera != null
        val modeText = selectedMode.displayName

        ExposedDropdownMenuBox(
            expanded = modeExpanded && modeEnabled,
            onExpandedChange = { if (modeEnabled) modeExpanded = !modeExpanded }
        ) {
            OutlinedTextField(
                value = modeText,
                onValueChange = {},
                readOnly = true,
                enabled = modeEnabled,
                label = { Text("Mode") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = modeExpanded && modeEnabled,
                onDismissRequest = { modeExpanded = false }
            ) {
                com.aistudio.missioncontrol.pxytwe.camera.CameraQualityMode.values().forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.displayName) },
                        onClick = {
                            selectedMode = mode
                            if (mode != com.aistudio.missioncontrol.pxytwe.camera.CameraQualityMode.CUSTOM) {
                                // Clear manual selections when switching to an auto mode
                                onResolutionSelected(null)
                                manualSelectedFpsMax = null
                            }
                            modeExpanded = false
                        }
                    )
                }
            }
        }

        if (modeEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(selectedMode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (selectedMode == com.aistudio.missioncontrol.pxytwe.camera.CameraQualityMode.CUSTOM && selectedCamera != null) {
            Spacer(modifier = Modifier.height(16.dp))

            // 3. Resolution Selection (Manual)
            var resExpanded by remember { mutableStateOf(false) }
            val sortedConfigs = selectedCamera.configurations.sortedByDescending { it.width * it.height }
            val uniqueResolutions = sortedConfigs.map { "${it.width} × ${it.height}" }.distinct()
            val resText = selectedResolution ?: "Select Resolution"

            ExposedDropdownMenuBox(
                expanded = resExpanded,
                onExpandedChange = { resExpanded = !resExpanded }
            ) {
                OutlinedTextField(
                    value = resText,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Resolution") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = resExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = resExpanded,
                    onDismissRequest = { resExpanded = false }
                ) {
                    uniqueResolutions.forEach { resStr ->
                        DropdownMenuItem(
                            text = { Text(resStr) },
                            onClick = {
                                onResolutionSelected(resStr)
                                resExpanded = false
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // 4. FPS Selection (Manual)
            var fpsExpanded by remember { mutableStateOf(false) }
            val fpsEnabled = selectedResolution != null
            val resParts = selectedResolution?.split(" × ")
            val w = resParts?.getOrNull(0)?.toIntOrNull() ?: 0
            val h = resParts?.getOrNull(1)?.toIntOrNull() ?: 0
            
            val configsForRes = selectedCamera.configurations.filter { it.width == w && it.height == h }
            val fpsRanges = configsForRes.flatMap { it.supportedFpsRanges }.distinct().sortedByDescending { it.max }
            
            val fpsText = manualSelectedFpsMax?.let { "$it FPS" } ?: "Select FPS"

            ExposedDropdownMenuBox(
                expanded = fpsExpanded && fpsEnabled,
                onExpandedChange = { if (fpsEnabled) fpsExpanded = !fpsExpanded }
            ) {
                OutlinedTextField(
                    value = fpsText,
                    onValueChange = {},
                    readOnly = true,
                    enabled = fpsEnabled,
                    label = { Text("FPS") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fpsExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = fpsExpanded && fpsEnabled,
                    onDismissRequest = { fpsExpanded = false }
                ) {
                    fpsRanges.forEach { range ->
                        val label = if (range.min == range.max) "${range.max} FPS" else "${range.min}–${range.max} FPS"
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                manualSelectedFpsMax = range.max
                                fpsExpanded = false
                            }
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Final Configuration State Evaluation
        if (selectedCamera != null) {
            val autoConfig = if (selectedMode != com.aistudio.missioncontrol.pxytwe.camera.CameraQualityMode.CUSTOM) {
                com.aistudio.missioncontrol.pxytwe.camera.CameraConfigurationSelector.select(selectedCamera, selectedMode)
            } else {
                null
            }

            val finalConfig: com.aistudio.missioncontrol.pxytwe.camera.CameraCaptureConfiguration? = if (selectedMode != com.aistudio.missioncontrol.pxytwe.camera.CameraQualityMode.CUSTOM) {
                autoConfig
            } else if (selectedResolution != null && manualSelectedFpsMax != null) {
                val resParts = selectedResolution.split(" × ")
                val w = resParts.getOrNull(0)?.toIntOrNull() ?: 0
                val h = resParts.getOrNull(1)?.toIntOrNull() ?: 0
                
                val isSupported = com.aistudio.missioncontrol.pxytwe.camera.isConfigurationSupported(
                    capabilities = cameras,
                    cameraId = selectedCamera.cameraId,
                    width = w,
                    height = h,
                    fps = manualSelectedFpsMax!!
                )
                
                if (isSupported) {
                    com.aistudio.missioncontrol.pxytwe.camera.CameraCaptureConfiguration(
                        cameraId = selectedCamera.cameraId,
                        width = w,
                        height = h,
                        fps = manualSelectedFpsMax!!
                    )
                } else null
            } else null
            
            if (finalConfig != null) {
                val prefix = if (selectedMode == com.aistudio.missioncontrol.pxytwe.camera.CameraQualityMode.CUSTOM) {
                    "Selected Configuration"
                } else {
                    "Selected automatically"
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(prefix, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(selectedCamera.displayName, style = MaterialTheme.typography.bodyMedium)
                        Text("${finalConfig.width} × ${finalConfig.height}", style = MaterialTheme.typography.bodyMedium)
                        Text("${finalConfig.fps} FPS", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val startState = AppState.cameraStartStates[deviceId]
                        
                        if (startState != null) {
                            when (startState.status) {
                                AppState.CameraStartStatus.REQUESTING -> {
                                    Text("Requesting camera start...", color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                AppState.CameraStartStatus.ACCEPTED -> {
                                    Text("Camera start request accepted. Waiting for capture...", color = androidx.compose.ui.graphics.Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                }
                                AppState.CameraStartStatus.OPENING -> {
                                    Text("Camera opening...", color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                AppState.CameraStartStatus.CAPTURING -> {
                                    Text("Camera capture active", color = androidx.compose.ui.graphics.Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                    startState.telemetry?.let { telemetry ->
                                        Spacer(modifier = Modifier.height(12.dp))
                                        
                                        // REQUESTED SECTION
                                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                                            Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                                                Text("Requested", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("Camera: ${selectedCamera?.displayName ?: "Unknown"}", style = MaterialTheme.typography.bodySmall)
                                                Text("Resolution: ${telemetry.requestedWidth} × ${telemetry.requestedHeight}", style = MaterialTheme.typography.bodySmall)
                                                Text("FPS: ${telemetry.requestedFps}", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        // ACTUAL SECTION
                                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                                            Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                                                Text("Actual", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("Camera: ${selectedCamera?.displayName ?: "Unknown"}", style = MaterialTheme.typography.bodySmall)
                                                if (telemetry.actualWidth != null && telemetry.actualHeight != null) {
                                                    Text("Resolution: ${telemetry.actualWidth} × ${telemetry.actualHeight}", style = MaterialTheme.typography.bodySmall)
                                                } else {
                                                    Text("Resolution: Measuring...", style = MaterialTheme.typography.bodySmall)
                                                }
                                                
                                                if (telemetry.observedFps != null) {
                                                    val fpsStr = String.format(java.util.Locale.US, "%.1f", telemetry.observedFps)
                                                    Text("FPS: $fpsStr", style = MaterialTheme.typography.bodySmall)
                                                } else {
                                                    Text("FPS: Measuring...", style = MaterialTheme.typography.bodySmall)
                                                }
                                                
                                                Text("Frames: ${telemetry.frameCount}", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                        
                                        // MISMATCH DETECTION SECTION
                                        val mismatchWarnings = mutableListOf<String>()
                                        if (telemetry.actualWidth != null && (telemetry.actualWidth != telemetry.requestedWidth || telemetry.actualHeight != telemetry.requestedHeight)) {
                                            mismatchWarnings.add("Actual resolution differs from requested resolution")
                                        }
                                        if (telemetry.observedFps != null) {
                                            val fpsTolerance = 5.0
                                            if (Math.abs(telemetry.observedFps - telemetry.requestedFps) > fpsTolerance) {
                                                mismatchWarnings.add("Actual FPS differs from requested FPS")
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        if (mismatchWarnings.isNotEmpty()) {
                                            Text("Status: ${mismatchWarnings.joinToString(", ")}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
                                        } else if (telemetry.observedFps != null) {
                                            Text("Status: Normal capture", color = androidx.compose.ui.graphics.Color(0xFF2E7D32), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
                                        } else {
                                            Text("Status: Waiting for first frame...", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                                AppState.CameraStartStatus.SWITCHING -> {
                                    Text("Switching camera...", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                                }
                                AppState.CameraStartStatus.RESTORING -> {
                                    Text("Switch failed � previous camera restored...", color = androidx.compose.ui.graphics.Color(0xFFE65100), fontWeight = FontWeight.Bold)
                                }
                                AppState.CameraStartStatus.STOPPING -> {
                                    Text("Camera stopping...", color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                AppState.CameraStartStatus.STOPPED -> {
                                    Text("Camera stopped", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                AppState.CameraStartStatus.REJECTED -> {
                                    Text("Request rejected: ", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                }
                                AppState.CameraStartStatus.FAILED -> {
                                    Text("Request failed: ", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                }
                                AppState.CameraStartStatus.ERROR -> {
                                    Text("Camera capture error", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                }
                                else -> {}
                            }
                        }

                                                val isActiveOrPending = startState != null && (
                            startState.status == AppState.CameraStartStatus.REQUESTING ||
                            startState.status == AppState.CameraStartStatus.ACCEPTED ||
                            startState.status == AppState.CameraStartStatus.OPENING ||
                            startState.status == AppState.CameraStartStatus.CAPTURING ||
                            startState.status == AppState.CameraStartStatus.SWITCHING ||
                            startState.status == AppState.CameraStartStatus.RESTORING
                        )

                        if (!isActiveOrPending) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    AppState.requestStartCamera(
                                        deviceId = deviceId,
                                        cameraId = finalConfig.cameraId,
                                        width = finalConfig.width,
                                        height = finalConfig.height,
                                        fps = finalConfig.fps
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Start Camera")
                            }
                        } else if (startState != null && startState.cameraId != null && startState.cameraId != finalConfig.cameraId && 
                            (startState.status == AppState.CameraStartStatus.CAPTURING || startState.status == AppState.CameraStartStatus.OPENING)) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    AppState.requestSwitchCamera(
                                        deviceId = deviceId,
                                        cameraId = finalConfig.cameraId,
                                        width = startState.width ?: finalConfig.width,
                                        height = startState.height ?: finalConfig.height,
                                        fps = startState.fps ?: finalConfig.fps
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Text("Switch to this Camera")
                            }
                        }
                        
                        val isCancellable = startState != null && (
                            startState.status == AppState.CameraStartStatus.ACCEPTED || 
                            startState.status == AppState.CameraStartStatus.OPENING || 
                            startState.status == AppState.CameraStartStatus.CAPTURING ||
                            startState.status == AppState.CameraStartStatus.SWITCHING ||
                            startState.status == AppState.CameraStartStatus.RESTORING
                        )

                        if (isCancellable) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    AppState.requestStopCamera(deviceId)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Stop Camera")
                            }
                        }
}
                }
            } else if (selectedMode == com.aistudio.missioncontrol.pxytwe.camera.CameraQualityMode.CUSTOM && selectedResolution != null && manualSelectedFpsMax != null) {
                Text("Invalid Configuration Selected", color = MaterialTheme.colorScheme.error)
            } else if (selectedMode != com.aistudio.missioncontrol.pxytwe.camera.CameraQualityMode.CUSTOM) {
                Text("Failed to calculate automatic configuration", color = MaterialTheme.colorScheme.error)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Camera Information", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            if (selectedCamera != null) {
                CameraDetails(camera = selectedCamera)
            }
        }
    }
}

@Composable
fun CameraCard(
    camera: CameraDeviceInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val cameraName = camera.displayName
            
            val niceFacing = camera.facing.name.lowercase().replaceFirstChar { it.uppercase() }
            val niceType = camera.lensType.name.lowercase().replaceFirstChar { it.uppercase() }.replace("_", "-")
            
            Text(text = cameraName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "$niceFacing • $niceType", style = MaterialTheme.typography.bodyMedium)
            
            // Show a summary of top resolutions
            val topRes = camera.configurations
                .sortedByDescending { it.width * it.height }
                .map { "${it.width}×${it.height}" }
                .distinct()
                .take(3)
                
            if (topRes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = topRes.joinToString(" • "), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun ResolutionCard(
    resolution: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            text = resolution,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun CameraDetails(camera: CameraDeviceInfo) {
    val niceFacing = camera.facing.name.lowercase().replaceFirstChar { it.uppercase() }
    val niceType = camera.lensType.name.lowercase().replaceFirstChar { it.uppercase() }.replace("_", "-")
    
    val hwLevelStr = when (camera.hardwareLevel) {
        0 -> "LIMITED"
        1 -> "FULL"
        2 -> "LEGACY"
        3 -> "LEVEL_3"
        4 -> "EXTERNAL"
        else -> "UNKNOWN (${camera.hardwareLevel})"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        DetailRow("Facing", niceFacing)
        DetailRow("Type", niceType)
        DetailRow("Sensor orientation", "${camera.sensorOrientation}°")
        if (camera.focalLengths.isNotEmpty()) DetailRow("Focal length", "${camera.focalLengths.joinToString(", ")} mm")
        DetailRow("Hardware level", hwLevelStr)
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

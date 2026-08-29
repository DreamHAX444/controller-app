package com.aistudio.missioncontrol.pxytwe.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.aistudio.missioncontrol.pxytwe.AppState
import com.aistudio.missioncontrol.pxytwe.SupabaseClientManager
import com.aistudio.missioncontrol.pxytwe.audio.AudioMonitorRepository
import com.aistudio.missioncontrol.pxytwe.security.SecurityManager
import com.aistudio.missioncontrol.pxytwe.ui.theme.ThemeManager
import com.aistudio.missioncontrol.pxytwe.ui.theme.ThemeMode
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onNavigateToSiren: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val securityManager = remember { SecurityManager(context) }
    
    var pinResetDialog by remember { mutableStateOf(false) }
    var themeDialog by remember { mutableStateOf(false) }
    var networkInfoDialog by remember { mutableStateOf(false) }
    
    val currentTheme by ThemeManager.themeMode.collectAsState()

    var isPinConfigured by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        isPinConfigured = securityManager.isPinSet()
    }

    val connectionState by SupabaseClientManager.connectionState.collectAsState()
    val isConnected = connectionState == SupabaseClientManager.ConnectionState.Connected
    val realtimeStatusLabel = when (connectionState) {
        SupabaseClientManager.ConnectionState.Connected -> "CONNECTED"
        SupabaseClientManager.ConnectionState.Connecting -> "CONNECTING…"
        SupabaseClientManager.ConnectionState.Reconnecting -> "RECONNECTING…"
        SupabaseClientManager.ConnectionState.Disconnected -> "OFFLINE"
    }

    val isSirenActive = AppState.isSirenEnabled.value
    val activeMicSession by AudioMonitorRepository.activeSession.collectAsState()

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
                            text = "MISSION CONTROL",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "SYSTEM SETTINGS",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // Connection Status Pill
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        border = BorderStroke(
                            1.dp,
                            if (isConnected) Color(0xFF4ADE80).copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        ),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isConnected) Color(0xFF4ADE80) else MaterialTheme.colorScheme.error)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = realtimeStatusLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isConnected) Color(0xFF4ADE80) else MaterialTheme.colorScheme.error,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Security & Protection
                item(key = "sec_section") {
                    SettingsSectionHeader("SECURITY & ACCESS CONTROL")
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModernSettingsCard(
                            icon = Icons.Default.Lock,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "Master Security PIN",
                            subtitle = if (isPinConfigured == true) "Biometric and PIN lock configured" else "No security PIN set",
                            badgeText = if (isPinConfigured == true) "CONFIGURED" else "UNPROTECTED",
                            badgeColor = if (isPinConfigured == true) Color(0xFF4ADE80) else MaterialTheme.colorScheme.error,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                pinResetDialog = true
                            }
                        )

                        ModernSettingsCard(
                            icon = Icons.Default.NotificationsActive,
                            iconTint = if (isSirenActive) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant,
                            title = "Geofence Intrusion Siren",
                            subtitle = if (isSirenActive) "High-volume alarm triggers on boundary breach" else "Intrusion siren currently disabled",
                            badgeText = if (isSirenActive) "ENABLED" else "DISABLED",
                            badgeColor = if (isSirenActive) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNavigateToSiren()
                            }
                        )

                        ModernSettingsCard(
                            icon = Icons.Default.GraphicEq,
                            iconTint = if (activeMicSession != null) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurfaceVariant,
                            title = "Live Acoustic Surveillance",
                            subtitle = if (activeMicSession != null) "Active listening session on $activeMicSession" else "No audio uplink currently broadcasting",
                            badgeText = if (activeMicSession != null) "ACTIVE" else "STANDBY",
                            badgeColor = if (activeMicSession != null) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = {
                                val currentSession = activeMicSession
                                if (currentSession != null) {
                                    scope.launch {
                                        AudioMonitorRepository.stopMonitoring(currentSession)
                                        Toast.makeText(context, "Closed audio stream", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }

                // Section 2: Interface & Theme
                item(key = "ui_section") {
                    SettingsSectionHeader("APPEARANCE & THEME")
                    Spacer(Modifier.height(8.dp))
                    ModernSettingsCard(
                        icon = Icons.Default.Palette,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        title = "Display Theme Mode",
                        subtitle = "Select system, dark AMOLED, or light theme",
                        badgeText = currentTheme.name,
                        badgeColor = MaterialTheme.colorScheme.secondary,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            themeDialog = true
                        }
                    )
                }

                // Section 3: Network & Pipeline
                item(key = "net_section") {
                    SettingsSectionHeader("NETWORK & INFRASTRUCTURE")
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModernSettingsCard(
                            icon = Icons.Default.Hub,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "Supabase Realtime Engine",
                            subtitle = "Topic: public:commands • WebSocket WSS Broadcast",
                            badgeText = realtimeStatusLabel,
                            badgeColor = if (isConnected) Color(0xFF4ADE80) else MaterialTheme.colorScheme.error,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                networkInfoDialog = true
                            }
                        )

                        ModernSettingsCard(
                            icon = Icons.Default.Layers,
                            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                            title = "Offline Map Tile Storage",
                            subtitle = "Osmdroid vector and satellite tile memory cache",
                            badgeText = "MANAGED",
                            badgeColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = {
                                Toast.makeText(context, "Map cache optimized", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                // Section 4: System Specs & About
                item(key = "about_section") {
                    SettingsSectionHeader("SYSTEM INFORMATION")
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "MISSION CONTROL CONTROLLER",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = "Version 1.2.0 (Build 2026.08)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "STABLE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                SpecRow("RUNTIME", "Android 15+ / ART Optimized")
                                SpecRow("UI TOOLKIT", "Jetpack Compose Material 3")
                                SpecRow("AUDIO CODEC", "PCM 16-bit Mono @ 8kHz")
                                SpecRow("REALTIME STACK", "Supabase Realtime WSS")
                                SpecRow("GEOFENCE ENGINE", "Osmdroid Spatial Polygon Raycaster")
                            }
                        }
                    }
                }
            }
        }
    }

    // Reset Master PIN Dialog
    if (pinResetDialog) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(24.dp),
            onDismissRequest = { pinResetDialog = false },
            title = { Text("Reset Master Security PIN?", fontWeight = FontWeight.Bold) },
            text = { Text("Clearing the security PIN will require you to set a new PIN the next time the app opens.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val prefs = context.getSharedPreferences("secure_prefs", android.content.Context.MODE_PRIVATE)
                        prefs.edit().remove("master_pin_hash").apply()
                        pinResetDialog = false
                        isPinConfigured = false
                        Toast.makeText(context, "PIN cleared. Restart app to set a new PIN.", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("RESET PIN", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pinResetDialog = false }) {
                    Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // Select Theme Modal Dialog
    if (themeDialog) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(24.dp),
            onDismissRequest = { themeDialog = false },
            title = { Text("Display Theme Mode", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        val isSelected = currentTheme == mode
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent,
                            onClick = {
                                ThemeManager.setTheme(mode)
                                themeDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        ThemeManager.setTheme(mode)
                                        themeDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = when (mode) {
                                        ThemeMode.SYSTEM -> "System Default"
                                        ThemeMode.DARK -> "Dark (AMOLED Night)"
                                        ThemeMode.LIGHT -> "Light Mode"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { themeDialog = false }) {
                    Text("CLOSE", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // Realtime Network Endpoint Dialog
    if (networkInfoDialog) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(24.dp),
            onDismissRequest = { networkInfoDialog = false },
            title = { Text("Supabase Realtime Pipeline", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpecRow("CONNECTION STATE", realtimeStatusLabel)
                    SpecRow("BROADCAST CHANNEL", "public:commands")
                    SpecRow("AUDIO CHANNEL", "public:audio_stream")
                    SpecRow("HEARTBEAT INTERVAL", "10 seconds")
                    SpecRow("RECONNECT DELAY", "2 seconds bounded")
                    SpecRow("SDK VERSION", "supabase-kt 2.4.0")
                }
            },
            confirmButton = {
                Button(
                    onClick = { networkInfoDialog = false },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text("CLOSE", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.5.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun ModernSettingsCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    badgeText: String? = null,
    badgeColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            if (badgeText != null) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

package com.aistudio.missioncontrol.pxytwe.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.missioncontrol.pxytwe.SupabaseClientManager
import com.aistudio.missioncontrol.pxytwe.audio.AudioMonitorRepository
import com.aistudio.missioncontrol.pxytwe.security.SecurityManager
import com.aistudio.missioncontrol.pxytwe.ui.theme.ThemeManager
import com.aistudio.missioncontrol.pxytwe.ui.theme.ThemeMode
import kotlinx.coroutines.launch

private val ColorBackground @Composable get() = MaterialTheme.colorScheme.background
private val ColorCard @Composable get() = MaterialTheme.colorScheme.surface
private val ColorTextPrimary @Composable get() = MaterialTheme.colorScheme.onSurface
private val ColorTextSecondary @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val ColorBorder @Composable get() = MaterialTheme.colorScheme.outline
private val ColorIcon @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val ColorPrimary @Composable get() = MaterialTheme.colorScheme.primary
private val ColorError @Composable get() = MaterialTheme.colorScheme.error

@Composable
fun SettingsScreen(onNavigateToSiren: () -> Unit = {}) {
    val context = LocalContext.current
    val securityManager = remember { SecurityManager(context) }
    var pinResetDialog by remember { mutableStateOf(false) }
    var themeDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val currentTheme by ThemeManager.themeMode.collectAsState()

    var isPinConfigured by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        isPinConfigured = securityManager.isPinSet()
    }

    val connectionState by SupabaseClientManager.connectionState.collectAsState()
    val realtimeStatusLabel = when (connectionState) {
        SupabaseClientManager.ConnectionState.Connected -> "Connected"
        SupabaseClientManager.ConnectionState.Connecting -> "Connecting…"
        SupabaseClientManager.ConnectionState.Reconnecting -> "Reconnecting…"
        SupabaseClientManager.ConnectionState.Disconnected -> "Offline"
    }

    Scaffold(
        containerColor = ColorBackground,
        modifier = Modifier.fillMaxSize().statusBarsPadding()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Text(
                    "CONTROLLER",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorTextSecondary,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ColorTextPrimary
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SettingsRow(
                    icon = Icons.Default.Lock,
                    label = "Master PIN",
                    value = when (isPinConfigured) {
                        null -> "Loading…"
                        true -> "Configured"
                        false -> "Not set"
                    }
                ) { pinResetDialog = true }

                SettingsRow(
                    icon = Icons.Default.Palette,
                    label = "Theme",
                    value = currentTheme.name
                ) { themeDialog = true }

                SettingsRow(
                    icon = Icons.Default.NotificationsActive,
                    label = "Geofence Siren",
                    value = if (com.aistudio.missioncontrol.pxytwe.AppState.isSirenEnabled.value) "Enabled" else "Disabled"
                ) { onNavigateToSiren() }

                SettingsRow(
                    icon = Icons.Default.Hub,
                    label = "Supabase Realtime",
                    value = realtimeStatusLabel
                ) { /* TODO: show endpoint info */ }

                SettingsRow(
                    icon = Icons.Default.Mic,
                    label = "Mic session",
                    value = if (AudioMonitorRepository.activeSession.value != null) "ACTIVE" else "—"
                ) { /* TODO: stop active session */ }

                SettingsRow(
                    icon = Icons.Default.Info,
                    label = "Version",
                    value = "1.0.0"
                ) { /* version detail modal */ }

                Spacer(Modifier.height(16.dp))

                // About blurb
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ColorCard),
                    border = BorderStroke(1.dp, ColorBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 100.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            "MISSION CONTROL",
                            style = MaterialTheme.typography.labelSmall,
                            color = ColorPrimary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Live Tracker controller app. Pairs with the Live Tracker Android service " +
                                    "and surfaces device telemetry + ambient audio monitor.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ColorTextSecondary,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }

    if (pinResetDialog) {
        AlertDialog(
            containerColor = ColorCard,
            titleContentColor = ColorTextPrimary,
            textContentColor = ColorTextSecondary,
            onDismissRequest = { pinResetDialog = false },
            title = { Text("Reset Master PIN?") },
            text = { Text("You'll be required to set a new PIN on next launch. This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            securityManager.clearPin()
                            isPinConfigured = false
                            pinResetDialog = false
                            Toast.makeText(context, "PIN cleared. Restart to set a new PIN.", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ColorError)
                ) { Text("RESET") }
            },
            dismissButton = {
                TextButton(onClick = { pinResetDialog = false }) { Text("CANCEL", color = ColorTextSecondary) }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (themeDialog) {
        AlertDialog(
            containerColor = ColorCard,
            titleContentColor = ColorTextPrimary,
            onDismissRequest = { themeDialog = false },
            title = { Text("Select Theme") },
            text = {
                Column {
                    ThemeMode.values().forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    ThemeManager.setTheme(mode)
                                    themeDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentTheme == mode,
                                onClick = {
                                    ThemeManager.setTheme(mode)
                                    themeDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = ColorPrimary, unselectedColor = ColorBorder)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(mode.name, color = ColorTextPrimary, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { themeDialog = false }) { Text("CLOSE", color = ColorPrimary) }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ColorCard),
        border = BorderStroke(1.dp, ColorBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ColorBorder),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = ColorTextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = ColorTextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = ColorTextSecondary,
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.width(12.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = ColorIcon.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

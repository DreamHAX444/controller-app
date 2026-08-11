package com.aistudio.missioncontrol.pxytwe.ui.screens

import android.widget.Toast
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

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val securityManager = remember { SecurityManager(context) }
    var pinResetDialog by remember { mutableStateOf(false) }
    var themeDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val currentTheme by ThemeManager.themeMode.collectAsState()

    // EncryptedSharedPreferences read on the IO dispatcher — no main-thread
    // crypto on cold start.
    var isPinConfigured by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        isPinConfigured = securityManager.isPinSet()
    }

    // Live realtime connection state — never let the UI claim "Connected"
    // when the SDK is mid-reconnect (E3 surface).
    val connectionState by SupabaseClientManager.connectionState.collectAsState()
    val realtimeStatusLabel = when (connectionState) {
        SupabaseClientManager.ConnectionState.Connected -> "Connected"
        SupabaseClientManager.ConnectionState.Connecting -> "Connecting…"
        SupabaseClientManager.ConnectionState.Reconnecting -> "Reconnecting…"
        SupabaseClientManager.ConnectionState.Disconnected -> "Offline"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            Text(
                "CONTROLLER",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

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
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "MISSION CONTROL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Live Tracker controller app. Pairs with the Live Tracker Android service " +
                            "and surfaces device telemetry + ambient audio monitor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (pinResetDialog) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            onDismissRequest = { pinResetDialog = false },
            title = { Text("Reset Master PIN?") },
            text = { Text("You'll be required to set a new PIN on next launch. This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Clear the stored hash so next launch forces setPin flow.
                        val prefs = context.getSharedPreferences(
                            "secure_prefs", android.content.Context.MODE_PRIVATE
                        )
                        prefs.edit().remove("master_pin_hash").apply()
                        pinResetDialog = false
                        Toast.makeText(context, "PIN cleared. Restart to set a new PIN.", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("RESET") }
            },
            dismissButton = {
                TextButton(onClick = { pinResetDialog = false }) { Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        )
    }

    if (themeDialog) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            onDismissRequest = { themeDialog = false },
            title = { Text("Select Theme") },
            text = {
                Column {
                    ThemeMode.values().forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ThemeManager.setTheme(mode)
                                    themeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentTheme == mode,
                                onClick = {
                                    ThemeManager.setTheme(mode)
                                    themeDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(mode.name, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { themeDialog = false }) { Text("CLOSE", color = MaterialTheme.colorScheme.primary) }
            }
        )
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(14.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

package com.aistudio.missioncontrol.pxytwe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.missioncontrol.pxytwe.audio.AudioMonitorRepository
import com.aistudio.missioncontrol.pxytwe.audio.AudioMonitorViewModel
import kotlinx.coroutines.launch

/**
 * Mic-tab landing surface: lists recent trackers that have ever uploaded a chunk,
 * lets the operator flip one on/off without leaving the dashboard. Tapping the
 * row's status icon deep-links into the streaming console for the selected device.
 */
@Composable
fun MicHomeScreen(
    onNavigateToMicMonitor: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val activeDeviceId by AudioMonitorRepository.activeSession.collectAsState()
    val monitorStatus by AudioMonitorRepository.status.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val vm = remember {
        AudioMonitorViewModel(context.applicationContext as android.app.Application)
    }
    var recentDevices by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        recentDevices = com.aistudio.missioncontrol.pxytwe.AppState.activeDevices.keys.toList()
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(
                "MIC CONTROLLER",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (activeDeviceId != null) "Uplink active on $activeDeviceId"
                else "Pick a tracker to listen in",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        if (loading) {
            // Ponytail: keep this branch so first paint doesn't show "no devices" for a frame.
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Loading recent trackers…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        if (recentDevices.isEmpty()) {
            EmptyMicState()
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 0.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = recentDevices, key = { device: String -> device }) { device ->
                MicRow(
                    deviceId = device,
                    isActive = activeDeviceId == device,
                    statusLabel = if (activeDeviceId == device) monitorStatus.name else null,
                    onTogglePower = {
                        scope.launch {
                            if (activeDeviceId == device) {
                                vm.stop()
                            } else {
                                vm.start(device)
                            }
                        }
                    },
                    onRowClick = { onNavigateToMicMonitor(device) },
                )
            }
        }
    }
}

@Composable
private fun MicRow(
    deviceId: String,
    isActive: Boolean,
    statusLabel: String?,
    onTogglePower: () -> Unit,
    onRowClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    deviceId.uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    statusLabel ?: "TAP TO LISTEN",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                )
            }
            IconButton(onClick = onRowClick) {
                Icon(Icons.Filled.GraphicEq, contentDescription = "Open monitor", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(
                onClick = onTogglePower,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isActive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (isActive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Icon(
                    if (isActive) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (isActive) "Stop listening" else "Start listening",
                )
            }
        }
    }
}

@Composable
private fun EmptyMicState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.MicOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "NO TRACKERS YET",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Once a tracker uploads audio chunks, it'll appear here ready to listen to.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

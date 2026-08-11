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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.clickable
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.Bolt
import com.aistudio.missioncontrol.pxytwe.ui.theme.StatusLive
import com.aistudio.missioncontrol.pxytwe.ui.theme.StatusConnecting
import com.aistudio.missioncontrol.pxytwe.ui.theme.StatusReconnecting
import com.aistudio.missioncontrol.pxytwe.ui.theme.StatusOffline
import com.aistudio.missioncontrol.pxytwe.ui.theme.StatusUnknown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

sealed class DashboardTab(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Map : DashboardTab("fleet_map", "Map", Icons.Filled.Map, Icons.Outlined.Map)
    object Devices : DashboardTab("devices", "Devices", Icons.Filled.Devices, Icons.Outlined.Devices)
    object Mic : DashboardTab("mic_home", "Mic", Icons.Filled.GraphicEq, Icons.Outlined.GraphicEq)
    object Signals : DashboardTab("signals", "Signals", Icons.Filled.Bolt, Icons.Outlined.Bolt)
    object Settings : DashboardTab("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun MainDashboardScreen(
    onNavigateToMicMonitor: (String) -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val tabs = listOf(
        DashboardTab.Map,
        DashboardTab.Devices,
        DashboardTab.Mic,
        DashboardTab.Signals,
        DashboardTab.Settings,
    )

    Scaffold(
        topBar = {
            // Ponytail: E3 was a silent 7s retry loop. Tiny strip with a
            // color-coded dot. Updating state is read from a single state
            // flow on SupabaseClientManager — no recomposition churn,
            // no subscriptions to wire up here.
            RealtimeStatusStrip()
        },
        bottomBar = {}
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            NavHost(
                navController = navController,
                startDestination = DashboardTab.Map.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (currentRoute == DashboardTab.Map.route) 0.dp else 100.dp),
            enterTransition = {
                androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(220)) +
                androidx.compose.animation.scaleIn(initialScale = 0.98f, animationSpec = androidx.compose.animation.core.tween(220))
            },
            exitTransition = {
                androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(180)) +
                androidx.compose.animation.scaleOut(targetScale = 1.02f, animationSpec = androidx.compose.animation.core.tween(180))
            }
        ) {
            composable(DashboardTab.Map.route) {
                FleetScreen(onNavigateToMicMonitor = onNavigateToMicMonitor)
            }
            composable(DashboardTab.Devices.route) {
                DevicesScreen(onNavigateToMicMonitor = onNavigateToMicMonitor)
            }
            composable(DashboardTab.Mic.route) {
                MicHomeScreen(onNavigateToMicMonitor = onNavigateToMicMonitor)
            }
            composable(DashboardTab.Signals.route) {
                SignalsScreen()
            }
            composable(DashboardTab.Settings.route) {
                SettingsScreen()
            }
        }

        val isDrawingGeofence = com.aistudio.missioncontrol.pxytwe.AppState.isDrawingGeofence.value
        androidx.compose.animation.AnimatedVisibility(
            visible = !isDrawingGeofence,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut()
        ) {
            FloatingNavigationBar(
                currentRoute = currentRoute,
                tabs = tabs,
                onNavigate = { tab ->
                    val isSelected = currentRoute == tab.route
                    if (!isSelected) {
                        navController.navigate(tab.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
        }
    }
}

@Composable
private fun RealtimeStatusStrip() {
    val state by com.aistudio.missioncontrol.pxytwe.SupabaseClientManager
        .connectionState
        .collectAsState()
    val (label, color) = when (state) {
        com.aistudio.missioncontrol.pxytwe.SupabaseClientManager.ConnectionState.Connected ->
            "LIVE" to StatusLive
        com.aistudio.missioncontrol.pxytwe.SupabaseClientManager.ConnectionState.Connecting ->
            "CONNECTING…" to StatusConnecting
        com.aistudio.missioncontrol.pxytwe.SupabaseClientManager.ConnectionState.Reconnecting ->
            "RECONNECTING…" to StatusReconnecting
        com.aistudio.missioncontrol.pxytwe.SupabaseClientManager.ConnectionState.Disconnected ->
            "OFFLINE" to StatusOffline
        else -> "—" to StatusUnknown
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier = androidx.compose.ui.Modifier
                .size(8.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun FloatingNavigationBar(
    currentRoute: String?,
    tabs: List<DashboardTab>,
    onNavigate: (DashboardTab) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Surface(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val isSelected = currentRoute == tab.route
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onNavigate(tab) }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.title,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}

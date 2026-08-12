package com.aistudio.missioncontrol.pxytwe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    object Events : DashboardTab("events", "Events", Icons.Filled.History, Icons.Outlined.History)
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
        DashboardTab.Events,
        DashboardTab.Settings,
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {}
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Let the NavHost (and Map) fill the entire screen (edge-to-edge)
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
            composable(DashboardTab.Events.route) {
                EventsScreen()
            }
            composable(DashboardTab.Settings.route) {
                SettingsScreen()
            }
        }

        // Removed local RealtimeStatusStrip - it's now handled globally in MainActivity

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
fun PlaceholderScreen(title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                            tint = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
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

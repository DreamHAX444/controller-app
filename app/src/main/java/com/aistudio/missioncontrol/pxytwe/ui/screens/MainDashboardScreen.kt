package com.aistudio.missioncontrol.pxytwe.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aistudio.missioncontrol.pxytwe.AppState

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
            modifier = Modifier.fillMaxSize()
        ) {
            // Keep NavHost solid fillMaxSize edge-to-edge with no conditional padding
            // so individual screens transition seamlessly without jumping.
            NavHost(
                navController = navController,
                startDestination = DashboardTab.Map.route,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn(animationSpec = tween(180)) },
                exitTransition = { fadeOut(animationSpec = tween(180)) }
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
                    SettingsScreen(
                        onNavigateToSiren = { navController.navigate("siren_settings") }
                    )
                }
                composable("siren_settings") { 
                    SirenSettingsScreen(onBack = { navController.popBackStack() }) 
                }
            }

            val isDrawingGeofence = AppState.isDrawingGeofence.value
            val isSubScreen = currentRoute == "siren_settings"
            androidx.compose.animation.AnimatedVisibility(
                visible = !isDrawingGeofence && !isSubScreen,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(animationSpec = tween(200)),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(animationSpec = tween(150))
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val isSelected = currentRoute == tab.route
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onNavigate(tab) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.title,
                            tint = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}

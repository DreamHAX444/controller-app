package com.aistudio.missioncontrol.pxytwe.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aistudio.missioncontrol.pxytwe.AppState
import com.aistudio.missioncontrol.pxytwe.ui.navigation.AppRoute
import kotlinx.coroutines.launch

sealed class DashboardTab(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Map : DashboardTab(AppRoute.Fleet.route, "Map", Icons.Filled.Map, Icons.Outlined.Map)
    object Devices : DashboardTab(AppRoute.Devices.route, "Devices", Icons.Filled.Devices, Icons.Outlined.Devices)
    object Signals : DashboardTab(AppRoute.Signals.route, "Signals", Icons.Filled.Bolt, Icons.Outlined.Bolt)
    object Events : DashboardTab(AppRoute.History.route, "Events", Icons.Filled.History, Icons.Outlined.History)
    object Settings : DashboardTab(AppRoute.Settings.route, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun MainDashboardScreen(
    rootNavController: NavController
) {
    val haptic = LocalHapticFeedback.current
    val tabs = remember {
        listOf(
            DashboardTab.Map,
            DashboardTab.Devices,
            DashboardTab.Signals,
            DashboardTab.Events,
            DashboardTab.Settings
        )
    }

    val innerNavController = rememberNavController()
    val navBackStackEntry by innerNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: AppRoute.Fleet.route
    
    val isDrawingGeofence = AppState.isDrawingGeofence.value
    
    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = !isDrawingGeofence,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(animationSpec = tween(200)),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(animationSpec = tween(150))
            ) {
                DynamicCapsuleNavigationDock(
                    currentRoute = currentRoute,
                    tabs = tabs,
                    onNavigate = { tab ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        innerNavController.navigate(tab.route) {
                            popUpTo(innerNavController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = innerNavController,
            startDestination = AppRoute.Fleet.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(AppRoute.Fleet.route) {
                FleetScreen()
            }
            composable(AppRoute.Devices.route) {
                DevicesScreen(
                    onNavigateToHistory = {
                        innerNavController.navigate(AppRoute.History.route) {
                            popUpTo(innerNavController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToDeviceDetails = { deviceId ->
                        rootNavController.navigate(AppRoute.DeviceDetails.createRoute(deviceId))
                    }
                )
            }
            composable(AppRoute.Signals.route) {
                SignalsScreen()
            }
            composable(AppRoute.History.route) {
                EventsScreen(
                    onNavigateToHistory = {
                        // The user asked to remove activeSubScreen, Events is History in this tab setup
                    }
                )
            }
            composable(AppRoute.Settings.route) {
                SettingsScreen(
                    onNavigateToSiren = {
                        rootNavController.navigate(AppRoute.SirenSettings.route)
                    }
                )
            }
        }
    }
}

@Composable
private fun DynamicCapsuleNavigationDock(
    currentRoute: String,
    tabs: List<DashboardTab>,
    onNavigate: (DashboardTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val isSelected = currentRoute == tab.route

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
                    border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)) else null,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onNavigate(tab) }
                ) {
                    Row(
                        modifier = Modifier
                            .animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                            .padding(
                                horizontal = if (isSelected) 14.dp else 10.dp,
                                vertical = 8.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        if (isSelected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tab.title.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

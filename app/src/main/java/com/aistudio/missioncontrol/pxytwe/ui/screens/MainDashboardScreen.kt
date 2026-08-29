package com.aistudio.missioncontrol.pxytwe.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.missioncontrol.pxytwe.AppState
import com.aistudio.missioncontrol.pxytwe.audio.AudioMonitorRepository
import kotlinx.coroutines.launch

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
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    
    val tabs = remember {
        listOf(
            DashboardTab.Map,
            DashboardTab.Devices,
            DashboardTab.Mic,
            DashboardTab.Signals,
            DashboardTab.Events,
            DashboardTab.Settings,
        )
    }

    // ═════════════════════════════════════════════════════════════════════
    // 1. ULTRA-FAST PRE-WARMED PAGER ENGINE (Zero-Latency Screen Switching)
    // ═════════════════════════════════════════════════════════════════════
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })
    var activeSubScreen by rememberSaveable { mutableStateOf<String?>(null) }

    // Handle Android system back button when a sub-screen (like Siren or History) is active
    BackHandler(enabled = activeSubScreen != null) {
        activeSubScreen = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Main Pager keeping all 6 screens alive & pre-rendered in memory
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            beyondViewportPageCount = 5,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            when (pageIndex) {
                0 -> FleetScreen(onNavigateToMicMonitor = onNavigateToMicMonitor)
                1 -> DevicesScreen(
                    onNavigateToMicMonitor = onNavigateToMicMonitor,
                    onNavigateToHistory = { activeSubScreen = "activity_history" }
                )
                2 -> MicHomeScreen(onNavigateToMicMonitor = onNavigateToMicMonitor)
                3 -> SignalsScreen()
                4 -> EventsScreen(
                    onNavigateToHistory = { activeSubScreen = "activity_history" }
                )
                5 -> SettingsScreen(
                    onNavigateToSiren = { activeSubScreen = "siren_settings" }
                )
            }
        }

        // ═════════════════════════════════════════════════════════════════════
        // 2. SUB-SCREENS (GPU-Accelerated Slide-In Transitions)
        // ═════════════════════════════════════════════════════════════════════
        AnimatedVisibility(
            visible = activeSubScreen != null,
            enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(180, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(140)),
            exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(140, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(100)),
            modifier = Modifier.fillMaxSize()
        ) {
            when (activeSubScreen) {
                "siren_settings" -> SirenSettingsScreen(onBack = { activeSubScreen = null })
                "activity_history" -> HistoryScreen(
                    onBack = { activeSubScreen = null },
                    micCallback = onNavigateToMicMonitor
                )
            }
        }

        // ═════════════════════════════════════════════════════════════════════
        // 3. DYNAMIC CAPSULE NAVIGATION DOCK (Springy Elastic Capsule)
        // ═════════════════════════════════════════════════════════════════════
        val isDrawingGeofence = AppState.isDrawingGeofence.value
        val isSubScreen = activeSubScreen != null
        val currentRoute = tabs[pagerState.currentPage].route
        
        AnimatedVisibility(
            visible = !isDrawingGeofence && !isSubScreen,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(animationSpec = tween(200)),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(animationSpec = tween(150))
        ) {
            DynamicCapsuleNavigationDock(
                currentRoute = currentRoute,
                tabs = tabs,
                onNavigate = { tab ->
                    val targetIndex = tabs.indexOf(tab)
                    if (targetIndex != -1 && pagerState.currentPage != targetIndex) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        scope.launch {
                            pagerState.animateScrollToPage(
                                page = targetIndex,
                                animationSpec = tween(180, easing = FastOutSlowInEasing)
                            )
                        }
                    }
                }
            )
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
    val haptic = LocalHapticFeedback.current
    val activeMicSession by AudioMonitorRepository.activeSession.collectAsState()

    Surface(
        modifier = modifier.fillMaxWidth(),
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
                val isMicActive = tab == DashboardTab.Mic && activeMicSession != null

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
                    border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)) else null,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            onNavigate(tab)
                        }
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

                            // Live Mic Pulsing Indicator Dot
                            if (isMicActive) {
                                val infiniteTransition = rememberInfiniteTransition(label = "nav_mic_pulse")
                                val alpha by infiniteTransition.animateFloat(
                                    initialValue = 0.4f,
                                    targetValue = 1f,
                                    animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
                                    label = "mic_alpha"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .offset(x = 2.dp, y = (-2).dp)
                                        .graphicsLayer { this.alpha = alpha }
                                        .clip(CircleShape)
                                        .background(Color(0xFF4ADE80))
                                )
                            }
                        }

                        // Animated label reveal on active tab
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

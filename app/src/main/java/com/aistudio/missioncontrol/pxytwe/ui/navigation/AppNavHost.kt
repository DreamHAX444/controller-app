package com.aistudio.missioncontrol.pxytwe.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aistudio.missioncontrol.pxytwe.ui.screens.AppStartupSplashScreen
import com.aistudio.missioncontrol.pxytwe.ui.screens.MainDashboardScreen
import com.aistudio.missioncontrol.pxytwe.ui.screens.PinLockScreen
import com.aistudio.missioncontrol.pxytwe.ui.screens.ScreenMonitorScreen
import com.aistudio.missioncontrol.pxytwe.ui.screens.DeviceDetailsScreen
import com.aistudio.missioncontrol.pxytwe.ui.screens.CameraAccessScreen

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = AppRoute.Startup.route
) {
    Box(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(180)) },
            exitTransition = { androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(140)) },
            popEnterTransition = { androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(180)) },
            popExitTransition = { androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(140)) }
        ) {
            composable(AppRoute.Startup.route) {
                AppStartupSplashScreen(
                    onBootComplete = {
                        navController.navigate(AppRoute.PinLock.route) {
                            popUpTo(AppRoute.Startup.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(AppRoute.PinLock.route) {
                PinLockScreen(
                    onUnlockSuccess = {
                        navController.navigate(AppRoute.Dashboard.route) {
                            popUpTo(AppRoute.PinLock.route) { inclusive = true }
                        }
                    }
                )
            }
            
            composable(AppRoute.Dashboard.route) {
                MainDashboardScreen(
                    rootNavController = navController
                )
            }

            composable(
                route = AppRoute.DeviceDetails.route,
                arguments = AppRoute.DeviceDetails.arguments
            ) { backStackEntry ->
                val deviceId = backStackEntry.arguments?.getString("deviceId") ?: return@composable
                DeviceDetailsScreen(
                    deviceId = deviceId,
                    onBack = { navController.popBackStack() },
                    onNavigateToScreenMonitor = { sid, profile ->
                        navController.navigate(AppRoute.ScreenMonitor.createRoute(deviceId, sid, profile))
                    },
                    onNavigateToCameraAccess = {
                        navController.navigate(AppRoute.CameraAccess.createRoute(deviceId))
                    }
                )
            }

            composable(
                route = AppRoute.CameraAccess.route,
                arguments = AppRoute.CameraAccess.arguments
            ) { backStackEntry ->
                val deviceId = backStackEntry.arguments?.getString("deviceId") ?: return@composable
                CameraAccessScreen(
                    deviceId = deviceId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = AppRoute.ScreenMonitor.route,
                arguments = AppRoute.ScreenMonitor.arguments
            ) { backStackEntry ->
                val deviceId = backStackEntry.arguments?.getString("deviceId") ?: return@composable
                val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
                val qualityProfile = backStackEntry.arguments?.getString("qualityProfile") ?: "BALANCED"

                ScreenMonitorScreen(
                    deviceId = deviceId,
                    sessionId = sessionId,
                    qualityProfile = qualityProfile,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

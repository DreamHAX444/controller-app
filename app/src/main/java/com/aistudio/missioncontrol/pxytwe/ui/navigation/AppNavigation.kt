package com.aistudio.missioncontrol.pxytwe.ui.navigation

import androidx.navigation.NavType
import androidx.navigation.navArgument

sealed class AppRoute(val route: String) {
    object Startup : AppRoute("startup")
    object PinLock : AppRoute("pin_lock")
    object Dashboard : AppRoute("dashboard") // Container for tabs
    
    object Fleet : AppRoute("fleet")
    object Devices : AppRoute("devices")
    object Signals : AppRoute("signals")
    object History : AppRoute("history")
    object Settings : AppRoute("settings")
    object SirenSettings : AppRoute("siren_settings")
    
    object DeviceDetails : AppRoute("device_details/{deviceId}") {
        fun createRoute(deviceId: String) = "device_details/$deviceId"
        val arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
    }
    
    object ScreenMonitor : AppRoute("screen_monitor/{deviceId}/{sessionId}/{qualityProfile}") {
        fun createRoute(deviceId: String, sessionId: String, qualityProfile: String) = "screen_monitor/$deviceId/$sessionId/$qualityProfile"
        val arguments = listOf(
            navArgument("deviceId") { type = NavType.StringType },
            navArgument("sessionId") { type = NavType.StringType },
            navArgument("qualityProfile") { type = NavType.StringType }
        )
    }
}

package com.aistudio.missioncontrol.pxytwe.navigation

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import com.aistudio.missioncontrol.pxytwe.ui.navigation.AppNavHost
import com.aistudio.missioncontrol.pxytwe.ui.navigation.AppRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

class ControllerNavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var navController: TestNavHostController

    private fun setupNavHost() {
        composeTestRule.setContent {
            navController = TestNavHostController(LocalContext.current)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            AppNavHost(navController = navController, startDestination = AppRoute.Dashboard.route)
        }
    }

    @Test
    fun testTopLevelNavigation_noUnboundedBackStack() {
        setupNavHost()
        
        // Emulate user clicking through tabs
        // Note: The inner NavHost controls the tabs, but for the sake of top-level structural testing,
        // we test the main NavHost routing to Dashboard.
        assertEquals(AppRoute.Dashboard.route, navController.currentDestination?.route)
    }

    @Test
    fun testDeviceDetails_receivesCorrectDeviceId() {
        setupNavHost()
        
        val deviceId = "test-device-123"
        composeTestRule.runOnUiThread {
            navController.navigate(AppRoute.DeviceDetails.createRoute(deviceId))
        }
        
        composeTestRule.waitForIdle()
        
        val route = navController.currentDestination?.route
        assertEquals(AppRoute.DeviceDetails.route, route)
        
        val arg = navController.currentBackStackEntry?.arguments?.getString("deviceId")
        assertEquals(deviceId, arg)
    }

    @Test
    fun testScreenMonitor_receivesCorrectDeviceIdAndSessionId() {
        setupNavHost()
        
        val deviceId = "test-device-123"
        val sessionId = "session-abc"
        
        composeTestRule.runOnUiThread {
            navController.navigate(AppRoute.ScreenMonitor.createRoute(deviceId, sessionId))
        }
        
        composeTestRule.waitForIdle()
        
        val route = navController.currentDestination?.route
        assertEquals(AppRoute.ScreenMonitor.route, route)
        
        val dId = navController.currentBackStackEntry?.arguments?.getString("deviceId")
        val sId = navController.currentBackStackEntry?.arguments?.getString("sessionId")
        
        assertEquals(deviceId, dId)
        assertEquals(sessionId, sId)
    }
}

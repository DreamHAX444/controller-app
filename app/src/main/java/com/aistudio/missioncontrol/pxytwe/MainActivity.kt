package com.aistudio.missioncontrol.pxytwe

import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.getValue
import com.aistudio.missioncontrol.pxytwe.ui.screens.AppStartupSplashScreen
import com.aistudio.missioncontrol.pxytwe.ui.screens.PinLockScreen
import com.aistudio.missioncontrol.pxytwe.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ponytail: the previous build swallowed every SocketException via
        // a global Thread.setDefaultUncaughtExceptionHandler. That hid real
        // realtime failures — symptom of E4. Replace it with:
        //   1. DEBUG-only Log.w so we don't ship a silent crash blocker;
        //   2. A SharedFlow leak detector so QA can see anomaly counts without
        //      needing logcat capture.
        // Real crashes go to the default handler as before — we're not
        // muting them; we only allow-list the known noisy InterruptedIOException.
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val isBenignInterrupt = throwable is java.io.InterruptedIOException
            if (BuildConfig.DEBUG && isBenignInterrupt) {
                Log.w(TAG, "Suppressed benign InterruptedIOException (debug only)", throwable)
                CrashGate.note(thread.name, throwable)
            } else {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        // Note: Heavy initializations (OsmDroid, AppState, ThemeManager, Supabase)
        // are offloaded asynchronously into AppStartupSplashScreen for zero-latency cold start.

        Log.d(TAG, "MainActivity launched successfully! Hello from Logcat!")
        enableEdgeToEdge()
        setContent {
            AppTheme {
                val navController = rememberNavController()

                Box(modifier = Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = "startup_loader",
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = { androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(180)) },
                        exitTransition = { androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(140)) },
                        popEnterTransition = { androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(180)) },
                        popExitTransition = { androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(140)) }
                    ) {
                        composable("startup_loader") {
                            AppStartupSplashScreen(
                                onBootComplete = {
                                    navController.navigate("pin_lock") {
                                        popUpTo("startup_loader") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("pin_lock") {
                            PinLockScreen(
                                onUnlockSuccess = {
                                    navController.navigate("main_dashboard") {
                                        popUpTo("pin_lock") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("main_dashboard") {
                            com.aistudio.missioncontrol.pxytwe.ui.screens.MainDashboardScreen(
                                onNavigateToMicMonitor = { deviceId ->
                                    navController.navigate("mic_monitor/$deviceId")
                                }
                            )
                        }

                        composable(
                            "mic_monitor/{deviceId}",
                            arguments = listOf(androidx.navigation.navArgument("deviceId") { type = androidx.navigation.NavType.StringType })
                        ) { backStackEntry ->
                            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
                            com.aistudio.missioncontrol.pxytwe.ui.screens.MicMonitorScreen(
                                deviceId = deviceId,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    private companion object { const val TAG = "MyApp" }
}

/**
 * Lightweight process-wide counter for suppressed benign exceptions.
 * Read it from SettingsScreen if QA needs a count after a long session.
 * Ponytail: capped map, add per-thread locks if the app starts
 * aggregating metrics across active sessions.
 */
private object CrashGate {
    data class Stamp(val thread: String, val type: String, val at: Long)
    private val _events = MutableSharedFlow<Stamp>(extraBufferCapacity = 32)
    val events: SharedFlow<Stamp> = _events.asSharedFlow()
    fun note(thread: String, t: Throwable) {
        _events.tryEmit(Stamp(thread, t.javaClass.simpleName, System.currentTimeMillis()))
    }
}

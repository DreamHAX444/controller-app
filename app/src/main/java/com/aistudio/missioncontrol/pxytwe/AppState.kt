package com.aistudio.missioncontrol.pxytwe

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch

data class DeviceTelemetry(
    val name: String,
    val lat: Double,
    val lon: Double,
    val battery: Int,
    val speed: Float,
    val signal: Int = -100,
    val lastSeen: Long = 0L,
    val history: List<Pair<Double, Double>> = emptyList(),
    val updateCount: Int = 0,
    val locTimestamp: Long = 0L,
    val heading: Float = 0f,
    val altitude: Double = 0.0,
    val pitch: Float = 0f,
    val roll: Float = 0f,
    val pressure: Float = 0f,
    val charging: Boolean = false,
    val ping: Long = -1L,
    val cameras: List<String> = emptyList()
)

object AppState {
    val selectedDevice = mutableStateOf<String?>(null)
    val isDrawingGeofence = mutableStateOf(false)
    val isDebugDeviceMode = mutableStateOf(false)
    
    // Live device telemetry received from Supabase
    val activeDevices = mutableStateMapOf<String, DeviceTelemetry>()

    val deviceCurrentZones = androidx.compose.runtime.mutableStateMapOf<String, String>()

    // Siren Preferences
    val isSirenEnabled = mutableStateOf(true)
    val sirenType = mutableStateOf("ALARM")
    val sirenDuration = mutableStateOf(3000L)

    private val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
    private var isInitialized = false
    private var sharedPrefs: android.content.SharedPreferences? = null
    private var appContext: android.content.Context? = null

    fun initialize(context: android.content.Context) {
        if (isInitialized) return
        isInitialized = true
        appContext = context.applicationContext
        sharedPrefs = context.getSharedPreferences("geofence_prefs", android.content.Context.MODE_PRIVATE)
        
        // Load Siren Preferences
        isSirenEnabled.value = sharedPrefs?.getBoolean("siren_enabled", true) ?: true
        sirenType.value = sharedPrefs?.getString("siren_type", "ALARM") ?: "ALARM"
        sirenDuration.value = sharedPrefs?.getLong("siren_duration", 3000L) ?: 3000L
        
        EventLogger.initialize(context)

        appScope.launch {
            try {
                // Fetch initial locations first
                val initialLocs = SupabaseClientManager.getInitialLocations()
                val fencesStr = sharedPrefs?.getString("saved_fences", "") ?: ""
                val fences = com.aistudio.missioncontrol.pxytwe.utils.GeofenceUtils.deserializeGeofences(fencesStr)

                initialLocs.forEach { initialLoc ->
                    val dev = DeviceTelemetry(
                        name = initialLoc.device_id,
                        lat = initialLoc.latitude,
                        lon = initialLoc.longitude,
                        heading = initialLoc.bearing,
                        battery = 100,
                        speed = 0f,
                        lastSeen = parseSupabaseDate(initialLoc.created_at)
                    )
                    activeDevices[dev.name] = dev
                    
                    val locGeo = org.osmdroid.util.GeoPoint(initialLoc.latitude, initialLoc.longitude)
                    val currentZone = fences.firstOrNull { com.aistudio.missioncontrol.pxytwe.utils.GeofenceUtils.isPointInPolygon(locGeo, it.points) }?.name
                    if (currentZone != null) {
                        deviceCurrentZones[dev.name] = currentZone
                    }
                }

                // Connect to realtime
                SupabaseClientManager.connectRealtime()
                SupabaseClientManager.startListeningForPongs()
                val locationsFlow = SupabaseClientManager.listenToLocations()
                locationsFlow.collect { loc ->
                    processLocationUpdate(loc.device_id, loc.latitude, loc.longitude, loc.bearing)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun processLocationUpdate(deviceId: String, lat: Double, lon: Double, heading: Float) {
        val existing = activeDevices[deviceId]
        val newHistory = existing?.history?.toMutableList() ?: mutableListOf()
        newHistory.add(Pair(lat, lon))
        
        val updatedDev = DeviceTelemetry(
            name = deviceId,
            lat = lat,
            lon = lon,
            heading = heading,
            battery = 100,
            speed = 0f,
            lastSeen = System.currentTimeMillis(),
            history = if (newHistory.size > 50) newHistory.takeLast(50) else newHistory,
            updateCount = (existing?.updateCount ?: 0) + 1,
            locTimestamp = System.currentTimeMillis()
        )
        activeDevices[deviceId] = updatedDev

        val currentFencesStr = sharedPrefs?.getString("saved_fences", "") ?: ""
        val currentFences = com.aistudio.missioncontrol.pxytwe.utils.GeofenceUtils.deserializeGeofences(currentFencesStr)
        val locGeo = org.osmdroid.util.GeoPoint(lat, lon)
        val currentZone = currentFences.firstOrNull { com.aistudio.missioncontrol.pxytwe.utils.GeofenceUtils.isPointInPolygon(locGeo, it.points) }?.name
        val previousZone = deviceCurrentZones[deviceId]
        
        if (currentZone != previousZone) {
            if (previousZone != null) {
                EventLogger.logEvent(deviceId, "GEOFENCE_EXIT", "Exited $previousZone")
            }
            if (currentZone != null) {
                EventLogger.logEvent(deviceId, "GEOFENCE_ENTER", "Entered $currentZone")
                playSiren()
            }
            if (currentZone == null) {
                deviceCurrentZones.remove(deviceId)
            } else {
                deviceCurrentZones[deviceId] = currentZone
            }
        }
    }

    fun injectDebugLocation(lat: Double, lon: Double) {
        processLocationUpdate("DEBUG-DEV-1", lat, lon, 0f)
    }

    private fun parseSupabaseDate(dateString: String): Long {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val cleanDate = dateString.substringBeforeLast("+").substringBeforeLast("Z").take(19)
            sdf.parse(cleanDate)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    fun saveSirenPrefs(enabled: Boolean, type: String, duration: Long) {
        isSirenEnabled.value = enabled
        sirenType.value = type
        sirenDuration.value = duration
        sharedPrefs?.edit()?.apply {
            putBoolean("siren_enabled", enabled)
            putString("siren_type", type)
            putLong("siren_duration", duration)
            apply()
        }
    }

    fun previewSiren() {
        playSirenInternal()
    }

    private fun playSiren() {
        if (!isSirenEnabled.value) return
        playSirenInternal()
    }

    private fun playSirenInternal() {
        appContext?.let { ctx ->
            try {
                val uri = if (sirenType.value == "ALARM") {
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                        ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                } else {
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                }
                
                val ringtone = android.media.RingtoneManager.getRingtone(ctx, uri)
                ringtone?.play()
                
                // Stop after configured duration to avoid infinite alarm ringing
                appScope.launch {
                    kotlinx.coroutines.delay(sirenDuration.value)
                    if (ringtone?.isPlaying == true) {
                        ringtone.stop()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}


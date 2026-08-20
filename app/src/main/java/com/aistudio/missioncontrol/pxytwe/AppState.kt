package com.aistudio.missioncontrol.pxytwe

import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

data class DeviceTelemetry(
    val name: String,
    val lat: Double,
    val lon: Double,
    val battery: Int = 100,
    val speed: Float = 0f,
    val signal: Int = -85,
    val networkType: String = "4G LTE",
    val lastSeen: Long = 0L,
    val history: List<Pair<Double, Double>> = emptyList(),
    val updateCount: Int = 0,
    val locTimestamp: Long = 0L,
    val heading: Float = 0f,
    val altitude: Double = 0.0,
    val accuracy: Float = 0f,
    val pitch: Float = 0f,
    val roll: Float = 0f,
    val pressure: Float = 0f,
    val charging: Boolean = false,
    val ping: Long = -1L,
    val cameras: List<String> = emptyList(),
    val isLocationOn: Boolean = true
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
            // Ponytail: Combined retry loop for initial state and realtime.
            // If the network is down (UnresolvedAddressException), we keep 
            // exponential backoff until we can reach Supabase.
            var retryDelay = 2000L
            while (true) {
                try {
                    // 1. Fetch initial locations first to populate the map
                    val initialLocs = SupabaseClientManager.getInitialLocations()
                    val fencesStr = sharedPrefs?.getString("saved_fences", "") ?: ""
                    val fences = com.aistudio.missioncontrol.pxytwe.utils.GeofenceUtils.deserializeGeofences(fencesStr)

                    initialLocs.forEach { initialLoc ->
                        val existing = activeDevices[initialLoc.device_id]
                        val isFallback = initialLoc.latitude == 0.0 && initialLoc.longitude == 0.0 && existing != null && (existing.lat != 0.0 || existing.lon != 0.0)
                        val actualLat = if (isFallback) existing!!.lat else initialLoc.latitude
                        val actualLon = if (isFallback) existing!!.lon else initialLoc.longitude
                        val parsedTime = parseSupabaseDate(initialLoc.created_at)

                        val dev = DeviceTelemetry(
                            name = initialLoc.device_id,
                            lat = actualLat,
                            lon = actualLon,
                            heading = initialLoc.bearing,
                            altitude = initialLoc.altitude ?: 0.0,
                            accuracy = initialLoc.accuracy,
                            speed = initialLoc.speed ?: 0f,
                            battery = (initialLoc.battery_level ?: 100f).toInt(),
                            charging = initialLoc.charging ?: false,
                            signal = initialLoc.signal_dbm ?: -85,
                            networkType = initialLoc.network_type ?: "4G LTE",
                            pitch = initialLoc.pitch ?: 0f,
                            roll = initialLoc.roll ?: 0f,
                            pressure = initialLoc.pressure ?: 0f,
                            lastSeen = parsedTime
                        )
                        // Only add if we don't have it or if the new one is newer
                        if (existing == null || dev.lastSeen > existing.lastSeen) {
                            activeDevices[dev.name] = dev
                        }
                        
                        val locGeo = org.osmdroid.util.GeoPoint(actualLat, actualLon)
                        val currentZone = fences.firstOrNull { com.aistudio.missioncontrol.pxytwe.utils.GeofenceUtils.isPointInPolygon(locGeo, it.points) }?.name
                        if (currentZone != null) {
                            deviceCurrentZones[dev.name] = currentZone
                        }
                    }

                    // 2. Connect to realtime
                    SupabaseClientManager.connectRealtime()
                    SupabaseClientManager.startListeningForPongs()
                    val locationsFlow = SupabaseClientManager.listenToLocations()
                    
                    // Reset retry delay on success
                    retryDelay = 2000L
                    
                    // Collect forever until flow ends or exception
                    locationsFlow.collect { loc ->
                        processLocationUpdate(loc)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("AppState", "Supabase sync failed, retrying in ${retryDelay}ms", e)
                    kotlinx.coroutines.delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(30_000L)
                }
            }
        }
    }

    private suspend fun processLocationUpdate(loc: LocationData) {
        withContext(Dispatchers.Main) {
            val deviceId = loc.device_id
            val lat = loc.latitude
            val lon = loc.longitude
            val heading = loc.bearing
            val existing = activeDevices[deviceId]
            val isFallback = lat == 0.0 && lon == 0.0 && existing != null && (existing.lat != 0.0 || existing.lon != 0.0)
            val actualLat = if (isFallback) existing!!.lat else lat
            val actualLon = if (isFallback) existing!!.lon else lon
            val actualHeading = if (lat == 0.0 && lon == 0.0 && existing != null) existing.heading else heading

            val newHistory = existing?.history?.toMutableList() ?: mutableListOf()
            if (actualLat != 0.0 || actualLon != 0.0) {
                newHistory.add(Pair(actualLat, actualLon))
            }
            
            val updatedDev = DeviceTelemetry(
                name = deviceId,
                lat = actualLat,
                lon = actualLon,
                heading = actualHeading,
                altitude = loc.altitude ?: existing?.altitude ?: 0.0,
                accuracy = if (loc.accuracy > 0f) loc.accuracy else existing?.accuracy ?: 0f,
                speed = loc.speed ?: existing?.speed ?: 0f,
                battery = loc.battery_level?.toInt() ?: existing?.battery ?: 100,
                charging = loc.charging ?: existing?.charging ?: false,
                signal = loc.signal_dbm ?: existing?.signal ?: -85,
                networkType = loc.network_type ?: existing?.networkType ?: "4G LTE",
                pitch = loc.pitch ?: existing?.pitch ?: 0f,
                roll = loc.roll ?: existing?.roll ?: 0f,
                pressure = loc.pressure ?: existing?.pressure ?: 0f,
                lastSeen = System.currentTimeMillis(),
                history = if (newHistory.size > 50) newHistory.takeLast(50) else newHistory,
                updateCount = (existing?.updateCount ?: 0) + 1,
                locTimestamp = System.currentTimeMillis(),
                isLocationOn = if (lat == 0.0 && lon == 0.0 && existing != null) existing.isLocationOn else true
            )
            activeDevices[deviceId] = updatedDev

            val currentFencesStr = sharedPrefs?.getString("saved_fences", "") ?: ""
            val currentFences = com.aistudio.missioncontrol.pxytwe.utils.GeofenceUtils.deserializeGeofences(currentFencesStr)
            val locGeo = org.osmdroid.util.GeoPoint(actualLat, actualLon)
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
    }

    fun updateDeviceLocationStatus(deviceId: String, isLocationOn: Boolean) {
        appScope.launch(Dispatchers.Main) {
            val existing = activeDevices[deviceId]
            if (existing != null) {
                if (existing.isLocationOn != isLocationOn) {
                    activeDevices[deviceId] = existing.copy(isLocationOn = isLocationOn)
                    Log.i("AppState", "Updated location state for $deviceId: isLocationOn=$isLocationOn")
                }
            } else {
                activeDevices[deviceId] = DeviceTelemetry(
                    name = deviceId,
                    lat = 0.0,
                    lon = 0.0,
                    battery = 100,
                    speed = 0f,
                    lastSeen = System.currentTimeMillis(),
                    isLocationOn = isLocationOn
                )
            }
        }
    }

    fun handleIncomingCommand(payload: CommandPayload) {
        when (payload.command) {
            "location_state" -> {
                val isLocOn = payload.params?.uppercase() == "ON"
                updateDeviceLocationStatus(payload.device_id, isLocOn)
            }
            "status_response" -> {
                val params = payload.params ?: ""
                if (params.contains("Loc:OFF", ignoreCase = true)) {
                    updateDeviceLocationStatus(payload.device_id, false)
                } else if (params.contains("Loc:ON", ignoreCase = true)) {
                    updateDeviceLocationStatus(payload.device_id, true)
                }
            }
        }
    }

    fun injectDebugLocation(lat: Double, lon: Double) {
        appScope.launch {
            processLocationUpdate(
                LocationData(
                    device_id = "DEBUG-DEV-1",
                    latitude = lat,
                    longitude = lon,
                    accuracy = 5f,
                    bearing = 45f,
                    speed = 12.5f,
                    battery_level = 88f,
                    charging = false,
                    signal_dbm = -75,
                    network_type = "5G",
                    created_at = java.time.Instant.now().toString()
                )
            )
        }
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


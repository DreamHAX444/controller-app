package com.aistudio.missioncontrol.pxytwe

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
    
    // Live device telemetry in ultra-fast RAM state
    val activeDevices = mutableStateMapOf<String, DeviceTelemetry>()
    val deviceCurrentZones = mutableStateMapOf<String, String>()

    // Siren Preferences
    val isSirenEnabled = mutableStateOf(true)
    val sirenType = mutableStateOf("ALARM")
    val sirenDuration = mutableStateOf(3000L)

    private val appScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
    private var isInitialized = false
    private var sharedPrefs: SharedPreferences? = null
    private var appContext: Context? = null
    private var cacheFile: File? = null

    // In-memory cache of deserialized geofences to avoid parsing JSON on every packet
    private var cachedFencesList: List<com.aistudio.missioncontrol.pxytwe.utils.GeofenceData> = emptyList()
    private var lastFencesRawStr: String = ""

    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true
        appContext = context.applicationContext
        sharedPrefs = context.getSharedPreferences("geofence_prefs", Context.MODE_PRIVATE)
        cacheFile = File(context.filesDir, "telemetry_disk_cache.json")
        
        // 1. Load Siren Preferences
        isSirenEnabled.value = sharedPrefs?.getBoolean("siren_enabled", true) ?: true
        sirenType.value = sharedPrefs?.getString("siren_type", "ALARM") ?: "ALARM"
        sirenDuration.value = sharedPrefs?.getLong("siren_duration", 3000L) ?: 3000L
        
        // 2. Initialize Event Logger
        EventLogger.initialize(context)

        // 3. ZERO-LATENCY COLD START: Instantly load cached telemetry from Flash Disk into RAM
        loadTelemetryFromDisk()

        // 4. Launch background worker to sync with Supabase Realtime
        appScope.launch {
            var retryDelay = 2000L
            while (true) {
                try {
                    // Fetch initial locations from Supabase REST
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
                        if (existing == null || dev.lastSeen > existing.lastSeen) {
                            activeDevices[dev.name] = dev
                        }
                        
                        val locGeo = org.osmdroid.util.GeoPoint(actualLat, actualLon)
                        val currentZone = fences.firstOrNull { com.aistudio.missioncontrol.pxytwe.utils.GeofenceUtils.isPointInPolygon(locGeo, it.points) }?.name
                        if (currentZone != null) {
                            deviceCurrentZones[dev.name] = currentZone
                        }
                    }

                    // Save snapshot of refreshed devices to disk
                    saveTelemetryToDisk()

                    // Connect to Supabase Realtime WebSocket
                    SupabaseClientManager.connectRealtime()
                    SupabaseClientManager.startListeningForPongs()
                    val locationsFlow = SupabaseClientManager.listenToLocations()
                    
                    retryDelay = 2000L
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

        // Calculate Geofence breach on background thread
        val currentFencesStr = sharedPrefs?.getString("saved_fences", "") ?: ""
        if (currentFencesStr != lastFencesRawStr) {
            lastFencesRawStr = currentFencesStr
            cachedFencesList = com.aistudio.missioncontrol.pxytwe.utils.GeofenceUtils.deserializeGeofences(currentFencesStr)
        }
        val locGeo = org.osmdroid.util.GeoPoint(actualLat, actualLon)
        val currentZone = cachedFencesList.firstOrNull { com.aistudio.missioncontrol.pxytwe.utils.GeofenceUtils.isPointInPolygon(locGeo, it.points) }?.name
        val previousZone = deviceCurrentZones[deviceId]

        // Switch to Main thread ONLY for direct Compose state assignment
        withContext(Dispatchers.Main) {
            activeDevices[deviceId] = updatedDev

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

        // Persist to Disk asynchronously (debounced in IO)
        saveTelemetryToDisk()
    }

    // ═════════════════════════════════════════════════════════════════════
    // PERSISTENT FLASH DISK CACHE SYSTEM (Instant Cold-Start Hydration)
    // ═════════════════════════════════════════════════════════════════════
    private fun loadTelemetryFromDisk() {
        try {
            val file = cacheFile ?: return
            if (!file.exists()) return
            val jsonStr = file.readText()
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val name = obj.getString("name")
                val lat = obj.getDouble("lat")
                val lon = obj.getDouble("lon")
                val battery = obj.optInt("battery", 100)
                val speed = obj.optDouble("speed", 0.0).toFloat()
                val signal = obj.optInt("signal", -85)
                val networkType = obj.optString("networkType", "4G LTE")
                val lastSeen = obj.optLong("lastSeen", System.currentTimeMillis())
                val heading = obj.optDouble("heading", 0.0).toFloat()
                val altitude = obj.optDouble("altitude", 0.0)
                val accuracy = obj.optDouble("accuracy", 0.0).toFloat()
                val isLocationOn = obj.optBoolean("isLocationOn", true)

                val historyList = mutableListOf<Pair<Double, Double>>()
                val histArray = obj.optJSONArray("history")
                if (histArray != null) {
                    for (j in 0 until histArray.length()) {
                        val pt = histArray.getJSONObject(j)
                        historyList.add(Pair(pt.getDouble("lat"), pt.getDouble("lon")))
                    }
                }

                activeDevices[name] = DeviceTelemetry(
                    name = name,
                    lat = lat,
                    lon = lon,
                    battery = battery,
                    speed = speed,
                    signal = signal,
                    networkType = networkType,
                    lastSeen = lastSeen,
                    heading = heading,
                    altitude = altitude,
                    accuracy = accuracy,
                    history = historyList,
                    isLocationOn = isLocationOn
                )
            }
            Log.i("AppState", "Hydrated ${activeDevices.size} devices instantly from Flash Disk Cache!")
        } catch (e: Exception) {
            Log.w("AppState", "Could not load disk cache", e)
        }
    }

    private fun saveTelemetryToDisk() {
        appScope.launch {
            try {
                val file = cacheFile ?: return@launch
                val array = JSONArray()
                activeDevices.values.forEach { dev ->
                    val obj = JSONObject()
                    obj.put("name", dev.name)
                    obj.put("lat", dev.lat)
                    obj.put("lon", dev.lon)
                    obj.put("battery", dev.battery)
                    obj.put("speed", dev.speed.toDouble())
                    obj.put("signal", dev.signal)
                    obj.put("networkType", dev.networkType)
                    obj.put("lastSeen", dev.lastSeen)
                    obj.put("heading", dev.heading.toDouble())
                    obj.put("altitude", dev.altitude)
                    obj.put("accuracy", dev.accuracy.toDouble())
                    obj.put("isLocationOn", dev.isLocationOn)

                    val histArray = JSONArray()
                    dev.history.takeLast(25).forEach { pt ->
                        val pObj = JSONObject()
                        pObj.put("lat", pt.first)
                        pObj.put("lon", pt.second)
                        histArray.put(pObj)
                    }
                    obj.put("history", histArray)
                    array.put(obj)
                }
                file.writeText(array.toString())
            } catch (e: Exception) {
                // Ignore transient write errors
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
            saveTelemetryToDisk()
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

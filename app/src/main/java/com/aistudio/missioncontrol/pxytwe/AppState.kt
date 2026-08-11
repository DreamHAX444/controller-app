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
    
    // Live device telemetry received from Supabase
    val activeDevices = mutableStateMapOf<String, DeviceTelemetry>()

    private val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
    private var isInitialized = false

    fun initialize() {
        if (isInitialized) return
        isInitialized = true

        appScope.launch {
            try {
                // Fetch initial locations first
                val initialLocs = SupabaseClientManager.getInitialLocations()
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
                }

                // Connect to realtime
                SupabaseClientManager.connectRealtime()
                SupabaseClientManager.startListeningForPongs()
                val locationsFlow = SupabaseClientManager.listenToLocations()
                locationsFlow.collect { loc ->
                    val existing = activeDevices[loc.device_id]
                    val newHistory = existing?.history?.toMutableList() ?: mutableListOf()
                    newHistory.add(Pair(loc.latitude, loc.longitude))
                    
                    val updatedDev = DeviceTelemetry(
                        name = loc.device_id,
                        lat = loc.latitude,
                        lon = loc.longitude,
                        heading = loc.bearing,
                        battery = 100,
                        speed = 0f,
                        lastSeen = System.currentTimeMillis(),
                        history = if (newHistory.size > 50) newHistory.takeLast(50) else newHistory,
                        updateCount = (existing?.updateCount ?: 0) + 1,
                        locTimestamp = System.currentTimeMillis()
                    )
                    activeDevices[loc.device_id] = updatedDev
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
}


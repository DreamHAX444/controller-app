package com.aistudio.missioncontrol.pxytwe

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

data class DeviceEvent(
    val timestamp: Long,
    val deviceId: String,
    val type: String,
    val message: String
)

object EventLogger {
    val events = mutableStateListOf<DeviceEvent>()
    private var sharedPrefs: SharedPreferences? = null
    
    private const val MAX_EVENTS = 500
    private const val PREFS_KEY = "event_logs"

    fun initialize(context: Context) {
        sharedPrefs = context.getSharedPreferences("events_prefs", Context.MODE_PRIVATE)
        loadEvents()
    }

    fun logEvent(deviceId: String, type: String, message: String) {
        val event = DeviceEvent(
            timestamp = System.currentTimeMillis(),
            deviceId = deviceId,
            type = type,
            message = message
        )
        // Add to front of the list for chronological ordering (newest first in UI)
        events.add(0, event)
        
        // Trim if too large
        while (events.size > MAX_EVENTS) {
            events.removeLast()
        }
        
        saveEvents()
    }

    fun clearEvents() {
        events.clear()
        saveEvents()
    }

    private fun loadEvents() {
        val data = sharedPrefs?.getString(PREFS_KEY, "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(data)
            events.clear()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                events.add(
                    DeviceEvent(
                        timestamp = obj.getLong("timestamp"),
                        deviceId = obj.getString("deviceId"),
                        type = obj.getString("type"),
                        message = obj.getString("message")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveEvents() {
        try {
            val jsonArray = JSONArray()
            events.forEach { event ->
                val obj = JSONObject()
                obj.put("timestamp", event.timestamp)
                obj.put("deviceId", event.deviceId)
                obj.put("type", event.type)
                obj.put("message", event.message)
                jsonArray.put(obj)
            }
            sharedPrefs?.edit()?.putString(PREFS_KEY, jsonArray.toString())?.apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

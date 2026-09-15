package com.aistudio.missioncontrol.pxytwe.webrtc

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Test

class CommandSerializationTest {

    @Test
    fun testStartScreenParamsSerialization() {
        val params = StartScreenParams(
            sessionId = "test-session-uuid",
            qualityProfile = "SMOOTH"
        )
        
        val json = Json.encodeToString(params)
        assertEquals("""{"session_id":"test-session-uuid","quality_profile":"SMOOTH"}""", json)
    }
}

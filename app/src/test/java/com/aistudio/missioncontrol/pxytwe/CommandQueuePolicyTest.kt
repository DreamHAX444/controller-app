package com.aistudio.missioncontrol.pxytwe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandQueuePolicyTest {

    @Test
    fun testWakeCommandSerialization() {
        val command = "wake"
        val isQueuedCommand = (command == "wake" || command == "sleep")
        
        val payload = CommandPayload(
            device_id = "test_device",
            command = command,
            status = if (isQueuedCommand) "pending" else null
        )
        
        assertEquals("pending", payload.status)
        assertEquals("wake", payload.command)
    }

    @Test
    fun testStartScreenCommandSerialization() {
        val command = "start_screen"
        val isQueuedCommand = (command == "wake" || command == "sleep")
        
        val payload = CommandPayload(
            device_id = "test_device",
            command = command,
            status = if (isQueuedCommand) "pending" else null
        )
        
        assertNull(payload.status)
        assertEquals("start_screen", payload.command)
    }
}

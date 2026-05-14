package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.integration.model.HomeStatus
import com.ghost.serialization.integration.model.SmartDevice
import com.ghost.serialization.integration.model.SmartHome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GhostResilienceAndFallbackTest {

    @Test
    fun testGhostFallbackWithUnknownDiscriminator() {
        val json = """
        {
            "id": "home_1",
            "active": true,
            "deviceCount": 2,
            "devices": [
                { "type": "Light", "brightness": 80 },
                { "type": "QuantumSensor", "quantumState": "superposition" }
            ]
        }
        """.trimIndent()

        val home = Ghost.deserialize<SmartHome>(json)
        assertEquals("home_1", home.id)
        assertEquals(2, home.devices.size)
        
        val light = home.devices[0]
        assertTrue(light is SmartDevice.Light)
        assertEquals(80, light.brightness)
        
        val unknown = home.devices[1]
        assertTrue(unknown is SmartDevice.UnknownDevice)
        // Note: rawData gets the default value because we didn't add a mechanism to capture unknown data yet,
        // but it safely avoids throwing an exception!
        assertEquals("unknown", unknown.rawData)
    }

    @Test
    fun testGhostResilientWithTypeMismatch() {
        // active is expected to be Boolean, deviceCount is Int
        // We'll pass an array for active and an object for deviceCount
        val json = """
        {
            "id": "home_2",
            "active": [1, 2, 3],
            "deviceCount": { "count": 5 },
            "devices": [],
            "status": "ONLINE"
        }
        """.trimIndent()

        val home = Ghost.deserialize<SmartHome>(json)
        assertEquals("home_2", home.id)
        
        // active is nullable and resilient, should become null
        assertEquals(null, home.active)
        
        // deviceCount is non-nullable with default 0, should become 0
        assertEquals(0, home.deviceCount)
        
        assertEquals(HomeStatus.ONLINE, home.status)
    }

    @Test
    fun testGhostResilientWithUnknownEnum() {
        // status is an Enum, we pass an unknown value
        val json = """
        {
            "id": "home_3",
            "active": true,
            "deviceCount": 1,
            "devices": [],
            "status": "SUPER_ONLINE"
        }
        """.trimIndent()

        val home = Ghost.deserialize<SmartHome>(json)
        assertEquals("home_3", home.id)
        
        // status is resilient and nullable, should be null
        assertEquals(null, home.status)
    }

    @Test
    fun testGhostBooleanCoercionLegacyFormat() {
        // We pass 1 and 0 for booleans, which SmartThings iOS app does often
        val json = """
        {
            "id": "home_4",
            "active": 1,
            "deviceCount": 10,
            "devices": []
        }
        """.trimIndent()

        val home = Ghost.deserialize<SmartHome>(json) {
            it.coerceBooleans = true
        }
        assertEquals(true, home.active)

        val jsonFalse = """
        {
            "id": "home_4",
            "active": 0,
            "deviceCount": 10,
            "devices": []
        }
        """.trimIndent()
        
        val homeFalse = Ghost.deserialize<SmartHome>(jsonFalse) {
            it.coerceBooleans = true
        }
        assertEquals(false, homeFalse.active)
    }
}

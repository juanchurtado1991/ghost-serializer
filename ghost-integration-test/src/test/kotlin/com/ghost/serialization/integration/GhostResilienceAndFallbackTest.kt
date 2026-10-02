package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.HomeStatus
import com.ghost.serialization.integration.model.SmartDevice
import com.ghost.serialization.integration.model.SmartHome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(InternalGhostApi::class)
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
        assertEquals(expected = "home_1", actual = home.id)
        assertEquals(expected = 2, actual = home.devices.size)

        val light = home.devices[0]
        assertTrue(actual = light is SmartDevice.Light)
        assertEquals(expected = 80, actual = light.brightness)

        val unknown = home.devices[1]
        assertTrue(actual = unknown is SmartDevice.UnknownDevice)
        // rawData falls back to its default — there's no mechanism yet to capture unknown data
        assertEquals(expected = "unknown", actual = unknown.rawData)
    }

    @Test
    fun testGhostResilientWithTypeMismatch() {
        // active (Boolean) gets an array, deviceCount (Int) gets an object
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
        assertEquals(expected = "home_2", actual = home.id)

        // active is nullable and resilient, should become null
        assertEquals(expected = null, actual = home.active)

        // deviceCount is non-nullable with default 0, should become 0
        assertEquals(expected = 0, actual = home.deviceCount)

        assertEquals(expected = HomeStatus.ONLINE, actual = home.status)
    }

    @Test
    fun testGhostResilientWithUnknownEnum() {
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
        assertEquals(expected = "home_3", actual = home.id)

        // status is resilient and nullable, should be null
        assertEquals(expected = null, actual = home.status)
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
        assertEquals(expected = true, actual = home.active)

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
        assertEquals(expected = false, actual = homeFalse.active)
    }

    @Test
    fun testGhostResilientWithMalformedNestedObject() {
        // Empty object is malformed: HomeConfig requires wifiSsid and autoLock
        val json = """
        {
            "id": "home_5",
            "active": true,
            "deviceCount": 1,
            "devices": [],
            "config": {}
        }
        """.trimIndent()

        val home = Ghost.deserialize<SmartHome>(json)
        assertEquals(expected = "home_5", actual = home.id)

        // config is malformed but @GhostResilient, should be null instead of crashing
        assertEquals(expected = null, actual = home.config)
    }
}

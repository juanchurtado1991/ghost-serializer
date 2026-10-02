package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.exception.GhostJsonException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GhostInferredPolymorphismTest {

    @Test
    fun testInferredTempEvent() {
        val json = """{"temperature": 25.5, "unit": "C"}"""
        val event = Ghost.deserialize<SmartEvent>(json)
        assertEquals(expected = SmartEvent.TempEvent(temperature = 25.5, unit = "C"), actual = event)
    }

    @Test
    fun testInferredHumidityEvent() {
        val json = """{"humidity": 60.0}"""
        val event = Ghost.deserialize<SmartEvent>(json)
        assertEquals(expected = SmartEvent.HumidityEvent(humidity = 60.0), actual = event)
    }

    @Test
    fun testInferredMixedEvent() {
        val json = """{"temperature": 22.0, "humidity": 55.0}"""
        val event = Ghost.deserialize<SmartEvent>(json)
        assertEquals(expected = SmartEvent.MixedEvent(temperature = 22.0, humidity = 55.0), actual = event)
    }

    @Test
    fun testInferredMotionEventWithSignature() {
        val json = """{"motion": true}"""
        val event = Ghost.deserialize<SmartEvent>(json)
        assertEquals(expected = SmartEvent.MotionEvent(motion = true), actual = event)
    }

    @Test
    fun testDeeplyNestedAndLists() {
        val json = """
            {
                "id": "dev_123",
                "event": {"humidity": 45.0},
                "commands": [
                    {"force": true},
                    {"level": 80},
                    {"url": "http://ghost.io", "version": "1.2"}
                ]
            }
        """.trimIndent()

        val container = Ghost.deserialize<InferredNestedContainer>(json)

        assertEquals(expected = "dev_123", actual = container.id)
        assertEquals(expected = SmartEvent.HumidityEvent(humidity = 45.0), actual = container.event)
        assertEquals(expected = 3, actual = container.commands.size)
        assertEquals(expected = DeviceCommand.Reboot(force = true), actual = container.commands[0])
        assertEquals(expected = DeviceCommand.SetBrightness(level = 80), actual = container.commands[1])
        assertEquals(
            expected = DeviceCommand.UpdateFirmware(url = "http://ghost.io", version = "1.2"),
            actual = container.commands[2]
        )
    }

    @Test
    fun testResilienceToUnknownFields() {
        val json = """
            {
                "extra1": "foo",
                "temperature": 10.0,
                "extra2": 123,
                "unit": "K",
                "extra3": null
            }
        """.trimIndent()
        val event = Ghost.deserialize<SmartEvent>(json)
        assertEquals(expected = SmartEvent.TempEvent(temperature = 10.0, unit = "K"), actual = event)
    }

    @Test
    fun testAmbiguousJSON() {
        val json = """{"unknown": "key"}"""
        assertFailsWith<GhostJsonException> {
            Ghost.deserialize<SmartEvent>(json)
        }
    }

    @Test
    fun testPartialSignatureFailure() {
        // unit is missing for TempEvent and humidity for MixedEvent, so eligibilityMask
        // has bits set but no candidate's reqMask is satisfied
        val json = """{"temperature": 25.5}"""
        assertFailsWith<GhostJsonException> {
            Ghost.deserialize<SmartEvent>(json)
        }
    }
}

package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.annotations.GhostSignature
import com.ghost.serialization.exception.GhostJsonException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@GhostSerialization(inferred = true)
sealed class SmartEvent {
    @GhostSerialization
    data class TempEvent(
        val temperature: Double,
        val unit: String
    ) : SmartEvent()

    @GhostSerialization
    data class HumidityEvent(
        val humidity: Double
    ) : SmartEvent()

    @GhostSerialization
    data class MixedEvent(
        val temperature: Double,
        val humidity: Double
    ) : SmartEvent()

    @GhostSerialization
    data class MotionEvent(
        @GhostSignature
        val motion: Boolean
    ) : SmartEvent()
}

@GhostSerialization(inferred = true)
sealed class DeviceCommand {
    @GhostSerialization
    data class Reboot(val force: Boolean = false) : DeviceCommand()
    
    @GhostSerialization
    data class SetBrightness(val level: Int) : DeviceCommand()

    @GhostSerialization
    data class UpdateFirmware(val url: String, val version: String) : DeviceCommand()
}

@GhostSerialization
data class InferredNestedContainer(
    val id: String,
    val event: SmartEvent,
    val commands: List<DeviceCommand>
)

class GhostInferredPolymorphismTest {

    @Test
    fun testInferredTempEvent() {
        val json = """{"temperature": 25.5, "unit": "C"}"""
        val event = Ghost.deserialize<SmartEvent>(json)
        assertEquals(SmartEvent.TempEvent(25.5, "C"), event)
    }

    @Test
    fun testInferredHumidityEvent() {
        val json = """{"humidity": 60.0}"""
        val event = Ghost.deserialize<SmartEvent>(json)
        assertEquals(SmartEvent.HumidityEvent(60.0), event)
    }

    @Test
    fun testInferredMixedEvent() {
        val json = """{"temperature": 22.0, "humidity": 55.0}"""
        val event = Ghost.deserialize<SmartEvent>(json)
        assertEquals(SmartEvent.MixedEvent(22.0, 55.0), event)
    }

    @Test
    fun testInferredMotionEventWithSignature() {
        val json = """{"motion": true}"""
        val event = Ghost.deserialize<SmartEvent>(json)
        assertEquals(SmartEvent.MotionEvent(true), event)
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
        
        assertEquals("dev_123", container.id)
        assertEquals(SmartEvent.HumidityEvent(45.0), container.event)
        assertEquals(3, container.commands.size)
        assertEquals(DeviceCommand.Reboot(true), container.commands[0])
        assertEquals(DeviceCommand.SetBrightness(80), container.commands[1])
        assertEquals(DeviceCommand.UpdateFirmware("http://ghost.io", "1.2"), container.commands[2])
    }

    @Test
    fun testResilienceToUnknownFields() {
        // Even with many unknown fields, it should still identify the signature
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
        assertEquals(SmartEvent.TempEvent(10.0, "K"), event)
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
        // temperature is present, but unit (required) is missing for TempEvent
        // humidity is also missing for MixedEvent
        // So eligibilityMask might have bits, but reqMasks won't match
        val json = """{"temperature": 25.5}"""
        assertFailsWith<GhostJsonException> {
            Ghost.deserialize<SmartEvent>(json)
        }
    }
}

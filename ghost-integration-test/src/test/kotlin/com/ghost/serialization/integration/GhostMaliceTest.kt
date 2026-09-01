@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.integration.model.CollisionModel
import com.ghost.serialization.integration.model.DecimalStress
import com.ghost.serialization.integration.model.MaliceModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GhostMaliceTest {

    @Test
    fun testMaxDepthProtection() {
        // Create a deeply nested JSON that exceeds the default depth of 255
        val depth = 300
        val nestedJson = "{ \"nested\": ".repeat(depth) + "{}" + "}".repeat(depth)

        val exception = assertFailsWith<GhostJsonException> {
            Ghost.deserialize<MaliceModel>(nestedJson.encodeToByteArray())
        }

        assertTrue(
            exception.message.contains("Reached maximum recursion depth"),
            "Should throw depth exceeded error"
        )
    }

    @Test
    fun testPrecisionInjection() {
        val massiveDecimal = "0." + "1".repeat(500)
        val json = """{"big": $massiveDecimal, "small": 0.1, "precise": 0.2}"""

        // Must not throw NumberFormatException; parsing as much as possible or failing gracefully is fine
        Ghost.deserialize<DecimalStress>(json.encodeToByteArray())
    }

    @Test
    fun testMalformedUnicode() {
        val json = """{"simple": "hello \u12"}"""
        assertFailsWith<GhostJsonException> {
            Ghost.deserialize<MaliceModel>(json.encodeToByteArray())
        }
    }

    @Test
    fun testCollisionStress() {
        val json = buildString {
            append("{")
            for (i in 1..100) {
                append("\"a$i\": $i")
                if (i < 100) append(", ")
            }
            append("}")
        }

        val result = Ghost.deserialize<CollisionModel>(json.encodeToByteArray())
        assertEquals(result.a1, 1, "a1 should be 1")
        assertEquals(result.a100, 100, "a100 should be 100")
    }
}

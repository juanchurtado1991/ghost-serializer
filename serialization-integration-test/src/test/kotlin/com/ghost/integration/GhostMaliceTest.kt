package com.ghost.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.core.exception.GhostJsonException
import com.ghost.integration.model.MaliceModel
import com.ghost.integration.model.DecimalStress
import com.ghost.integration.model.CollisionModel
import kotlin.test.Test
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
        
        assertTrue(exception.message!!.contains("Reached maximum recursion depth"), "Should throw depth exceeded error")
    }

    @Test
    fun testPrecisionInjection() {
        // Test with a massive number of decimals (500+)
        val massiveDecimal = "0." + "1".repeat(500)
        val json = """{"value": $massiveDecimal, "text": "massive"}"""
        
        // This should not throw NumberFormatException, but parse as much as possible or fail gracefully
        Ghost.deserialize<DecimalStress>(json.encodeToByteArray())
    }

    @Test
    fun testMalformedUnicode() {
        // Truncated unicode at the end
        val json = """{"simple": "hello \u12"}"""
        assertFailsWith<GhostJsonException> {
            Ghost.deserialize<MaliceModel>(json.encodeToByteArray())
        }
    }

    @Test
    fun testCollisionStress() {
        // Test 100 fields with similar prefixes (a1, a2, ..., a100)
        val json = buildString {
            append("{")
            for (i in 1..100) {
                append("\"a$i\": $i")
                if (i < 100) append(", ")
            }
            append("}")
        }
        
        val result = Ghost.deserialize<CollisionModel>(json.encodeToByteArray())
        assertTrue(result.a1 == 1, "a1 should be 1")
        assertTrue(result.a100 == 100, "a100 should be 100")
    }
}

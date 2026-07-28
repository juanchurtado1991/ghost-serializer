@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.writer.common.*
import com.ghost.serialization.writer.strings.*
import com.ghost.serialization.parser.strings.*
import com.ghost.serialization.parser.streaming.*
import com.ghost.serialization.parser.common.*
import com.ghost.serialization.parser.bytes.*
import com.ghost.serialization.writer.bytes.*
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.common.JsonReaderOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GhostResilienceTest {

    @Test
    fun testValidationPositionReporting() {
        val json = """
            {
                "name": "Ghost",
                "missing_field": 
        """.trimIndent()
        
        val reader = GhostJsonReader(json.encodeToByteArray())
        
        // This simulates a generated serializer failing at a specific point
        val exception = assertFailsWith<GhostJsonException> {
            reader.beginObject()
            reader.selectString(JsonReaderOptions.of("name"))
            reader.consumeKeySeparator()
            reader.nextString() // name
            
            // Now we are at the end, simulate a missing field check
            reader.throwError("Required field 'info' missing")
        }
        
        // After the fix, this was -1. Now it must be the current reader position.
        // The reader passed "Ghost", so it is currently on line 1 (at the comma).
        assertEquals(1, exception.line, "Line must be precisely tracked")
        // Column should also be > 0
        assertTrue(exception.column > 0, "Column should be positive: ${exception.column}")
    }

    @Test
    fun testTruncatedJsonReporting() {
        val json = """{"id": 123, "name": "Ju"""
        val reader = GhostJsonReader(json.encodeToByteArray())
        
        val exception = assertFailsWith<GhostJsonException> {
            reader.beginObject()
            while (reader.hasNext()) {
                val index = reader.selectString(JsonReaderOptions.of("id", "name"))
                reader.consumeKeySeparator()
                when (index) {
                    0 -> reader.nextInt()
                    1 -> reader.nextString() // This will fail due to terminal quote missing
                }
            }
        }
        
        assertEquals(0, exception.line)
        assertTrue(exception.column >= 22, "Column should be at least 22: ${exception.column}")
    }

    @Test
    fun testStrictModeUnknownFieldReporting() {
        val json = """{"id": 1, "unknown_field": true}"""
        val reader = GhostJsonReader(json.encodeToByteArray(), strictMode = true)
        
        val exception = assertFailsWith<GhostJsonException> {
            reader.beginObject()
            val opts = JsonReaderOptions.of("id")
            assertEquals(0, reader.selectString(opts))
            reader.consumeKeySeparator()
            reader.nextInt()
            
            // Next one is 'unknown_field', in strict mode it should throw
            reader.selectString(opts)
        }
        
        assertTrue(exception.message.contains("unknown_field"))
        assertEquals(0, exception.line)
        // Position should be near the start of the unknown field
        assertTrue(exception.column > 10)
    }
}

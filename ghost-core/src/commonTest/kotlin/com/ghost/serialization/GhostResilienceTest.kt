package com.ghost.serialization

import com.ghost.serialization.core.parser.GhostJsonReader
import com.ghost.serialization.core.parser.consumeKeySeparator
import com.ghost.serialization.core.parser.nextInt
import com.ghost.serialization.core.exception.GhostJsonException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
            reader.selectName(GhostJsonReader.Options.of("name"))
            reader.consumeKeySeparator()
            reader.nextString() // name
            
            // Now we are at the end, simulate a missing field check
            reader.throwError("Required field 'info' missing")
        }
        
        // After the fix, this was -1. Now it must be the current reader position.
        // The reader passed "Ghost", so it is currently on line 2 (at the comma).
        assertEquals(2, exception.line, "Line must be precisely tracked")
        // Column should also be > 0
        assert(exception.column > 0) { "Column should be positive: ${exception.column}" }
    }

    @Test
    fun testTruncatedJsonReporting() {
        val json = """{"id": 123, "name": "Ju"""
        val reader = GhostJsonReader(json.encodeToByteArray())
        
        val exception = assertFailsWith<GhostJsonException> {
            reader.beginObject()
            while (reader.hasNext()) {
                val index = reader.selectName(GhostJsonReader.Options.of("id", "name"))
                reader.consumeKeySeparator()
                when (index) {
                    0 -> reader.nextInt()
                    1 -> reader.nextString() // This will fail due to terminal quote missing
                }
            }
        }
        
        assertEquals(1, exception.line)
        assert(exception.column >= 22) { "Column should be at least 22: ${exception.column}" }
    }

    @Test
    fun testStrictModeUnknownFieldReporting() {
        val json = """{"id": 1, "unknown_field": true}"""
        val reader = GhostJsonReader(json.encodeToByteArray(), strictMode = true)
        
        val exception = assertFailsWith<GhostJsonException> {
            reader.beginObject()
            val opts = GhostJsonReader.Options.of("id")
            assertEquals(0, reader.selectName(opts))
            reader.consumeKeySeparator()
            reader.nextInt()
            
            // Next one is 'unknown_field', in strict mode it should throw
            reader.selectName(opts)
        }
        
        assert(exception.message!!.contains("unknown_field"))
        assertEquals(1, exception.line)
        // Position should be near the start of the unknown field
        assert(exception.column > 10)
    }
}

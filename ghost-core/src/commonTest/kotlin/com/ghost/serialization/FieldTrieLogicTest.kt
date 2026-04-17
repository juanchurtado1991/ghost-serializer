package com.ghost.serialization
import com.ghost.serialization.core.parser.JsonReaderOptions

import com.ghost.serialization.core.parser.GhostJsonReader
import kotlin.test.Test
import kotlin.test.assertEquals

class FieldTrieLogicTest {

    @Test
    fun `internalSelect should match fields correctly with optimized filters`() {
        val json = """{"id":1,"name":"Rick"}""".encodeToByteArray()
        val reader = GhostJsonReader(json)
        
        // Skip '{'
        reader.beginObject()
        
        val options = JsonReaderOptions.of("id", "name", "species")
        
        // 1. Select "id"
        val index1 = reader.selectString(options)
        assertEquals(0, index1, "Should match 'id' at index 0")
        
        // Consume value and separator
        reader.expectByte(':'.code.toByte())
        reader.internalSkip(1) // skip '1'
        reader.expectByte(','.code.toByte())
        
        // 2. Select "name"
        val index2 = reader.selectString(options)
        assertEquals(1, index2, "Should match 'name' at index 1")
    }

    @Test
    fun `selectString should return -2 for unknown fields`() {
        val json = """{"unknown":true}""".encodeToByteArray()
        val reader = GhostJsonReader(json)
        reader.beginObject()
        
        val options = JsonReaderOptions.of("id", "name")
        val index = reader.selectString(options)
        assertEquals(-2, index, "Should return -2 for unknown field (Industrial Constant)")
    }
}

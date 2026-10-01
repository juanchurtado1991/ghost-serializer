@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.parser.common.json.JsonReaderOptions
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.selectString
import kotlin.test.Test
import kotlin.test.assertEquals


class FieldTrieLogicTest {

    @Test
    fun `internalSelect should match fields correctly with optimized filters`() {
        val json = """{"id":1,"name":"Rick"}""".encodeToByteArray()
        val reader = GhostJsonReader(json)
        reader.beginObject()

        val options = JsonReaderOptions.of("id", "name", "species")

        val index1 = reader.selectString(options = options)
        assertEquals(
            expected = 0,
            actual = index1,
            message = "Should match 'id' at index 0"
        )

        reader.expectByte(expected = ':'.code)
        reader.internalSkip(1)
        reader.expectByte(expected = ','.code)

        val index2 = reader.selectString(options = options)
        assertEquals(
            expected = 1,
            actual = index2,
            message = "Should match 'name' at index 1"
        )
    }

    @Test
    fun `selectString should return -2 for unknown fields`() {
        val json = """{"unknown":true}""".encodeToByteArray()
        val reader = GhostJsonReader(json)
        reader.beginObject()

        val options = JsonReaderOptions.of("id", "name")
        val index = reader.selectString(options = options)
        assertEquals(
            expected = -2,
            actual = index,
            message = "Should return -2 for unknown field (Industrial Constant)"
        )
    }
}

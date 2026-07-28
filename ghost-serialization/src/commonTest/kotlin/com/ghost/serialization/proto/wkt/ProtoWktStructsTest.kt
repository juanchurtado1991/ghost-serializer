package com.ghost.serialization.proto.wkt

import com.ghost.serialization.parser.common.*
import com.ghost.serialization.parser.bytes.*
import com.ghost.serialization.parser.strings.*
import com.ghost.serialization.parser.streaming.*
import com.ghost.serialization.parser.proto.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProtoWktStructsTest {

    @Test
    fun testFieldMaskSnakeToCamel() {
        val mask = parseFieldMask("user.displayName,photo")
        assertEquals(2, mask.paths.size)
        assertEquals("user.display_name", mask.paths[0])
        assertEquals("photo", mask.paths[1])

        val formatted = formatFieldMask(mask)
        assertEquals("user.displayName,photo", formatted)
    }

    @Test
    fun testEmpty() {
        // Should parse empty structures and successfully skip unknown fields
        val parsed = ProtoEmptySerializer.parseTimestampForTesting("{}")
        assertTrue(parsed is ProtoEmpty)
    }

    private fun ProtoEmptySerializer.parseTimestampForTesting(json: String): ProtoEmpty {
        val reader = com.ghost.serialization.parser.proto.GhostProtoJsonFlatReader(json.encodeToByteArray())
        return deserialize(reader)
    }
}

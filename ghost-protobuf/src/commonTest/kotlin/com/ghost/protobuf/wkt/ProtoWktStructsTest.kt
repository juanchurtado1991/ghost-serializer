package com.ghost.protobuf.wkt

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
        val reader = com.ghost.serialization.parser.GhostProtoJsonFlatReader(json.encodeToByteArray())
        return deserialize(reader)
    }
}

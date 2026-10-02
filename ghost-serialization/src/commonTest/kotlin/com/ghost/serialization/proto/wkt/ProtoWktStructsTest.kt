package com.ghost.serialization.proto.wkt

import com.ghost.serialization.parser.proto.GhostProtoJsonFlatReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class ProtoWktStructsTest {

    @Test
    fun testFieldMaskSnakeToCamel() {
        val mask = parseFieldMask(pathsText = "user.displayName,photo")
        assertEquals(
            expected = 2,
            actual = mask.paths.size
        )
        assertEquals(
            expected = "user.display_name",
            actual = mask.paths[0]
        )
        assertEquals(
            expected = "photo",
            actual = mask.paths[1]
        )

        val formatted = formatFieldMask(mask = mask)
        assertEquals(
            expected = "user.displayName,photo",
            actual = formatted
        )
    }

    @Test
    fun testEmpty() {
        val parsed = ProtoEmptySerializer.parseTimestampForTesting(json = "{}")
        assertTrue(actual = parsed is ProtoEmpty)
    }

    private fun ProtoEmptySerializer.parseTimestampForTesting(json: String): ProtoEmpty {
        val reader =
            GhostProtoJsonFlatReader(rawData = json.encodeToByteArray())
        return deserialize(reader)
    }
}

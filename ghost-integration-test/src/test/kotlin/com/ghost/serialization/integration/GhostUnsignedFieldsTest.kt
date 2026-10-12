package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.exception.GhostJsonException
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GhostUnsignedFieldsTest {

    @Test
    fun outOfRangeUnsignedValuesAreRejectedOnEveryChannel() {
        OUT_OF_RANGE_INPUTS.forEach { json ->
            assertFailsWith<GhostJsonException>(message = json) {
                Ghost.deserialize<UnsignedFieldsModel>(json = json)
            }
            assertFailsWith<GhostJsonException>(message = json) {
                Ghost.deserialize<UnsignedFieldsModel>(bytes = json.encodeToByteArray())
            }
            assertFailsWith<GhostJsonException>(message = json) {
                Ghost.deserializeStreaming<UnsignedFieldsModel>(source = Buffer().writeUtf8(string = json))
            }
        }
    }

    @Test
    fun unsignedFieldsRoundTripAtTheirMaximum() {
        val model = UnsignedFieldsModel(
            unsignedInt = UInt.MAX_VALUE,
            unsignedShort = UShort.MAX_VALUE,
            unsignedByte = UByte.MAX_VALUE,
            unsignedLong = ULong.MAX_VALUE,
            maybeUnsignedInt = 5u,
            unsignedInts = listOf(0u, UInt.MAX_VALUE)
        )
        val json = Ghost.encodeToString(value = model)

        assertEquals(
            expected = EXPECTED_JSON,
            actual = json
        )
        assertEquals(
            expected = model,
            actual = Ghost.deserialize<UnsignedFieldsModel>(json = json)
        )
    }

    companion object {
        private const val EXPECTED_JSON = "{\"unsignedInt\":4294967295,\"unsignedShort\":65535,\"unsignedByte\":255," +
            "\"unsignedLong\":18446744073709551615,\"maybeUnsignedInt\":5,\"unsignedInts\":[0,4294967295]}"
        private val OUT_OF_RANGE_INPUTS = listOf(
            """{"unsignedInt":-1,"unsignedShort":1,"unsignedByte":1,"unsignedLong":1}""",
            """{"unsignedInt":4294967296,"unsignedShort":1,"unsignedByte":1,"unsignedLong":1}""",
            """{"unsignedInt":1,"unsignedShort":65536,"unsignedByte":1,"unsignedLong":1}""",
            """{"unsignedInt":1,"unsignedShort":1,"unsignedByte":256,"unsignedLong":1}""",
            """{"unsignedInt":1,"unsignedShort":1,"unsignedByte":1,"unsignedLong":-1}"""
        )
    }
}

@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.types.RawJson
import com.ghost.serialization.parser.JsonReaderOptions
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GhostWrappedKeysCaptureTest {

    @Test
    fun materializeBuildsSyntheticWrapperObject() {
        val capture = GhostWrappedKeysCapture(2)
        val reader = GhostJsonFlatReader(
            """{"extra1":"a","extra2":42}""".encodeToByteArray(),
        )
        reader.beginObject()
        reader.selectNameAndConsume(
            JsonReaderOptions.of("extra1", "extra2"),
        )
        reader.captureWrappedKey(capture, 0)
        reader.selectNameAndConsume(
            JsonReaderOptions.of("extra1", "extra2"),
        )
        reader.captureWrappedKey(capture, 1)
        reader.endObject()

        val keyLiterals = arrayOf(
            "\"extra1\":".encodeToByteArray(),
            "\"extra2\":".encodeToByteArray(),
        )
        val wrapped = capture.materializeWrappedObject(
            keyUtf8Literals = keyLiterals,
            omitIfEmpty = false,
            omitIfAbsentIndices = intArrayOf(),
        )

        assertEquals("""{"extra1":"a","extra2":42}""", wrapped!!.decodeToString())
    }

    @Test
    fun omitIfEmptyReturnsNullWhenAllAbsent() {
        val capture = GhostWrappedKeysCapture(2)
        val wrapped = capture.materializeWrappedObject(
            keyUtf8Literals = arrayOf(
                "\"extra1\":".encodeToByteArray(),
                "\"extra2\":".encodeToByteArray(),
            ),
            omitIfEmpty = true,
            omitIfAbsentIndices = intArrayOf(),
        )
        assertNull(wrapped)
    }

    @Test
    fun omitIfAbsentReturnsNullWhenTriggerMissing() {
        val capture = GhostWrappedKeysCapture(2)
        capture.put(0, RawJson.fromString("\"a\""))
        val wrapped = capture.materializeWrappedObject(
            keyUtf8Literals = arrayOf(
                "\"extra1\":".encodeToByteArray(),
                "\"extra2\":".encodeToByteArray(),
            ),
            omitIfEmpty = false,
            omitIfAbsentIndices = intArrayOf(1),
        )
        assertNull(wrapped)
    }

    @Test
    fun missingSlotsMaterializeAsNullLiterals() {
        val capture = GhostWrappedKeysCapture(2)
        capture.put(0, RawJson.fromString("\"a\""))
        val wrapped = capture.materializeWrappedObject(
            keyUtf8Literals = arrayOf(
                "\"extra1\":".encodeToByteArray(),
                "\"extra2\":".encodeToByteArray(),
            ),
            omitIfEmpty = false,
            omitIfAbsentIndices = intArrayOf(),
        )
        assertContentEquals(
            """{"extra1":"a","extra2":null}""".encodeToByteArray(),
            wrapped,
        )
    }
}

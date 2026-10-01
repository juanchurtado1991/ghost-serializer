@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser.common

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.json.JsonReaderOptions
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.endObject
import com.ghost.serialization.parser.streaming.selectNameAndConsume
import com.ghost.serialization.types.RawJson
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull


class GhostWrappedKeysCaptureTest {

    @Test
    fun materializeBuildsSyntheticWrapperObject() {
        val capture = GhostWrappedKeysCapture(slotCount = 2)
        val reader = GhostJsonReader(
            """{"extra1":"a","extra2":42}""".encodeToByteArray(),
        )
        reader.beginObject()
        reader.selectNameAndConsume(
            JsonReaderOptions.of("extra1", "extra2"),
        )
        reader.captureWrappedKey(capture = capture, slotIndex = 0)
        reader.selectNameAndConsume(
            JsonReaderOptions.of("extra1", "extra2"),
        )
        reader.captureWrappedKey(capture = capture, slotIndex = 1)
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

        assertEquals(
            expected = """{"extra1":"a","extra2":42}""",
            actual = wrapped!!.decodeToString()
        )
    }

    @Test
    fun omitIfEmptyReturnsNullWhenAllAbsent() {
        val capture = GhostWrappedKeysCapture(slotCount = 2)
        val wrapped = capture.materializeWrappedObject(
            keyUtf8Literals = arrayOf(
                "\"extra1\":".encodeToByteArray(),
                "\"extra2\":".encodeToByteArray(),
            ),
            omitIfEmpty = true,
            omitIfAbsentIndices = intArrayOf(),
        )
        assertNull(actual = wrapped)
    }

    @Test
    fun omitIfAbsentReturnsNullWhenTriggerMissing() {
        val capture = GhostWrappedKeysCapture(slotCount = 2)
        capture.put(0, RawJson.fromString(json = "\"a\""))
        val wrapped = capture.materializeWrappedObject(
            keyUtf8Literals = arrayOf(
                "\"extra1\":".encodeToByteArray(),
                "\"extra2\":".encodeToByteArray(),
            ),
            omitIfEmpty = false,
            omitIfAbsentIndices = intArrayOf(1),
        )
        assertNull(actual = wrapped)
    }

    @Test
    fun absentSlotsAreOmittedFromMaterializedObject() {
        val capture = GhostWrappedKeysCapture(slotCount = 2)
        capture.put(0, RawJson.fromString(json = "\"a\""))
        val wrapped = capture.materializeWrappedObject(
            keyUtf8Literals = arrayOf(
                "\"extra1\":".encodeToByteArray(),
                "\"extra2\":".encodeToByteArray(),
            ),
            omitIfEmpty = false,
            omitIfAbsentIndices = intArrayOf(),
        )
        assertContentEquals(
            expected = """{"extra1":"a"}""".encodeToByteArray(),
            actual = wrapped
        )
    }
}

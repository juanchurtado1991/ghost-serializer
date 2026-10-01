@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.bytes.extensions.captureRawJson
import com.ghost.serialization.parser.common.json.JsonReaderOptions
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.captureRawJson
import com.ghost.serialization.parser.streaming.captureRawJsonBytes
import com.ghost.serialization.parser.streaming.selectNameAndConsume
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.beginObject
import com.ghost.serialization.parser.strings.captureRawJson
import com.ghost.serialization.parser.strings.captureRawJsonBytes
import com.ghost.serialization.parser.strings.consumeKeySeparator
import com.ghost.serialization.parser.strings.nextKey
import com.ghost.serialization.parser.strings.nextString
import com.ghost.serialization.types.RawJson
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue


class CaptureRawJsonTest {

    @Test
    fun captureRawJsonAliasesInputBufferForStandaloneValue() {
        val json = """{"k":"v"}""".encodeToByteArray()
        val reader = GhostJsonReader(json)
        val captured = reader.captureRawJson()

        assertSame(
            expected = json,
            actual = captured.storage
        )
        assertEquals(
            expected = 0,
            actual = captured.storageOffset
        )
        assertEquals(
            expected = json.size,
            actual = captured.storageLength
        )
    }

    @Test
    fun captureRawJsonBytesMaterializesCopy() {
        val json = """{"body":{"k":"v"}}""".encodeToByteArray()
        val reader = GhostJsonReader(json)
        reader.beginObject()
        reader.selectNameAndConsume(
            options = JsonReaderOptions.of(0, 31, 128, true, "body")
        )

        val bytes = reader.captureRawJsonBytes()
        assertContentEquals(
            expected = """{"k":"v"}""".encodeToByteArray(),
            actual = bytes
        )
    }

    @Test
    fun rawJsonBytesGetterCopiesSliceOnlyWhenNeeded() {
        val json = """{"meta":123}""".encodeToByteArray()
        val reader = GhostJsonReader(json)
        reader.beginObject()
        reader.selectNameAndConsume(
            options = JsonReaderOptions.of(0, 31, 128, true, "meta")
        )

        val captured = reader.captureRawJson()
        val materialized = captured.bytes

        assertEquals(
            expected = "123",
            actual = materialized.decodeToString()
        )
        assertNotSame(
            illegal = captured.storage,
            actual = materialized
        )
    }

    @Test
    fun roundTripObjectViaRawJsonSerializer() {
        val json = """{"enabled":true,"tags":["a","b"]}"""
        val value = Ghost.deserialize<RawJson>(json.encodeToByteArray())
        val restored = Ghost.deserialize<RawJson>(Ghost.serialize(value))
        assertTrue(actual = value.contentEquals(restored))
    }

    @Test
    fun captureRawJsonStreamingReaderAliasesInputBuffer() {
        val json = """{"k":"v"}""".encodeToByteArray()
        val reader = GhostJsonReader(json)
        val captured = reader.captureRawJson()

        assertSame(
            expected = json,
            actual = captured.storage
        )
        assertEquals(
            expected = 0,
            actual = captured.storageOffset
        )
        assertEquals(
            expected = json.size,
            actual = captured.storageLength
        )
    }

    @Test
    fun captureRawJsonFlatReaderMaterializesOwnedBytesWhenBridgedFromString() {
        val json = """{"body":{"k":"v"}}""".encodeToByteArray()
        val reader = GhostJsonFlatReader(rawData = json).also {
            it.materializeRawJsonCaptures = true
        }
        reader.beginObject()
        reader.selectNameAndConsume(
            options = JsonReaderOptions.of(0, 31, 128, true, "body")
        )

        val captured = reader.captureRawJson()
        assertNotSame(
            illegal = json,
            actual = captured.storage
        )
        assertEquals(
            expected = 0,
            actual = captured.storageOffset
        )
        assertEquals(
            expected = """{"k":"v"}""",
            actual = captured.decodeToString()
        )
    }

    @Test
    fun captureRawJsonStringReaderMaterializesOwnedBytes() {
        val json = """{"k":"v"}"""
        val reader = GhostJsonStringReader(rawData = json)
        val captured = reader.captureRawJson()

        assertNotSame(
            illegal = json.encodeToByteArray(),
            actual = captured.storage
        )
        assertEquals(
            expected = json,
            actual = captured.decodeToString()
        )
    }

    @Test
    fun captureRawJsonStringReaderEncodesCapturedFieldOnly() {
        fun nested(level: Int): String {
            if (level == 0) return "\"leaf\":true"
            return buildString {
                append('{')
                repeat(4) { index ->
                    if (index > 0) append(',')
                    append("\"k$level$index\":{")
                    append(nested(level = level - 1))
                    append('}')
                }
                append('}')
            }
        }

        val envelope = """{"id":"bench-1","metadata":${nested(3)}}"""
        val reader = GhostJsonStringReader(rawData = envelope)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        reader.nextString()
        reader.nextKey()
        reader.consumeKeySeparator()

        val bytes = reader.captureRawJsonBytes()
        val metadataStart = envelope.indexOf("\"metadata\":") + "\"metadata\":".length
        val expected = envelope.substring(metadataStart, envelope.lastIndex).encodeToByteArray()
        assertContentEquals(
            expected = expected,
            actual = bytes
        )
    }

    @Test
    fun captureRawJsonStreamingReaderMaterializesOwnedBytes() {
        val json = """{"k":"v"}"""
        val reader = GhostJsonReader(Buffer().writeUtf8(json))
        val captured = reader.captureRawJson()

        assertEquals(
            expected = 0,
            actual = captured.storageOffset
        )
        assertEquals(
            expected = json,
            actual = captured.decodeToString()
        )
    }
}

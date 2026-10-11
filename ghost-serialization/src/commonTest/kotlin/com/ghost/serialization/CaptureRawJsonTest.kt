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
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.types.RawJson
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
            if (level == 0) return "{\"leaf\":true}"
            return buildString {
                append('{')
                repeat(4) { index ->
                    if (index > 0) append(',')
                    append("\"k$level$index\":")
                    append(nested(level = level - 1))
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

    @Test
    fun malformedOrTruncatedValueIsRejectedOnEveryChannel() {
        MALFORMED_VALUES.forEach { json ->
            assertFailsWith<GhostJsonException>(message = "string: $json") {
                Ghost.deserialize<RawJson>(json = json)
            }
            assertFailsWith<GhostJsonException>(message = "bytes: $json") {
                Ghost.deserialize<RawJson>(bytes = json.encodeToByteArray())
            }
            assertFailsWith<GhostJsonException>(message = "streaming: $json") {
                Ghost.deserializeStreaming<RawJson>(source = Buffer().writeUtf8(string = json))
            }
        }
    }

    @Test
    fun wellFormedValueIsCapturedVerbatimOnEveryChannel() {
        WELL_FORMED_VALUES.forEach { json ->
            val captured = listOf(
                Ghost.deserialize<RawJson>(json = json),
                Ghost.deserialize<RawJson>(bytes = json.encodeToByteArray()),
                Ghost.deserializeStreaming<RawJson>(source = Buffer().writeUtf8(string = json))
            )
            captured.forEach { value ->
                assertEquals(
                    expected = json,
                    actual = Ghost.serialize(value = value)
                )
            }
        }
    }

    companion object {
        private val MALFORMED_VALUES = listOf(
            "",
            "tru",
            "txyz",
            "nul",
            "abc",
            "-",
            "01",
            "1.",
            "1e",
            "\"abc",
            "\"\\x\"",
            "\"\\u12G4\"",
            "{\"a\":1",
            "{\"a\":}",
            "{\"a\" 1}",
            "{\"a\":1,}",
            "{a:1}",
            "[1,2}",
            "[1 2]",
            "[1,]",
            "[txyz]"
        )
        private val WELL_FORMED_VALUES = listOf(
            "true",
            "null",
            "-0.5e+10",
            "\"a\\u00e9\\n\\\"b\"",
            "{}",
            "[]",
            "{\"a\":[1,{\"b\":null},\"x\"],\"c\":false}",
            "[[[]],{}]"
        )
    }
}

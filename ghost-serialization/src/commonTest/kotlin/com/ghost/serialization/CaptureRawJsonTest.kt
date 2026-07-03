@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.parser.GhostJsonFlatReader
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.GhostJsonStringReader
import com.ghost.serialization.parser.beginObject
import com.ghost.serialization.parser.captureRawJson
import com.ghost.serialization.parser.captureRawJsonBytes
import com.ghost.serialization.parser.consumeKeySeparator
import com.ghost.serialization.parser.nextKey
import com.ghost.serialization.parser.nextString
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
        val reader = GhostJsonFlatReader(json)
        val captured = reader.captureRawJson()

        assertSame(json, captured.storage)
        assertEquals(0, captured.storageOffset)
        assertEquals(json.size, captured.storageLength)
    }

    @Test
    fun captureRawJsonBytesMaterializesCopy() {
        val json = """{"body":{"k":"v"}}""".encodeToByteArray()
        val reader = GhostJsonFlatReader(json)
        reader.beginObject()
        reader.selectNameAndConsume(
            com.ghost.serialization.parser.JsonReaderOptions.of(0, 31, 128, true, "body")
        )

        val bytes = reader.captureRawJsonBytes()
        assertContentEquals("""{"k":"v"}""".encodeToByteArray(), bytes)
    }

    @Test
    fun rawJsonBytesGetterCopiesSliceOnlyWhenNeeded() {
        val json = """{"meta":123}""".encodeToByteArray()
        val reader = GhostJsonFlatReader(json)
        reader.beginObject()
        reader.selectNameAndConsume(
            com.ghost.serialization.parser.JsonReaderOptions.of(0, 31, 128, true, "meta")
        )

        val captured = reader.captureRawJson()
        val materialized = captured.bytes

        assertEquals("123", materialized.decodeToString())
        assertNotSame(captured.storage, materialized)
    }

    @Test
    fun roundTripObjectViaRawJsonSerializer() {
        val json = """{"enabled":true,"tags":["a","b"]}"""
        val value = Ghost.deserialize<RawJson>(json.encodeToByteArray())
        val restored = Ghost.deserialize<RawJson>(Ghost.serialize(value))
        assertTrue(value.contentEquals(restored))
    }

    @Test
    fun captureRawJsonStreamingReaderAliasesInputBuffer() {
        val json = """{"k":"v"}""".encodeToByteArray()
        val reader = GhostJsonReader(json)
        val captured = reader.captureRawJson()

        assertSame(json, captured.storage)
        assertEquals(0, captured.storageOffset)
        assertEquals(json.size, captured.storageLength)
    }

    @Test
    fun captureRawJsonFlatReaderMaterializesOwnedBytesWhenBridgedFromString() {
        val json = """{"body":{"k":"v"}}""".encodeToByteArray()
        val reader = GhostJsonFlatReader(json).also {
            it.materializeRawJsonCaptures = true
        }
        reader.beginObject()
        reader.selectNameAndConsume(
            com.ghost.serialization.parser.JsonReaderOptions.of(0, 31, 128, true, "body")
        )

        val captured = reader.captureRawJson()
        assertNotSame(json, captured.storage)
        assertEquals(0, captured.storageOffset)
        assertEquals("""{"k":"v"}""", captured.decodeToString())
    }

    @Test
    fun captureRawJsonStringReaderMaterializesOwnedBytes() {
        val json = """{"k":"v"}"""
        val reader = GhostJsonStringReader(json)
        val captured = reader.captureRawJson()

        assertNotSame(json.encodeToByteArray(), captured.storage)
        assertEquals(json, captured.decodeToString())
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
                    append(nested(level - 1))
                    append('}')
                }
                append('}')
            }
        }

        val envelope = """{"id":"bench-1","metadata":${nested(3)}}"""
        val reader = GhostJsonStringReader(envelope)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        reader.nextString()
        reader.nextKey()
        reader.consumeKeySeparator()

        val bytes = reader.captureRawJsonBytes()
        val metadataStart = envelope.indexOf("\"metadata\":") + "\"metadata\":".length
        val expected = envelope.substring(metadataStart, envelope.lastIndex).encodeToByteArray()
        assertContentEquals(expected, bytes)
    }

    @Test
    fun captureRawJsonStreamingReaderMaterializesOwnedBytes() {
        val json = """{"k":"v"}"""
        val reader = GhostJsonReader(Buffer().writeUtf8(json))
        val captured = reader.captureRawJson()

        assertEquals(0, captured.storageOffset)
        assertEquals(json, captured.decodeToString())
    }
}

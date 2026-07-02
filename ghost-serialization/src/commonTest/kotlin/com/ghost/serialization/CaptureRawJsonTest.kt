@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.parser.GhostJsonFlatReader
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.GhostJsonStringReader
import com.ghost.serialization.parser.captureRawJson
import com.ghost.serialization.parser.captureRawJsonBytes
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
    fun captureRawJsonStringReaderMaterializesOwnedBytes() {
        val json = """{"k":"v"}"""
        val reader = GhostJsonStringReader(json)
        val captured = reader.captureRawJson()

        assertNotSame(json.encodeToByteArray(), captured.storage)
        assertEquals(json, captured.decodeToString())
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

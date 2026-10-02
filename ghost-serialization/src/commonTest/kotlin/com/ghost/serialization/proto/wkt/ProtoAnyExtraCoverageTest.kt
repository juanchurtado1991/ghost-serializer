package com.ghost.serialization.proto.wkt

import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.writer.bytes.FlatByteArrayWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals


/**
 * Fills gaps [ProtoAnyTest] doesn't cover: the unrecognized-key skip branch, key-order
 * independence, and the streaming reader/writer overloads (`ProtoAnyTest` only exercises
 * the flat path, since `GhostProto.deserialize<T>(String)` always builds a flat reader).
 */
class ProtoAnyExtraCoverageTest {

    @Test
    fun deserialize_skipsUnrecognizedKeys() {
        val json = """{"@type":"type.googleapis.com/x","extra":"ignored","value":"1s"}"""
        val parsed = ProtoAnySerializer.deserialize(GhostJsonReader(json.encodeToByteArray()))
        assertEquals(
            expected = "type.googleapis.com/x",
            actual = parsed.typeUrl
        )
        assertEquals(
            expected = "\"1s\"",
            actual = parsed.value.decodeToString()
        )
    }

    @Test
    fun deserialize_toleratesValueKeyBeforeTypeUrlKey() {
        val json = """{"value":"1s","@type":"type.googleapis.com/x"}"""
        val parsed = ProtoAnySerializer.deserialize(GhostJsonReader(json.encodeToByteArray()))
        assertEquals(
            expected = "type.googleapis.com/x",
            actual = parsed.typeUrl
        )
        assertEquals(
            expected = "\"1s\"",
            actual = parsed.value.decodeToString()
        )
    }

    @Test
    fun streamingWriterAndReader_roundTripWithValue() {
        val original = ProtoAny(typeUrl = "type.googleapis.com/x", value = "\"1s\"".encodeToByteArray())

        val buffer = Buffer()
        val writer = GhostJsonWriter(buffer)
        ProtoAnySerializer.serialize(writer, original)
        writer.flush()
        val json = buffer.readUtf8()
        assertEquals(
            expected = """{"@type":"type.googleapis.com/x","value":"1s"}""",
            actual = json
        )

        val parsed = ProtoAnySerializer.deserialize(GhostJsonReader(json.encodeToByteArray()))
        assertEquals(
            expected = original,
            actual = parsed
        )
    }

    @Test
    fun streamingWriterAndReader_roundTripWithoutValue() {
        val original = ProtoAny(typeUrl = "type.googleapis.com/x", value = ByteArray(0))

        val buffer = Buffer()
        val writer = GhostJsonWriter(buffer)
        ProtoAnySerializer.serialize(writer, original)
        writer.flush()
        val json = buffer.readUtf8()
        assertEquals(
            expected = """{"@type":"type.googleapis.com/x"}""",
            actual = json
        )

        val parsed = ProtoAnySerializer.deserialize(GhostJsonReader(json.encodeToByteArray()))
        assertEquals(
            expected = original,
            actual = parsed
        )
    }

    @Test
    fun flatWriter_omitsValueKeyWhenEmpty() {
        val byteWriter = FlatByteArrayWriter()
        val writer = GhostJsonWriter(byteWriter)
        ProtoAnySerializer.serialize(writer, ProtoAny(typeUrl = "type.googleapis.com/x", value = ByteArray(0)))
        assertEquals(
            expected = """{"@type":"type.googleapis.com/x"}""",
            actual = byteWriter.toStringUtf8()
        )
    }
}

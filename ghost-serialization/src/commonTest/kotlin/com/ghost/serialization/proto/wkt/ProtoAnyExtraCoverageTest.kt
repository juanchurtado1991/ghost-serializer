package com.ghost.serialization.proto.wkt

import com.ghost.serialization.parser.common.*
import com.ghost.serialization.parser.bytes.*
import com.ghost.serialization.parser.strings.*
import com.ghost.serialization.parser.streaming.*
import com.ghost.serialization.parser.proto.*
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.writer.bytes.FlatByteArrayWriter
import com.ghost.serialization.writer.bytes.GhostJsonFlatWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Fills gaps [ProtoAnyTest] doesn't cover: the unrecognized-key `skipValue()` branch, key
 * order independence, and the streaming (`GhostJsonWriter`/`GhostJsonReader`) overloads --
 * `ProtoAnyTest` only ever exercises the flat path (`GhostProto.deserialize<T>(String)`
 * always builds a `GhostProtoJsonFlatReader`).
 */
class ProtoAnyExtraCoverageTest {

    @Test
    fun deserialize_skipsUnrecognizedKeys() {
        val json = """{"@type":"type.googleapis.com/x","extra":"ignored","value":"1s"}"""
        val parsed = ProtoAnySerializer.deserialize(GhostJsonFlatReader(json.encodeToByteArray()))
        assertEquals("type.googleapis.com/x", parsed.typeUrl)
        assertEquals("\"1s\"", parsed.value.decodeToString())
    }

    @Test
    fun deserialize_toleratesValueKeyBeforeTypeUrlKey() {
        val json = """{"value":"1s","@type":"type.googleapis.com/x"}"""
        val parsed = ProtoAnySerializer.deserialize(GhostJsonFlatReader(json.encodeToByteArray()))
        assertEquals("type.googleapis.com/x", parsed.typeUrl)
        assertEquals("\"1s\"", parsed.value.decodeToString())
    }

    @Test
    fun streamingWriterAndReader_roundTripWithValue() {
        val original = ProtoAny("type.googleapis.com/x", "\"1s\"".encodeToByteArray())

        val buffer = Buffer()
        val writer = GhostJsonWriter(buffer)
        ProtoAnySerializer.serialize(writer, original)
        writer.flush()
        val json = buffer.readUtf8()
        assertEquals("""{"@type":"type.googleapis.com/x","value":"1s"}""", json)

        val parsed = ProtoAnySerializer.deserialize(GhostJsonReader(json.encodeToByteArray()))
        assertEquals(original, parsed)
    }

    @Test
    fun streamingWriterAndReader_roundTripWithoutValue() {
        val original = ProtoAny("type.googleapis.com/x", ByteArray(0))

        val buffer = Buffer()
        val writer = GhostJsonWriter(buffer)
        ProtoAnySerializer.serialize(writer, original)
        writer.flush()
        val json = buffer.readUtf8()
        assertEquals("""{"@type":"type.googleapis.com/x"}""", json)

        val parsed = ProtoAnySerializer.deserialize(GhostJsonReader(json.encodeToByteArray()))
        assertEquals(original, parsed)
    }

    @Test
    fun flatWriter_omitsValueKeyWhenEmpty() {
        val byteWriter = FlatByteArrayWriter()
        val writer = GhostJsonFlatWriter(byteWriter)
        ProtoAnySerializer.serialize(writer, ProtoAny("type.googleapis.com/x", ByteArray(0)))
        assertEquals("""{"@type":"type.googleapis.com/x"}""", byteWriter.toStringUtf8())
    }
}

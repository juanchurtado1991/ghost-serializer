package com.ghost.serialization.proto.wkt

import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.writer.bytes.FlatByteArrayWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals


/**
 * Covers [ProtoStructSerializer] (the top-level `ProtoStruct = Map<String, ProtoValue>`
 * entry point, distinct from [ProtoValue.Struct]'s nested variant) directly, since
 * `ProtoStruct` is a type alias whose `::class` erases to `Map::class` and won't dispatch
 * through the `KClass`-keyed registry via `GhostProto.deserialize<ProtoStruct>()`.
 */
class ProtoStructSerializerTest {

    @Test
    fun flatWriter_serializesEmptyStruct() {
        val byteWriter = FlatByteArrayWriter()
        val writer = GhostJsonWriter(byteWriter)
        ProtoStructSerializer.serialize(writer, emptyMap())
        assertEquals(
            expected = "{}",
            actual = byteWriter.toStringUtf8()
        )
    }

    @Test
    fun flatWriter_serializesStructWithMultipleEntries() {
        val byteWriter = FlatByteArrayWriter()
        val writer = GhostJsonWriter(byteWriter)
        val struct: ProtoStruct = linkedMapOf(
            "name" to ProtoValue.Str(value = "ghost"),
            "active" to ProtoValue.Bool(value = true)
        )
        ProtoStructSerializer.serialize(writer, struct)
        assertEquals(
            expected = """{"name":"ghost","active":true}""",
            actual = byteWriter.toStringUtf8()
        )
    }

    @Test
    fun flatReader_deserializesEmptyStruct() {
        val reader = GhostJsonReader("{}".encodeToByteArray())
        assertEquals(
            expected = emptyMap(),
            actual = ProtoStructSerializer.deserialize(reader)
        )
    }

    @Test
    fun flatReader_deserializesStructWithMultipleEntries() {
        val reader = GhostJsonReader("""{"a":1.0,"b":"x"}""".encodeToByteArray())
        val result = ProtoStructSerializer.deserialize(reader)
        assertEquals(
            expected = 2,
            actual = result.size
        )
        assertEquals(
            expected = ProtoValue.Number(value = 1.0),
            actual = result["a"]
        )
        assertEquals(
            expected = ProtoValue.Str(value = "x"),
            actual = result["b"]
        )
    }

    @Test
    fun flatWriterAndReader_roundTripNestedStruct() {
        val byteWriter = FlatByteArrayWriter()
        val writer = GhostJsonWriter(byteWriter)
        val struct: ProtoStruct = mapOf(
            "nested" to ProtoValue.Struct(value = mapOf("inner" to ProtoValue.Number(value = 42.0)))
        )
        ProtoStructSerializer.serialize(writer, struct)
        val json = byteWriter.toStringUtf8()
        assertEquals(
            expected = """{"nested":{"inner":42.0}}""",
            actual = json
        )

        val parsed =
            ProtoStructSerializer.deserialize(GhostJsonReader(json.encodeToByteArray()))
        assertEquals(
            expected = struct,
            actual = parsed
        )
    }

    // ── Streaming (GhostJsonWriter/GhostJsonReader) overloads ─────────────

    @Test
    fun streamingWriterAndReader_roundTripStruct() {
        val buffer = Buffer()
        val writer = GhostJsonWriter(buffer)
        val struct: ProtoStruct = linkedMapOf(
            "count" to ProtoValue.Number(value = 3.0),
            "tags" to ProtoValue.List(value = listOf(ProtoValue.Str(value = "a"), ProtoValue.Str(value = "b")))
        )
        ProtoStructSerializer.serialize(writer, struct)
        writer.flush()
        val json = buffer.readUtf8()
        assertEquals(
            expected = """{"count":3.0,"tags":["a","b"]}""",
            actual = json
        )

        val parsed = ProtoStructSerializer.deserialize(GhostJsonReader(json.encodeToByteArray()))
        assertEquals(
            expected = struct,
            actual = parsed
        )
    }
}

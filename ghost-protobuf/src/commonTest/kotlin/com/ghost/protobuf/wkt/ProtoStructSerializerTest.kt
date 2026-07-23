package com.ghost.protobuf.wkt

import com.ghost.serialization.parser.GhostJsonFlatReader
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.writer.FlatByteArrayWriter
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.GhostJsonWriter
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [ProtoStructSerializer] (the top-level `ProtoStruct = Map<String, ProtoValue>` entry point,
 * distinct from [ProtoValue.Struct]'s nested variant) had no test anywhere in the module.
 * Calls the serializer directly rather than through `GhostProtobuf.deserialize<ProtoStruct>()`,
 * since `ProtoStruct` is a type alias -- `ProtoStruct::class` erases to `Map::class` at
 * runtime, which doesn't reliably dispatch through the `KClass`-keyed registry.
 */
class ProtoStructSerializerTest {

    @Test
    fun flatWriter_serializesEmptyStruct() {
        val byteWriter = FlatByteArrayWriter()
        val writer = GhostJsonFlatWriter(byteWriter)
        ProtoStructSerializer.serialize(writer, emptyMap())
        assertEquals("{}", byteWriter.toStringUtf8())
    }

    @Test
    fun flatWriter_serializesStructWithMultipleEntries() {
        val byteWriter = FlatByteArrayWriter()
        val writer = GhostJsonFlatWriter(byteWriter)
        val struct: ProtoStruct = linkedMapOf(
            "name" to ProtoValue.Str("ghost"),
            "active" to ProtoValue.Bool(true)
        )
        ProtoStructSerializer.serialize(writer, struct)
        assertEquals("""{"name":"ghost","active":true}""", byteWriter.toStringUtf8())
    }

    @Test
    fun flatReader_deserializesEmptyStruct() {
        val reader = GhostJsonFlatReader("{}".encodeToByteArray())
        assertEquals(emptyMap(), ProtoStructSerializer.deserialize(reader))
    }

    @Test
    fun flatReader_deserializesStructWithMultipleEntries() {
        val reader = GhostJsonFlatReader("""{"a":1.0,"b":"x"}""".encodeToByteArray())
        val result = ProtoStructSerializer.deserialize(reader)
        assertEquals(2, result.size)
        assertEquals(ProtoValue.Number(1.0), result["a"])
        assertEquals(ProtoValue.Str("x"), result["b"])
    }

    @Test
    fun flatWriterAndReader_roundTripNestedStruct() {
        val byteWriter = FlatByteArrayWriter()
        val writer = GhostJsonFlatWriter(byteWriter)
        val struct: ProtoStruct = mapOf(
            "nested" to ProtoValue.Struct(mapOf("inner" to ProtoValue.Number(42.0)))
        )
        ProtoStructSerializer.serialize(writer, struct)
        val json = byteWriter.toStringUtf8()
        assertEquals("""{"nested":{"inner":42.0}}""", json)

        val parsed = ProtoStructSerializer.deserialize(GhostJsonFlatReader(json.encodeToByteArray()))
        assertEquals(struct, parsed)
    }

    // ── Streaming (GhostJsonWriter/GhostJsonReader) overloads ─────────────

    @Test
    fun streamingWriterAndReader_roundTripStruct() {
        val buffer = Buffer()
        val writer = GhostJsonWriter(buffer)
        val struct: ProtoStruct = linkedMapOf(
            "count" to ProtoValue.Number(3.0),
            "tags" to ProtoValue.List(listOf(ProtoValue.Str("a"), ProtoValue.Str("b")))
        )
        ProtoStructSerializer.serialize(writer, struct)
        writer.flush()
        val json = buffer.readUtf8()
        assertEquals("""{"count":3.0,"tags":["a","b"]}""", json)

        val parsed = ProtoStructSerializer.deserialize(GhostJsonReader(json.encodeToByteArray()))
        assertEquals(struct, parsed)
    }
}

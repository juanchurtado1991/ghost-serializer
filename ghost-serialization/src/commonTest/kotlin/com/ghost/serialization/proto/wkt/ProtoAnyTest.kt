package com.ghost.serialization.proto.wkt

import com.ghost.serialization.Ghost
import com.ghost.serialization.contract.AbstractGhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.proto.GhostProto
import com.ghost.serialization.writer.bytes.FlatByteArrayWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import kotlin.test.Test
import kotlin.test.assertEquals

class ProtoAnyTest {

    init {
        val registry = object : AbstractGhostRegistry() {
            private val map =
                mapOf<kotlin.reflect.KClass<*>, GhostSerializer<*>>(
                    ProtoAny::class to ProtoAnySerializer
                )

            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> getSerializer(clazz: kotlin.reflect.KClass<T>): GhostSerializer<T>? {
                return map[clazz] as? GhostSerializer<T>
            }

            override fun getAllSerializers(): Map<kotlin.reflect.KClass<*>, GhostSerializer<*>> {
                return map
            }

        }
        Ghost.addRegistry(registry = registry)
    }

    @Test
    fun testAnyDeserialization() {
        val json =
            "{\"@type\":\"type.googleapis.com/google.protobuf.Duration\",\"value\":\"10.5s\"}"
        val parsed = GhostProto.deserialize<ProtoAny>(json)
        assertEquals(
            expected = "type.googleapis.com/google.protobuf.Duration",
            actual = parsed.typeUrl
        )
        assertEquals(
            expected = "\"10.5s\"",
            actual = parsed.value.decodeToString()
        )
    }

    @Test
    fun testAnyRoundtripPreservesPayload() {
        // Regression: ProtoAnySerializer used to silently drop the "value" payload on both
        // serialize and deserialize, returning ByteArray(0) unconditionally.
        val json =
            "{\"@type\":\"type.googleapis.com/google.protobuf.Struct\",\"value\":{\"a\":1,\"b\":\"c\"}}"
        val parsed = GhostProto.deserialize<ProtoAny>(json)
        assertEquals(
            expected = "{\"a\":1,\"b\":\"c\"}",
            actual = parsed.value.decodeToString()
        )

        val flatBuffer = FlatByteArrayWriter(initialCapacity = 256)
        val writer = GhostJsonWriter(flatBuffer)
        ProtoAnySerializer.serialize(writer, parsed)
        assertEquals(
            expected = json,
            actual = flatBuffer.toStringUtf8()
        )

        val reparsed = GhostProto.deserialize<ProtoAny>(flatBuffer.toStringUtf8())
        assertEquals(
            expected = parsed,
            actual = reparsed
        )
    }

    @Test
    fun testAnyWithoutValueKeyRoundtrips() {
        val json = "{\"@type\":\"type.googleapis.com/google.protobuf.Empty\"}"
        val parsed = GhostProto.deserialize<ProtoAny>(json)
        assertEquals(
            expected = "type.googleapis.com/google.protobuf.Empty",
            actual = parsed.typeUrl
        )
        assertEquals(
            expected = 0,
            actual = parsed.value.size
        )
    }
}

package com.ghost.protobuf.wkt

import com.ghost.protobuf.GhostProtobuf
import kotlin.test.Test
import kotlin.test.assertEquals

class ProtoAnyTest {

    init {
        val registry = object : com.ghost.serialization.contract.GhostRegistry {
            private val map = mapOf<kotlin.reflect.KClass<*>, com.ghost.serialization.contract.GhostSerializer<*>>(
                ProtoAny::class to ProtoAnySerializer
            )
            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> getSerializer(clazz: kotlin.reflect.KClass<T>): com.ghost.serialization.contract.GhostSerializer<T>? {
                return map[clazz] as? com.ghost.serialization.contract.GhostSerializer<T>
            }
            override fun getAllSerializers(): Map<kotlin.reflect.KClass<*>, com.ghost.serialization.contract.GhostSerializer<*>> {
                return map
            }
        }
        com.ghost.serialization.Ghost.addRegistry(registry)
    }

    @Test
    fun testAnyDeserialization() {
        val json = "{\"@type\":\"type.googleapis.com/google.protobuf.Duration\",\"value\":\"10.5s\"}"
        val parsed = GhostProtobuf.deserialize<ProtoAny>(json)
        assertEquals("type.googleapis.com/google.protobuf.Duration", parsed.typeUrl)
        assertEquals("\"10.5s\"", parsed.value.decodeToString())
    }

    @Test
    fun testAnyRoundtripPreservesPayload() {
        // Regression: ProtoAnySerializer used to silently drop the "value" payload on both
        // serialize and deserialize, returning ByteArray(0) unconditionally.
        val json = "{\"@type\":\"type.googleapis.com/google.protobuf.Struct\",\"value\":{\"a\":1,\"b\":\"c\"}}"
        val parsed = GhostProtobuf.deserialize<ProtoAny>(json)
        assertEquals("{\"a\":1,\"b\":\"c\"}", parsed.value.decodeToString())

        val flatBuffer = com.ghost.serialization.writer.FlatByteArrayWriter(256)
        val writer = com.ghost.serialization.writer.GhostJsonFlatWriter(flatBuffer)
        ProtoAnySerializer.serialize(writer, parsed)
        assertEquals(json, flatBuffer.toStringUtf8())

        val reparsed = GhostProtobuf.deserialize<ProtoAny>(flatBuffer.toStringUtf8())
        assertEquals(parsed, reparsed)
    }

    @Test
    fun testAnyWithoutValueKeyRoundtrips() {
        val json = "{\"@type\":\"type.googleapis.com/google.protobuf.Empty\"}"
        val parsed = GhostProtobuf.deserialize<ProtoAny>(json)
        assertEquals("type.googleapis.com/google.protobuf.Empty", parsed.typeUrl)
        assertEquals(0, parsed.value.size)
    }
}

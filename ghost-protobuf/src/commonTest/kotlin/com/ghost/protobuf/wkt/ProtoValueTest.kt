package com.ghost.protobuf.wkt

import com.ghost.protobuf.GhostProtobuf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProtoValueTest {

    init {
        val registry = object : com.ghost.serialization.contract.GhostRegistry {
            private val map = mapOf<kotlin.reflect.KClass<*>, com.ghost.serialization.contract.GhostSerializer<*>>(
                ProtoValue::class to ProtoValueSerializer
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
    fun testNullValue() {
        val parsed = GhostProtobuf.deserialize<ProtoValue>("null")
        assertTrue(parsed is ProtoValue.Null)
    }

    @Test
    fun testBoolValue() {
        val parsed = GhostProtobuf.deserialize<ProtoValue>("true")
        assertTrue(parsed is ProtoValue.Bool)
        assertTrue(parsed.v)
    }

    @Test
    fun testNumberValue() {
        val parsed = GhostProtobuf.deserialize<ProtoValue>("123.45")
        assertTrue(parsed is ProtoValue.Number)
        assertEquals(123.45, parsed.v)
    }

    @Test
    fun testStringValue() {
        val parsed = GhostProtobuf.deserialize<ProtoValue>("\"test-str\"")
        assertTrue(parsed is ProtoValue.Str)
        assertEquals("test-str", parsed.v)
    }

    @Test
    fun testListValue() {
        val parsed = GhostProtobuf.deserialize<ProtoValue>("[null, true, 42.0, \"abc\"]")
        assertTrue(parsed is ProtoValue.List)
        assertEquals(4, parsed.v.size)
        assertTrue(parsed.v[0] is ProtoValue.Null)
        assertTrue(parsed.v[1] is ProtoValue.Bool)
        assertTrue(parsed.v[2] is ProtoValue.Number)
        assertTrue(parsed.v[3] is ProtoValue.Str)
    }

    @Test
    fun testStructValue() {
        val parsed = GhostProtobuf.deserialize<ProtoValue>("{\"key1\":true,\"key2\":[1.0]}")
        assertTrue(parsed is ProtoValue.Struct)
        val map = parsed.v
        assertTrue(map["key1"] is ProtoValue.Bool)
        assertTrue(map["key2"] is ProtoValue.List)
    }
}

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.proto.GhostProto
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
        val parsed = GhostProto.deserialize<ProtoValue>("null")
        assertTrue(parsed is ProtoValue.Null)
    }

    @Test
    fun testBoolValue() {
        val parsed = GhostProto.deserialize<ProtoValue>("true")
        assertTrue(parsed is ProtoValue.Bool)
        assertTrue(parsed.value)
    }

    @Test
    fun testNumberValue() {
        val parsed = GhostProto.deserialize<ProtoValue>("123.45")
        assertTrue(parsed is ProtoValue.Number)
        assertEquals(123.45, parsed.value)
    }

    @Test
    fun testStringValue() {
        val parsed = GhostProto.deserialize<ProtoValue>("\"test-str\"")
        assertTrue(parsed is ProtoValue.Str)
        assertEquals("test-str", parsed.value)
    }

    @Test
    fun testListValue() {
        val parsed = GhostProto.deserialize<ProtoValue>("[null, true, 42.0, \"abc\"]")
        assertTrue(parsed is ProtoValue.List)
        assertEquals(4, parsed.value.size)
        assertTrue(parsed.value[0] is ProtoValue.Null)
        assertTrue(parsed.value[1] is ProtoValue.Bool)
        assertTrue(parsed.value[2] is ProtoValue.Number)
        assertTrue(parsed.value[3] is ProtoValue.Str)
    }

    @Test
    fun testStructValue() {
        val parsed = GhostProto.deserialize<ProtoValue>("{\"key1\":true,\"key2\":[1.0]}")
        assertTrue(parsed is ProtoValue.Struct)
        val map = parsed.value
        assertTrue(map["key1"] is ProtoValue.Bool)
        assertTrue(map["key2"] is ProtoValue.List)
    }
}

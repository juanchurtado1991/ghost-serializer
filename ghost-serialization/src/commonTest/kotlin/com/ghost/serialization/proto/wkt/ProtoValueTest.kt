package com.ghost.serialization.proto.wkt

import com.ghost.serialization.Ghost
import com.ghost.serialization.contract.AbstractGhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.proto.GhostProto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProtoValueTest {

    init {
        val registry = object : AbstractGhostRegistry() {
            private val map =
                mapOf<kotlin.reflect.KClass<*>, GhostSerializer<*>>(
                    ProtoValue::class to ProtoValueSerializer
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
    fun testNullValue() {
        val parsed = GhostProto.deserialize<ProtoValue>("null")
        assertTrue(actual = parsed is ProtoValue.Null)
    }

    @Test
    fun testBoolValue() {
        val parsed = GhostProto.deserialize<ProtoValue>("true")
        assertTrue(actual = parsed is ProtoValue.Bool)
        assertTrue(actual = parsed.value)
    }

    @Test
    fun testNumberValue() {
        val parsed = GhostProto.deserialize<ProtoValue>("123.45")
        assertTrue(actual = parsed is ProtoValue.Number)
        assertEquals(
            expected = 123.45,
            actual = parsed.value
        )
    }

    @Test
    fun testStringValue() {
        val parsed = GhostProto.deserialize<ProtoValue>("\"test-str\"")
        assertTrue(actual = parsed is ProtoValue.Str)
        assertEquals(
            expected = "test-str",
            actual = parsed.value
        )
    }

    @Test
    fun testListValue() {
        val parsed = GhostProto.deserialize<ProtoValue>("[null, true, 42.0, \"abc\"]")
        assertTrue(actual = parsed is ProtoValue.List)
        assertEquals(
            expected = 4,
            actual = parsed.value.size
        )
        assertTrue(actual = parsed.value[0] is ProtoValue.Null)
        assertTrue(actual = parsed.value[1] is ProtoValue.Bool)
        assertTrue(actual = parsed.value[2] is ProtoValue.Number)
        assertTrue(actual = parsed.value[3] is ProtoValue.Str)
    }

    @Test
    fun testStructValue() {
        val parsed = GhostProto.deserialize<ProtoValue>("{\"key1\":true,\"key2\":[1.0]}")
        assertTrue(actual = parsed is ProtoValue.Struct)
        val map = parsed.value
        assertTrue(actual = map["key1"] is ProtoValue.Bool)
        assertTrue(actual = map["key2"] is ProtoValue.List)
    }
}

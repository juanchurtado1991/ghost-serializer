package com.ghost.protobuf.wkt

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull

class ProtoAnyRegistryTest {

    init {
        val registry = object : com.ghost.serialization.contract.GhostRegistry {
            private val map = mapOf<kotlin.reflect.KClass<*>, com.ghost.serialization.contract.GhostSerializer<*>>(
                ProtoDuration::class to ProtoDurationSerializer,
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

    @AfterTest
    fun tearDown() {
        ProtoAnyRegistry.resetForTest()
    }

    @Test
    fun packAndUnpackRoundTrip() {
        ProtoAnyRegistry.register<ProtoDuration>("type.googleapis.com/google.protobuf.Duration")

        val original = ProtoDuration(123L, 456)
        val any = ProtoAnyRegistry.pack(original)

        assertEquals("type.googleapis.com/google.protobuf.Duration", any.typeUrl)
        assertEquals("\"123.000000456s\"", any.value.decodeToString())

        val unpacked = ProtoAnyRegistry.unpack<ProtoDuration>(any)
        assertEquals(original, unpacked)
    }

    @Test
    fun unpackDynamicResolvesTypeFromTypeUrl() {
        ProtoAnyRegistry.register<ProtoDuration>("type.googleapis.com/google.protobuf.Duration")

        val any = ProtoAnyRegistry.pack(ProtoDuration(5L, 0))
        val dynamic = ProtoAnyRegistry.unpackDynamic(any)

        assertEquals(ProtoDuration(5L, 0), dynamic)
    }

    @Test
    fun unpackDynamicReturnsNullForUnregisteredTypeUrl() {
        val any = ProtoAny("type.googleapis.com/unknown.Message", "{}".encodeToByteArray())
        assertNull(ProtoAnyRegistry.unpackDynamic(any))
    }

    @Test
    fun packFailsWithoutRegisteredTypeUrl() {
        assertFails { ProtoAnyRegistry.pack(ProtoDuration(1L, 0)) }
    }
}

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.Ghost
import com.ghost.serialization.contract.AbstractGhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull

class ProtoAnyRegistryTest {

    init {
        val registry = object : AbstractGhostRegistry() {
            private val map =
                mapOf<kotlin.reflect.KClass<*>, GhostSerializer<*>>(
                    ProtoDuration::class to ProtoDurationSerializer,
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

    @AfterTest
    fun tearDown() {
        ProtoAnyRegistry.resetForTest()
    }

    @Test
    fun packAndUnpackRoundTrip() {
        ProtoAnyRegistry.register<ProtoDuration>(typeUrl = "type.googleapis.com/google.protobuf.Duration")

        val original = ProtoDuration(seconds = 123L, nanos = 456)
        val any = ProtoAnyRegistry.pack(message = original)

        assertEquals(
            expected = "type.googleapis.com/google.protobuf.Duration",
            actual = any.typeUrl
        )
        assertEquals(
            expected = "\"123.000000456s\"",
            actual = any.value.decodeToString()
        )

        val unpacked = ProtoAnyRegistry.unpack<ProtoDuration>(any = any)
        assertEquals(
            expected = original,
            actual = unpacked
        )
    }

    @Test
    fun unpackDynamicResolvesTypeFromTypeUrl() {
        ProtoAnyRegistry.register<ProtoDuration>(typeUrl = "type.googleapis.com/google.protobuf.Duration")

        val any = ProtoAnyRegistry.pack(message = ProtoDuration(seconds = 5L, nanos = 0))
        val dynamic = ProtoAnyRegistry.unpackDynamic(any = any)

        assertEquals(
            expected = ProtoDuration(seconds = 5L, nanos = 0),
            actual = dynamic
        )
    }

    @Test
    fun unpackDynamicReturnsNullForUnregisteredTypeUrl() {
        val any = ProtoAny(typeUrl = "type.googleapis.com/unknown.Message", value = "{}".encodeToByteArray())
        assertNull(actual = ProtoAnyRegistry.unpackDynamic(any = any))
    }

    @Test
    fun packFailsWithoutRegisteredTypeUrl() {
        assertFails { ProtoAnyRegistry.pack(message = ProtoDuration(seconds = 1L, nanos = 0)) }
    }

    @Test
    fun unpackFailsOnTypeUrlMismatch() {
        ProtoAnyRegistry.register<ProtoDuration>(typeUrl = "type.googleapis.com/google.protobuf.Duration")
        ProtoAnyRegistry.register<ProtoEmpty>(typeUrl = "type.googleapis.com/google.protobuf.Empty")

        val any = ProtoAnyRegistry.pack(message = ProtoDuration(seconds = 1L, nanos = 0))
        assertFails { ProtoAnyRegistry.unpack<ProtoEmpty>(any = any) }
    }
}

package com.ghost.serialization.proto

import com.ghost.serialization.Ghost
import com.ghost.serialization.contract.AbstractGhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.proto.wkt.ProtoDuration
import com.ghost.serialization.proto.wkt.ProtoDurationSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class GhostProtoEntryPointsTest {

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

    @Test
    fun deserializeByKClassMatchesReifiedOverload() {
        val json = "\"10.5s\""
        val viaReified: ProtoDuration = GhostProto.deserialize(json)
        val viaKClass = GhostProto.deserialize(json.encodeToByteArray(), ProtoDuration::class)
        assertEquals(
            expected = viaReified,
            actual = viaKClass
        )
    }

    @Test
    fun deserializeByKClassThrowsWhenUnregistered() {
        data class Unregistered(val x: Int)
        assertFails { GhostProto.deserialize("{}".encodeToByteArray(), Unregistered::class) }
    }

    @Test
    fun encodeToBytesAndStringMatchGhostDirectly() {
        val value = ProtoDuration(seconds = 42L, nanos = 0)
        assertEquals(
            expected = Ghost.encodeToString(value),
            actual = GhostProto.encodeToString(value)
        )
        assertEquals(
            expected = Ghost.encodeToBytes(value).decodeToString(),
            actual = GhostProto.encodeToBytes(value).decodeToString()
        )
    }
}

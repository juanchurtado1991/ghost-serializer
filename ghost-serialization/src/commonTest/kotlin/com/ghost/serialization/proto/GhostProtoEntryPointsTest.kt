package com.ghost.serialization.proto

import com.ghost.serialization.proto.wkt.ProtoDuration
import com.ghost.serialization.proto.wkt.ProtoDurationSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails


class GhostProtoEntryPointsTest {

    init {
        val registry = object : com.ghost.serialization.contract.GhostRegistry {
            private val map =
                mapOf<kotlin.reflect.KClass<*>, com.ghost.serialization.contract.GhostSerializer<*>>(
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

    @Test
    fun deserializeByKClassMatchesReifiedOverload() {
        val json = "\"10.5s\""
        val viaReified: ProtoDuration = GhostProto.deserialize(json)
        val viaKClass = GhostProto.deserialize(json.encodeToByteArray(), ProtoDuration::class)
        assertEquals(viaReified, viaKClass)
    }

    @Test
    fun deserializeByKClassThrowsWhenUnregistered() {
        data class Unregistered(val x: Int)
        assertFails { GhostProto.deserialize("{}".encodeToByteArray(), Unregistered::class) }
    }

    @Test
    fun encodeToBytesAndStringMatchGhostDirectly() {
        val value = ProtoDuration(42L, 0)
        assertEquals(
            com.ghost.serialization.Ghost.encodeToString(value),
            GhostProto.encodeToString(value)
        )
        assertEquals(
            com.ghost.serialization.Ghost.encodeToBytes(value).decodeToString(),
            GhostProto.encodeToBytes(value).decodeToString()
        )
    }
}

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
    }
}

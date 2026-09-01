package com.ghost.serialization

import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeepPrewarmValidationTest {

    @Test
    fun `prewarm should populate serializer cache eagerly`() {
        val mockRegistry = object : GhostRegistry {
            override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? = null

            override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> {
                return mapOf(
                    String::class to com.ghost.serialization.serializers.StringSerializer as GhostSerializer<*>
                )
            }
        }

        Ghost.serializerCache.clear()
        Ghost.addRegistry(mockRegistry)

        // serializerCache is internal; verified indirectly via the prewarm effect below
        Ghost.prewarm()

        val serializer = Ghost.getSerializer(String::class)
        assertTrue(
            Ghost.serializerCache.containsKey(String::class),
            "Cache should contain String::class after deep prewarm"
        )
        assertEquals(com.ghost.serialization.serializers.StringSerializer, serializer)
    }
}

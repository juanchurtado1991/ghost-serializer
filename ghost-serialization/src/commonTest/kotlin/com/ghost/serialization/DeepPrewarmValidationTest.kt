package com.ghost.serialization

import com.ghost.serialization.contract.AbstractGhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeepPrewarmValidationTest {

    @Test
    fun `prewarm should populate serializer cache eagerly`() {
        val mockRegistry = object : AbstractGhostRegistry() {
            override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? = null

            override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> {
                return mapOf(
                    String::class to com.ghost.serialization.serializers.StringSerializer as GhostSerializer<*>
                )
            }

        }

        Ghost.serializerCache.clear()
        Ghost.addRegistry(registry = mockRegistry)

        // serializerCache is internal; verified indirectly via the prewarm effect below
        Ghost.prewarm()

        val serializer = Ghost.getSerializer(String::class)
        assertTrue(
            actual = Ghost.serializerCache.containsKey(key = String::class),
            message = "Cache should contain String::class after deep prewarm"
        )
        assertEquals(
            expected = com.ghost.serialization.serializers.StringSerializer,
            actual = serializer
        )
    }
}

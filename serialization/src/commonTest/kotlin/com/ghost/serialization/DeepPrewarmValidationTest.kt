package com.ghostserializer
import kotlin.test.assertTrue

import com.ghostserializer.core.contract.GhostRegistry
import com.ghostserializer.core.contract.GhostSerializer
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeepPrewarmValidationTest {

    @Test
    fun `prewarm should populate serializer cache eagerly`() {
        // 1. Create a mock registry
        val mockRegistry = object : GhostRegistry {
            override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? = null
            
            override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> {
                return mapOf(
                    String::class to com.ghostserializer.serializers.StringSerializer as GhostSerializer<*>
                )
            }
        }

        // 2. Clear cache and register
        serializerCache.clear()
        Ghost.addRegistry(mockRegistry)

        // 3. Verify cache is empty before prewarm (discovered registries are lazy)
        // Note: serializerCache is internal, we check it via prewarm effect
        
        // 4. Trigger Deep Prewarm
        Ghost.prewarm()

        // 5. Verify the type is now in the cache
        val serializer = Ghost.getSerializer(String::class)
        assertTrue(serializerCache.containsKey(String::class), "Cache should contain String::class after deep prewarm")
        assertEquals(com.ghostserializer.serializers.StringSerializer, serializer)
    }
}

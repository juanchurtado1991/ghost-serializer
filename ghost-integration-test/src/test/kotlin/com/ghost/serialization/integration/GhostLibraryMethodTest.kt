package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.integration.model.IgnoreModel
import com.ghost.serialization.integration.model.NamingModel
import kotlin.reflect.KClass
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(InternalGhostApi::class)
class GhostLibraryMethodTest {

    @BeforeTest
    fun setup() {
        Ghost.resetForTest()
    }

    @Test
    fun testPrewarm() {
        // Prewarm should not crash and should populate caches
        Ghost.prewarm()
        
        // After prewarm, common serializers should be in cache
        // Note: they are in discovered registries, so prewarm should have pulled them
        val serializer = Ghost.getSerializer(IgnoreModel::class)
        assertNotNull(serializer)
    }

    @Test
    fun testAddRegistryManual() {
        val myRegistry = object : GhostRegistry {
            override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? = null
            override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> = emptyMap()
            override fun prewarm() {}
        }
        
        Ghost.addRegistry(myRegistry)
        // Verify it was added (indirectly by checking if we can still get standard ones)
        assertNotNull(Ghost.getSerializer(NamingModel::class))
    }

    @Test
    fun testGetSerializerByName() {
        Ghost.prewarm()
        val names = Ghost.getSerializerNames()
        println("Registered Serializers: $names")
        val serializer = Ghost.getSerializerByName("NamingModel")
        assertNotNull(serializer, "Serializer for NamingModel should be found by name. Available: $names")
        assertEquals("NamingModel", serializer.typeName)
    }

    @Test
    fun testResetForTest() {
        Ghost.addRegistry(object : GhostRegistry {
            override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? = null
            override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> = emptyMap()
        })
        
        Ghost.resetForTest()
        // Caches should be empty (though they might re-populate on demand via discovery)
        // But manual registries should be gone.
    }
}

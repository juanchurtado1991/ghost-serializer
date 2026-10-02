package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.integration.model.IgnoreModel
import com.ghost.serialization.integration.model.NamingModel
import kotlin.reflect.KClass
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(InternalGhostApi::class)
class GhostLibraryMethodTest {

    @BeforeTest
    fun setup() {
        Ghost.resetForTest()
    }

    @Test
    fun testPrewarm() {
        Ghost.prewarm()

        // IgnoreModel lives in a discovered registry; prewarm should have pulled it in
        val serializer = Ghost.getSerializer(IgnoreModel::class)
        assertNotNull(actual = serializer)
    }

    @Test
    fun testAddRegistryManual() {
        val myRegistry = object : AbstractGhostRegistry() {
            override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? = null
            override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> = emptyMap()
        }

        Ghost.addRegistry(registry = myRegistry)
        // myRegistry returns null, so this only proves discovery still works alongside it
        assertNotNull(actual = Ghost.getSerializer(NamingModel::class))
    }

    @Test
    fun testGetSerializerByName() {
        Ghost.prewarm()
        val names = Ghost.getSerializerNames()
        println("Registered Serializers: $names")
        val serializer = Ghost.getSerializerByName(name = "NamingModel")
        assertNotNull(
            actual = serializer,
            message = "Serializer for NamingModel should be found by name. Available: $names"
        )
        assertEquals(expected = "NamingModel", actual = serializer.typeName)
    }

    @Test
    fun testResetForTest() {
        Ghost.addRegistry(registry = object : AbstractGhostRegistry() {
            override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? = null
            override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> = emptyMap()
        })

        Ghost.resetForTest()
        // No assertion: this only checks resetForTest doesn't throw after a manual registry was added.
    }
}

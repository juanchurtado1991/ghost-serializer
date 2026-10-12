@file:Suppress("UNCHECKED_CAST")

package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.integration.model.ContextualModel
import com.ghost.serialization.integration.model.ExternalColor
import com.ghost.serialization.integration.model.ExternalColorSerializer
import com.ghost.serialization.integration.model.ExternalDate
import com.ghost.serialization.integration.model.ExternalDateSerializer
import com.ghost.serialization.integration.model.ModelWithExternal
import kotlin.reflect.KClass
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(InternalGhostApi::class)
class GhostContextualTest {

    private val manualRegistry = object : AbstractGhostRegistry() {
        override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? {
            return getAllSerializers()[clazz] as GhostSerializer<T>?
        }

        override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> {
            return mapOf(
                ExternalDate::class to ExternalDateSerializer,
                ExternalColor::class to ExternalColorSerializer
            )
        }
    }

    @BeforeTest
    fun setup() {
        Ghost.resetForTest()
        Ghost.addRegistry(registry = manualRegistry)
    }

    @Test
    fun prewarmBeforeContextualRegistryLeavesSerializersUsable() {
        Ghost.resetForTest()
        Ghost.prewarm()
        Ghost.addRegistry(registry = manualRegistry)
        val model = ContextualModel(id = "c2", color = ExternalColor(r = 0, g = 128, b = 255))

        val roundtrip = Ghost.deserialize<ContextualModel>(Ghost.serialize(model))

        assertEquals(expected = model, actual = roundtrip)
    }

    @Test
    fun testExternalContextualSerialization() {
        val model = ModelWithExternal(id = 1, date = ExternalDate(timestamp = 1672531200000L))
        val json = Ghost.serialize(model)

        // ExternalDateSerializer writes the timestamp as a raw string
        assertEquals(expected = """{"id":1,"date":"1672531200000"}""", actual = json)

        val deserialized = Ghost.deserialize<ModelWithExternal>(json)
        assertEquals(expected = model, actual = deserialized)
    }

    @Test
    fun testExternalColorContextualSerialization() {
        val model = ContextualModel(id = "c1", color = ExternalColor(r = 255, g = 0, b = 0))

        val json = Ghost.serialize(model)

        assertTrue(actual = json.contains("\"color\":\"#ff0000\""))

        val roundtrip = Ghost.deserialize<ContextualModel>(json)
        assertEquals(expected = model, actual = roundtrip)
    }
}

package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.integration.model.ExternalDate
import com.ghost.serialization.integration.model.ExternalDateSerializer
import com.ghost.serialization.integration.model.ModelWithExternal
import kotlin.reflect.KClass
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(InternalGhostApi::class)
class GhostContextualTest {

    @BeforeTest
    fun setup() {
        Ghost.resetForTest()
        
        // Register the external serializer manually
        val manualRegistry = object : GhostRegistry {
            override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? {
                return if (clazz == ExternalDate::class) {
                    ExternalDateSerializer as GhostSerializer<T>
                } else null
            }

            override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> {
                return mapOf(ExternalDate::class to ExternalDateSerializer)
            }
        }
        
        Ghost.addRegistry(manualRegistry)
    }

    @Test
    fun testExternalContextualSerialization() {
        val model = ModelWithExternal(id = 1, date = ExternalDate(1672531200000L))
        val json = Ghost.serialize(model)
        
        // ExternalDateSerializer writes the timestamp as a raw string
        assertEquals("""{"id":1,"date":"1672531200000"}""", json)
        
        val deserialized = Ghost.deserialize<ModelWithExternal>(json)
        assertEquals(model, deserialized)
    }
}

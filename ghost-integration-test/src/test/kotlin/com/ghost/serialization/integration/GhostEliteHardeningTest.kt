@file:OptIn(InternalGhostApi::class)
package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.integration.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GhostEliteHardeningTest {

    @Test
    fun testFlatteningCollision() {
        // CollisionModel has 'name' and child.name flattened into 'meta'.
        // The JSON should have two 'name' fields if we aren't careful, 
        // but Ghost handles paths by nesting them.
        val json = """
        {
            "name": "parent",
            "meta": {
                "name": "child",
                "value": 123
            }
        }
        """.trimIndent()

        val model = Ghost.deserialize<StructuralCollisionModel>(json)
        assertEquals("parent", model.name)
        assertEquals("child", model.child.name)
        assertEquals(123, model.child.value)

        val serialized = Ghost.serialize(model)
        assertTrue(serialized.contains("\"name\":\"parent\""))
        assertTrue(serialized.contains("\"meta\":{"))
        assertTrue(serialized.contains("\"name\":\"child\""))
    }

    @Test
    fun testSharedWrapPaths() {
        val model = WrapSharedPathModel(name = "Ghost", token = "SECRET", active = true)
        val json = Ghost.serialize(model)
        
        // Should produce nested structure: {"metadata":{"info":{"name":"Ghost"},"auth":{"token":"SECRET"}},"system":{"flags":{"active":true}}}
        assertTrue(json.contains("\"metadata\":{"))
        assertTrue(json.contains("\"info\":{"))
        assertTrue(json.contains("\"auth\":{"))
        
        val roundtrip = Ghost.deserialize<WrapSharedPathModel>(json)
        assertEquals(model, roundtrip)
    }

    @Test
    fun testExtremeBooleanCoercion() {
        val json = """
        {
            "b1": "true",
            "b2": "yes",
            "b3": "on",
            "b4": 1,
            "b5": "y",
            "b6": true
        }
        """.trimIndent()

        val model = Ghost.deserialize<CoercionStressModel>(json) {
            it.coerceBooleans = true
        }
        assertTrue(model.b1)
        assertTrue(model.b2)
        assertTrue(model.b3)
        assertTrue(model.b4)
        assertTrue(model.b5)
        assertTrue(model.b6)

        val jsonFalse = """
        {
            "b1": "false",
            "b2": "no",
            "b3": "off",
            "b4": 0,
            "b5": "n",
            "b6": false
        }
        """.trimIndent()

        val modelFalse = Ghost.deserialize<CoercionStressModel>(jsonFalse) {
            it.coerceBooleans = true
        }
        assertFalse(modelFalse.b1)
        assertFalse(modelFalse.b2)
        assertFalse(modelFalse.b3)
        assertFalse(modelFalse.b4)
        assertFalse(modelFalse.b5)
        assertFalse(modelFalse.b6)
    }

    @Test
    fun testDeepListResilience() {
        // One item is good, one is bad (value has type mismatch but is resilient).
        val json = """
        {
            "id": "deep_1",
            "list": [
                { "id": "good", "value": 10 },
                { "id": "bad", "value": "NOT_AN_INT" }
            ]
        }
        """.trimIndent()

        val model = Ghost.deserialize<DeepResilientModel>(json)
        assertEquals(2, model.list.size)
        assertEquals(10, model.list[0].value)
        // Item 1 value should be null due to resilience
        assertEquals(null, model.list[1].value)
    }

    @Test
    fun testCustomCoderEdgeCases() {
        val json = """
        {
            "id": "c1",
            "secret": "AABBCC",
            "score": null
        }
        """.trimIndent()

        val model = Ghost.deserialize<CustomCoderStressModel>(json)
        assertEquals("HEX:AABBCC", model.secret)
        // score decoder returns -1 for null
        assertEquals(-1, model.score)

        val serialized = Ghost.serialize(model)
        assertTrue(serialized.contains("\"secret\":\"AABBCC\""))
    }

    @Test
    fun testMissingPolymorphicDiscriminatorWithFallback() {
        // SmartDevice has UnknownDevice as fallback. We provide NO type.
        val json = """
        {
            "id": "h_missing",
            "devices": [
                { "brightness": 10 }
            ]
        }
        """.trimIndent()

        val home = Ghost.deserialize<SmartHome>(json)
        assertTrue(home.devices[0] is SmartDevice.UnknownDevice)
    }

    @Test
    fun testContextualSerializer() {
        Ghost.resetForTest()
        val registry = object : GhostRegistry {
            override fun <T : Any> getSerializer(clazz: kotlin.reflect.KClass<T>): GhostSerializer<T>? {
                if (clazz == ExternalColor::class) {
                    @Suppress("UNCHECKED_CAST")
                    return ExternalColorSerializer as GhostSerializer<T>
                }
                return null
            }
        }
        Ghost.addRegistry(registry)

        val model = ContextualModel(id = "c1", color = ExternalColor(255, 0, 0))
        
        val json = Ghost.serialize(model)
        
        assertTrue(json.contains("\"color\":\"#ff0000\""))
        
        val roundtrip = Ghost.deserialize<ContextualModel>(json)
        assertEquals(model, roundtrip)
    }

    @Test
    fun testUltimateResilience() {
        // ResilientItem is marked @GhostResilient
        // If an item is TOTALLY malformed (not an object), it should be skipped
        val json = """
        [
            {"id": "v1", "value": 10},
            "TOTALLY_MALFORMED_ITEM",
            {"id": "v3", "value": 30}
        ]
        """.trimIndent()

        val list = Ghost.deserialize<List<ResilientItem>>(json)
        // Should skip the second item
        assertEquals(2, list.size)
        assertEquals("v1", list[0].id)
        assertEquals("v3", list[1].id)
    }
}

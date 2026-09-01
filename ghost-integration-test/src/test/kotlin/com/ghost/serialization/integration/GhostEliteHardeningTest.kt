@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.integration.model.CoercionStressModel
import com.ghost.serialization.integration.model.ContextualModel
import com.ghost.serialization.integration.model.CustomCoderStressModel
import com.ghost.serialization.integration.model.DeepResilientModel
import com.ghost.serialization.integration.model.ExternalColor
import com.ghost.serialization.integration.model.ExternalColorSerializer
import com.ghost.serialization.integration.model.ResilientItem
import com.ghost.serialization.integration.model.SmartDevice
import com.ghost.serialization.integration.model.SmartHome
import com.ghost.serialization.integration.model.StructuralCollisionModel
import com.ghost.serialization.integration.model.WrapSharedPathModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GhostEliteHardeningTest {

    @Test
    fun testFlatteningCollision() {
        // 'name' and child.name both flatten toward 'meta' — Ghost must nest by path, not collide
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

        // Expected: {"metadata":{"info":{"name":"Ghost"},"auth":{"token":"SECRET"}},"system":{"flags":{"active":true}}}
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
        // Second item's value has a type mismatch but the field is resilient
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
        // No "type" key at all — should route to SmartDevice's UnknownDevice fallback
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
        // ResilientItem is @GhostResilient: a non-object array element should be skipped, not thrown
        val json = """
        [
            {"id": "v1", "value": 10},
            "TOTALLY_MALFORMED_ITEM",
            {"id": "v3", "value": 30}
        ]
        """.trimIndent()

        val list = Ghost.deserialize<List<ResilientItem>>(json)
        assertEquals(2, list.size)
        assertEquals("v1", list[0].id)
        assertEquals("v3", list[1].id)
    }
}

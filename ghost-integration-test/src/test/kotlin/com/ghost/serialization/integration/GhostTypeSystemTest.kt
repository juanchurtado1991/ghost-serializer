package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.integration.model.CollectionOfNulls
import com.ghost.serialization.integration.model.EvolutionModel
import com.ghost.serialization.integration.model.NestedGenericModel
import com.ghost.serialization.integration.model.NullablePrimitives
import com.ghost.serialization.integration.model.RecursiveGraphNode
import kotlin.test.Test
import kotlin.test.assertEquals

class GhostTypeSystemTest {

    @Test
    fun testNestedGenericCollections() {
        val data = mapOf(
            "level1" to listOf(
                mapOf("a" to 1, "b" to 2),
                mapOf("c" to 3)
            ),
            "level2" to listOf(
                mapOf("d" to 4)
            )
        )
        val model = NestedGenericModel(data = data)
        val json = Ghost.serialize(model)
        val decoded = Ghost.deserialize<NestedGenericModel>(json)
        assertEquals(expected = model, actual = decoded)
    }

    @Test
    fun testCollectionOfNullsRoundTrip() {
        val model = CollectionOfNulls(items = listOf("a", null, "b", null))
        val json = Ghost.serialize(model)
        val decoded = Ghost.deserialize<CollectionOfNulls>(json)
        assertEquals(expected = model, actual = decoded)
    }

    @Test
    fun testRecursiveGraphDeep() {
        val root = RecursiveGraphNode(
            name = "1",
            next = RecursiveGraphNode(
                name = "2",
                next = RecursiveGraphNode(
                    name = "3",
                    next = RecursiveGraphNode(name = "4")
                )
            )
        )
        val json = Ghost.serialize(root)
        val decoded = Ghost.deserialize<RecursiveGraphNode>(json)
        assertEquals(expected = root, actual = decoded)
    }

    @Test
    fun testEmptyCollections() {
        val model = NestedGenericModel(data = emptyMap())
        val json = Ghost.serialize(model)
        val decoded = Ghost.deserialize<NestedGenericModel>(json)
        assertEquals(expected = model, actual = decoded)
    }

    @Test
    fun testNullablePrimitivesRoundTrip() {
        val model = NullablePrimitives(i = 1, l = 2L, b = true, d = 3.14, s = "hi")
        val json = Ghost.serialize(model)
        val decoded = Ghost.deserialize<NullablePrimitives>(json)
        assertEquals(expected = model, actual = decoded)
    }

    @Test
    fun testAllNullPrimitives() {
        val model = NullablePrimitives(i = null, l = null, b = null, d = null, s = null)
        val json = Ghost.serialize(model)
        val decoded = Ghost.deserialize<NullablePrimitives>(json)
        assertEquals(expected = model, actual = decoded)
    }

    @Test
    fun testSchemaEvolutionMissingOptional() {
        val json = "{\"required\": \"must-have\"}"
        val decoded = Ghost.deserialize<EvolutionModel>(json)
        assertEquals(expected = "must-have", actual = decoded.required)
        assertEquals(expected = "default", actual = decoded.optional)
    }

    @Test
    fun testSchemaEvolutionUnknownFields() {
        val json = "{\"required\": \"val\", \"unknown\": 123, \"nested\": {\"a\": 1}}"
        // Ghost should ignore unknown fields by default (unless strict mode is on)
        val decoded = Ghost.deserialize<EvolutionModel>(json)
        assertEquals(expected = "val", actual = decoded.required)
    }
}

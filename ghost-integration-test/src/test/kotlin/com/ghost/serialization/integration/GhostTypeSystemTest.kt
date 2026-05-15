package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.integration.model.*
import kotlin.test.*

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
        val model = NestedGenericModel(data)
        val json = Ghost.serialize(model)
        val decoded = Ghost.deserialize<NestedGenericModel>(json)
        assertEquals(model, decoded)
    }

    @Test
    fun testCollectionOfNullsRoundTrip() {
        val model = CollectionOfNulls(listOf("a", null, "b", null))
        val json = Ghost.serialize(model)
        val decoded = Ghost.deserialize<CollectionOfNulls>(json)
        assertEquals(model, decoded)
    }

    @Test
    fun testRecursiveGraphDeep() {
        val root = RecursiveGraphNode("1", 
            RecursiveGraphNode("2", 
                RecursiveGraphNode("3", 
                    RecursiveGraphNode("4")
                )
            )
        )
        val json = Ghost.serialize(root)
        val decoded = Ghost.deserialize<RecursiveGraphNode>(json)
        assertEquals(root, decoded)
    }

    @Test
    fun testEmptyCollections() {
        val model = NestedGenericModel(emptyMap())
        val json = Ghost.serialize(model)
        val decoded = Ghost.deserialize<NestedGenericModel>(json)
        assertEquals(model, decoded)
    }
    
    @Test
    fun testNullablePrimitivesRoundTrip() {
        val model = NullablePrimitives(1, 2L, true, 3.14, "hi")
        val json = Ghost.serialize(model)
        val decoded = Ghost.deserialize<NullablePrimitives>(json)
        assertEquals(model, decoded)
    }

    @Test
    fun testAllNullPrimitives() {
        val model = NullablePrimitives(null, null, null, null, null)
        val json = Ghost.serialize(model)
        val decoded = Ghost.deserialize<NullablePrimitives>(json)
        assertEquals(model, decoded)
    }

    @Test
    fun testSchemaEvolutionMissingOptional() {
        val json = "{\"required\": \"must-have\"}"
        val decoded = Ghost.deserialize<EvolutionModel>(json)
        assertEquals("must-have", decoded.required)
        assertEquals("default", decoded.optional)
    }

    @Test
    fun testSchemaEvolutionUnknownFields() {
        val json = "{\"required\": \"val\", \"unknown\": 123, \"nested\": {\"a\": 1}}"
        // Ghost should ignore unknown fields by default (unless strict mode is on)
        val decoded = Ghost.deserialize<EvolutionModel>(json)
        assertEquals("val", decoded.required)
    }
}

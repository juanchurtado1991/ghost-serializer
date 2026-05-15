@file:OptIn(InternalGhostApi::class)
package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.*
import com.ghost.serialization.exception.GhostJsonException
import kotlin.test.*

class GhostFinalHardeningTest {

    @Test
    fun testDuplicateKeysInJson() {
        // According to common JSON practice, the last key should win.
        val json = """{"id": 1, "id": 2, "id_internal": 100, "identity": "ghost"}"""
        val model = Ghost.deserialize<OverlappingKeyModel>(json.encodeToByteArray())
        
        assertEquals(2, model.id, "Last key 'id' should win")
        assertEquals(100, model.id_internal)
    }

    @Test
    fun testMapWithEscapedKeys() {
        val model = MapEdgeCaseModel(
            complexKeys = mapOf("key with \"quotes\"" to "val1", "key\nnewline" to "val2")
        )
        
        val json = Ghost.serialize(model)
        
        // Verify key escaping in maps
        assertTrue(json.contains("\"key with \\\"quotes\\\"\":\"val1\""))
        assertTrue(json.contains("\"key\\nnewline\":\"val2\""))
        
        val decoded = Ghost.deserialize<MapEdgeCaseModel>(json.encodeToByteArray())
        assertEquals(model, decoded)
    }

    @Test
    fun testVeryLargeStringBoundaryFlush() {
        val largeString = buildString {
            append("start-")
            for (i in 1..1000) {
                append("escaped\"quote\"-")
                append("unicode🧛-")
            }
            append("-end")
        }
        
        val model = LargeStringModel(largeString)
        val json = Ghost.serialize(model)
        
        // Ensure it can be deserialized back
        val decoded = Ghost.deserialize<LargeStringModel>(json.encodeToByteArray())
        assertEquals(largeString, decoded.large)
    }

    @Test
    fun testExtremeNumericCoercion() {
        // Int.MAX_VALUE as string
        val maxIntStr = Int.MAX_VALUE.toString()
        val json = """{"id": "$maxIntStr", "name": "Max Int"}"""
        
        val model = Ghost.deserialize<UserWithValueClass>(json.encodeToByteArray()) {
            it.coerceStringsToNumbers = true
        }
        
        assertEquals(UserId(Int.MAX_VALUE), model.id)
    }

    @Test
    fun testCollectionOfNulls() {
        val model = CollectionOfNulls(items = listOf(null, "A", null, "B"))
        val json = Ghost.serialize(model)
        
        assertEquals("{\"items\":[null,\"A\",null,\"B\"]}", json)
        
        val decoded = Ghost.deserialize<CollectionOfNulls>(json.encodeToByteArray())
        assertEquals(model, decoded)
    }

    @Test
    fun testMalformedTrailingComma() {
        // Standard JSON does not allow trailing commas. Ghost should fail.
        val json = """{"id": 1, "name": "Ghost",}"""
        assertFailsWith<GhostJsonException> {
            Ghost.deserialize<NamingModel>(json.encodeToByteArray())
        }
    }

    @Test
    fun testDeepRecursiveChain() {
        // Test a deep chain to ensure no stack overflow for reasonable depths
        var current = RecursiveGraphNode("bottom")
        repeat(50) {
            current = RecursiveGraphNode("node-$it", current)
        }
        
        val json = Ghost.serialize(current)
        assertTrue(json.contains("node-49"))
        assertTrue(json.contains("bottom"))
        
        val decoded = Ghost.deserialize<RecursiveGraphNode>(json.encodeToByteArray())
        assertEquals("node-49", decoded.name)
    }
}

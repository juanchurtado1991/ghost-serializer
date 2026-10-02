@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.integration.model.CollectionOfNulls
import com.ghost.serialization.integration.model.LargeStringModel
import com.ghost.serialization.integration.model.MapEdgeCaseModel
import com.ghost.serialization.integration.model.NamingModel
import com.ghost.serialization.integration.model.OverlappingKeyModel
import com.ghost.serialization.integration.model.RecursiveGraphNode
import com.ghost.serialization.integration.model.UserId
import com.ghost.serialization.integration.model.UserWithValueClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GhostFinalHardeningTest {

    @Test
    fun testDuplicateKeysInJson() {
        val json = """{"id": 1, "id": 2, "id_internal": 100, "identity": "ghost"}"""
        val model = Ghost.deserialize<OverlappingKeyModel>(json.encodeToByteArray())

        assertEquals(expected = 2, actual = model.id, message = "Last key 'id' should win")
        assertEquals(expected = 100, actual = model.id_internal)
    }

    @Test
    fun testMapWithEscapedKeys() {
        val model = MapEdgeCaseModel(
            complexKeys = mapOf("key with \"quotes\"" to "val1", "key\nnewline" to "val2")
        )

        val json = Ghost.serialize(model)

        assertTrue(actual = json.contains("\"key with \\\"quotes\\\"\":\"val1\""))
        assertTrue(actual = json.contains("\"key\\nnewline\":\"val2\""))

        val decoded = Ghost.deserialize<MapEdgeCaseModel>(json.encodeToByteArray())
        assertEquals(expected = model, actual = decoded)
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

        val model = LargeStringModel(large = largeString)
        val json = Ghost.serialize(model)

        val decoded = Ghost.deserialize<LargeStringModel>(json.encodeToByteArray())
        assertEquals(expected = largeString, actual = decoded.large)
    }

    @Test
    fun testExtremeNumericCoercion() {
        val maxIntStr = Int.MAX_VALUE.toString()
        val json = """{"id": "$maxIntStr", "name": "Max Int"}"""

        val model = Ghost.deserialize<UserWithValueClass>(json.encodeToByteArray()) {
            it.coerceStringsToNumbers = true
        }

        assertEquals(expected = UserId(value = Int.MAX_VALUE), actual = model.id)
    }

    @Test
    fun testCollectionOfNulls() {
        val model = CollectionOfNulls(items = listOf(null, "A", null, "B"))
        val json = Ghost.serialize(model)

        assertEquals(expected = "{\"items\":[null,\"A\",null,\"B\"]}", actual = json)

        val decoded = Ghost.deserialize<CollectionOfNulls>(json.encodeToByteArray())
        assertEquals(expected = model, actual = decoded)
    }

    @Test
    fun testMalformedTrailingComma() {
        val json = """{"id": 1, "name": "Ghost",}"""
        assertFailsWith<GhostJsonException> {
            Ghost.deserialize<NamingModel>(json.encodeToByteArray())
        }
    }

    @Test
    fun testDeepRecursiveChain() {
        var current = RecursiveGraphNode(name = "bottom")
        repeat(50) {
            current = RecursiveGraphNode(name = "node-$it", next = current)
        }

        val json = Ghost.serialize(current)
        assertTrue(actual = json.contains("node-49"))
        assertTrue(actual = json.contains("bottom"))

        val decoded = Ghost.deserialize<RecursiveGraphNode>(json.encodeToByteArray())
        assertEquals(expected = "node-49", actual = decoded.name)
    }
}

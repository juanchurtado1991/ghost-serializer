package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.integration.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GhostStressTest {

    @Test
    fun testRecursiveStructure() {
        val node = RecursiveNode(
            id = 1,
            name = "root",
            children = listOf(
                RecursiveNode(id = 2, name = "child1"),
                RecursiveNode(id = 3, name = "child2", children = listOf(
                    RecursiveNode(id = 4, name = "grandchild")
                ))
            )
        )
        
        val json = Ghost.serialize(node)
        val deserialized = Ghost.deserialize<RecursiveNode>(json)
        assertEquals(node, deserialized)
        assertEquals(2, deserialized.children?.size)
        assertEquals("grandchild", deserialized.children?.get(1)?.children?.get(0)?.name)
    }

    @Test
    fun testDeepGenerics() {
        val model = DeepGenericModel(
            data = mapOf(
                "level1" to listOf(
                    mapOf("1" to listOf("a", "b")),
                    mapOf("2" to listOf("c"))
                )
            )
        )
        
        val json = Ghost.serialize(model)
        val deserialized = Ghost.deserialize<DeepGenericModel>(json)
        assertEquals(model, deserialized)
        assertEquals("b", deserialized.data["level1"]?.get(0)?.get("1")?.get(1))
    }

    @Test
    fun testReservedWords() {
        val model = ReservedWordModel(
            `when` = "now",
            `val` = 42,
            `fun` = true,
            reader = "input",
            writer = "output",
            index = 1,
            mask = 0xFFL,
            OPTIONS = "config"
        )
        
        val json = Ghost.serialize(model)
        val deserialized = Ghost.deserialize<ReservedWordModel>(json)
        assertEquals(model, deserialized)
    }

    @Test
    fun testWideModelFragmentation() {
        val model = WideModel(
            f01="v1", f02="v2", f03="v3", f04="v4", f05="v5",
            f06="v6", f07="v7", f08="v8", f09="v9", f10="v10",
            f11="v11", f12="v12", f13="v13", f14="v14", f15="v15",
            f16="v16", f17="v17", f18="v18", f19="v19", f20="v20",
            f21="v21", f22="v22", f23="v23", f24="v24", f25="v25",
            f26="v26", f27="v27", f28="v28", f29="v29", f30="v30",
            f31="v31", f32="v32", f33="v33", f34="v34", f35="v35",
            f36="v36", f37="v37", f38="v38", f39="v39", f40="v40",
            f41="v41", f42="v42", f43="v43", f44="v44", f45="v45",
            f46="v46", f47="v47", f48="v48", f49="v49", f50="v50",
            f51="v51", f52="v52", f53="v53", f54="v54", f55="v55"
        )
        
        val json = Ghost.serialize(model)
        assertTrue(json.contains("\"f01\":\"v1\""))
        assertTrue(json.contains("\"f55\":\"v55\""))
        
        val deserialized = Ghost.deserialize<WideModel>(json)
        assertEquals(model, deserialized)
    }

    @Test
    fun testDepthProtection() {
        // Create a deeply nested object that exceeds default 255 depth
        var current = RecursiveNode(id = 0, name = "leaf")
        for (i in 1..300) {
            current = RecursiveNode(id = i, name = "node_$i", children = listOf(current))
        }
        
        // Should fail due to depth protection (either in serialize or deserialize)
        try {
            val json = Ghost.serialize(current)
            Ghost.deserialize<RecursiveNode>(json)
            assertTrue(false, "Should have thrown GhostJsonException due to depth")
        } catch (e: Exception) {
            val isGhostException = e is com.ghost.serialization.exception.GhostJsonException || 
                                 e.message?.contains("depth") == true
            assertTrue(isGhostException, "Expected depth protection exception but got: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    @Test
    fun testLargeArrayStress() {
        val largeList = List(10000) { RecursiveNode(id = it, name = "name_$it") }
        val json = Ghost.serialize(largeList)
        val deserialized = Ghost.deserialize<List<RecursiveNode>>(json)
        assertEquals(10000, deserialized.size)
        assertEquals("name_9999", deserialized.last().name)
    }
}

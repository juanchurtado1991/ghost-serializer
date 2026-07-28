package com.ghost.serialization.yaml
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.yaml.GhostYamlFlatWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

/**
 * Group C tests: flow-style mappings (`{key: value}`), flow-style sequences (`[a, b, c]`),
 * and deeply nested combinations of flow and block styles.
 */
class GhostYamlGroupCTest {

    @Test
    fun `reads simple flow mapping`() {
        val yaml = "simple_flow_map: {name: Alice, age: 30, active: true}"
        val result = parseMap(yaml)
        @Suppress("UNCHECKED_CAST")
        val map = result["simple_flow_map"] as Map<String, Any?>
        assertEquals("Alice", map["name"])
        assertEquals(30L, map["age"])
        assertEquals(true, map["active"])
    }

    @Test
    fun `reads simple flow sequence`() {
        val yaml = "simple_flow_seq: [one, two, three]"
        val result = parseMap(yaml)
        @Suppress("UNCHECKED_CAST")
        val list = result["simple_flow_seq"] as List<Any?>
        assertEquals(3, list.size)
        assertEquals("one", list[0])
        assertEquals("two", list[1])
        assertEquals("three", list[2])
    }

    @Test
    fun `reads empty flow collections`() {
        val yaml = """
            empty_map: {}
            empty_seq: []
        """.trimIndent()
        val result = parseMap(yaml)
        @Suppress("UNCHECKED_CAST")
        val map = result["empty_map"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val seq = result["empty_seq"] as List<Any?>
        assertTrue(map.isEmpty())
        assertTrue(seq.isEmpty())
    }

    @Test
    fun `reads nested flow mapping`() {
        val yaml = "nested_flow: {user: {name: Bob, role: admin}}"
        val result = parseMap(yaml)
        @Suppress("UNCHECKED_CAST")
        val nested = result["nested_flow"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val user = nested["user"] as Map<String, Any?>
        assertEquals("Bob", user["name"])
        assertEquals("admin", user["role"])
    }

    private fun parseMap(yaml: String): Map<String, Any?> {
        val reader = GhostYamlFlatReader(yaml.encodeToByteArray())
        @Suppress("UNCHECKED_CAST")
        return reader.readDocument() as Map<String, Any?>
    }

}


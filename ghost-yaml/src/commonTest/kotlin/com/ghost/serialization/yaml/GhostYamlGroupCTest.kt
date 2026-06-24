package com.ghost.serialization.yaml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

/**
 * TDD — Group C Tests (Red phase)
 *
 * Tests for: Flow style mappings ({key: value}), flow style sequences ([a, b, c]),
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

    @Test
    fun `parses edge_flow_style yaml dataset completely`() {
        val yaml = loadTestResource("yaml/edge_flow_style.yaml")
        val result = parseMap(yaml)

        @Suppress("UNCHECKED_CAST")
        val simpleMap = result["simple_flow_map"] as Map<String, Any?>
        assertEquals("Alice", simpleMap["name"])
        assertEquals(30L, simpleMap["age"])
        assertEquals(true, simpleMap["active"])

        @Suppress("UNCHECKED_CAST")
        val simpleSeq = result["simple_flow_seq"] as List<Any?>
        assertEquals(5, simpleSeq.size)
        assertEquals("one", simpleSeq[0])
        assertEquals("five", simpleSeq[4])

        @Suppress("UNCHECKED_CAST")
        val intSeq = result["int_sequence"] as List<Any?>
        assertEquals(7, intSeq.size)
        assertEquals(1L, intSeq[0])
        assertEquals(999L, intSeq[6])

        @Suppress("UNCHECKED_CAST")
        val mixedSeq = result["mixed_seq"] as List<Any?>
        assertEquals("hello", mixedSeq[0])
        assertEquals(42L, mixedSeq[1])
        assertEquals(true, mixedSeq[2])
        assertEquals(3.14, mixedSeq[3])
        assertNull(mixedSeq[4])

        @Suppress("UNCHECKED_CAST")
        val nestedFlow = result["nested_flow"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val user = nestedFlow["user"] as Map<String, Any?>
        assertEquals("Bob", user["name"])
        assertEquals("admin", user["role"])

        @Suppress("UNCHECKED_CAST")
        val server = result["server"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val options = server["options"] as Map<String, Any?>
        assertEquals(false, options["ssl"])
        assertEquals(100L, options["maxConnections"])

        @Suppress("UNCHECKED_CAST")
        val allowedMethods = result["allowed_methods"] as List<Any?>
        assertEquals(6, allowedMethods.size)
        assertEquals("GET", allowedMethods[0])
        assertEquals("OPTIONS", allowedMethods[5])

        @Suppress("UNCHECKED_CAST")
        val users = result["users"] as List<Any?>
        assertEquals(3, users.size)
        @Suppress("UNCHECKED_CAST")
        val alice = users[0] as Map<String, Any?>
        assertEquals(1L, alice["id"])
        assertEquals("Alice", alice["name"])

        @Suppress("UNCHECKED_CAST")
        val matrix = result["matrix"] as List<Any?>
        assertEquals(3, matrix.size)
        @Suppress("UNCHECKED_CAST")
        val row0 = matrix[0] as List<Any?>
        assertEquals(1L, row0[0])

        @Suppress("UNCHECKED_CAST")
        val quotedFlow = result["quoted_flow"] as Map<String, Any?>
        assertEquals("Hello, World!", quotedFlow["message"])
        assertEquals("/usr/local/bin", quotedFlow["path"])

        @Suppress("UNCHECKED_CAST")
        val nullableFlow = result["nullable_flow"] as Map<String, Any?>
        assertNull(nullableFlow["email"])
        assertEquals(28L, nullableFlow["age"])

        @Suppress("UNCHECKED_CAST")
        val complex = result["complex"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val metadata = complex["metadata"] as Map<String, Any?>
        assertEquals("2024-01-15", metadata["created"])
        @Suppress("UNCHECKED_CAST")
        val tags = metadata["tags"] as List<Any?>
        assertEquals(3, tags.size)
        assertEquals("yaml", tags[0])
    }

    @Test
    fun `parses openapi_schema yaml dataset completely`() {
        val file = java.io.File("../ghost-benchmark/src/main/resources/yaml/openapi_schema.yaml")
        val yaml = if (file.exists()) {
            file.readText()
        } else {
            java.io.File("ghost-benchmark/src/main/resources/yaml/openapi_schema.yaml").readText()
        }
        val result = parseMap(yaml)
        assertEquals("3.0.3", result["openapi"])

        @Suppress("UNCHECKED_CAST")
        val info = result["info"] as Map<String, Any?>
        assertEquals("Ghost Serializer API", info["title"])

        @Suppress("UNCHECKED_CAST")
        val paths = result["paths"] as Map<String, Any?>
        assertTrue(paths.containsKey("/users"))

        @Suppress("UNCHECKED_CAST")
        val security = result["security"] as List<Any?>
        assertEquals(1, security.size)
        @Suppress("UNCHECKED_CAST")
        val secMap = security[0] as Map<String, Any?>
        assertTrue(secMap.containsKey("bearerAuth"))
    }

    private fun parseMap(yaml: String): Map<String, Any?> {
        val reader = GhostYamlFlatReader(yaml.encodeToByteArray())
        @Suppress("UNCHECKED_CAST")
        return reader.readDocument() as Map<String, Any?>
    }

    private fun loadTestResource(path: String): String {
        return readTestResource(path)
    }
}


package com.ghost.serialization.yaml
import com.ghost.serialization.yaml.parser.GhostYamlFlatReader
import com.ghost.serialization.yaml.writer.GhostYamlFlatWriter
import com.ghost.serialization.yaml.writer.GhostYamlWriter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

/**
 * TDD — Group A Tests (Red phase)
 *
 * Tests for: block mappings, block sequences, all scalar types,
 * quoted strings, nested objects/lists, comments, multiple documents.
 *
 * These tests define the expected behavior of [GhostYamlFlatReader].
 * All tests were written BEFORE the implementation (Red → Green → Refactor).
 */
class GhostYamlGroupATest {

    // ── Scalar: String ────────────────────────────────────────────────────────

    @Test
    fun `reads plain string scalar`() {
        val yaml = "name: Alice"
        val result = parseMap(yaml)
        assertEquals("Alice", result["name"])
    }

    @Test
    fun `reads double-quoted string scalar`() {
        val yaml = """name: "Alice Smith""""
        val result = parseMap(yaml)
        assertEquals("Alice Smith", result["name"])
    }

    @Test
    fun `reads single-quoted string scalar`() {
        val yaml = "name: 'Alice Smith'"
        val result = parseMap(yaml)
        assertEquals("Alice Smith", result["name"])
    }

    @Test
    fun `reads string with special chars in double quotes`() {
        val yaml = """message: "Hello, World! #not-a-comment: still-value""""
        val result = parseMap(yaml)
        assertEquals("Hello, World! #not-a-comment: still-value", result["message"])
    }

    @Test
    fun `reads string with colon inside double quotes`() {
        val yaml = """url: "http://localhost:8080/path""""
        val result = parseMap(yaml)
        assertEquals("http://localhost:8080/path", result["url"])
    }

    @Test
    fun `reads empty string in double quotes`() {
        val yaml = """name: """""
        val result = parseMap(yaml)
        assertEquals("", result["name"])
    }

    @Test
    fun `reads empty string in single quotes`() {
        val yaml = "name: ''"
        val result = parseMap(yaml)
        assertEquals("", result["name"])
    }

    // ── Scalar: Integer ───────────────────────────────────────────────────────

    @Test
    fun `reads positive integer scalar`() {
        val yaml = "count: 42"
        val result = parseMap(yaml)
        assertEquals(42L, result["count"])
    }

    @Test
    fun `reads zero`() {
        val yaml = "value: 0"
        val result = parseMap(yaml)
        assertEquals(0L, result["value"])
    }

    @Test
    fun `reads negative integer scalar`() {
        val yaml = "delta: -17"
        val result = parseMap(yaml)
        assertEquals(-17L, result["delta"])
    }

    @Test
    fun `reads large integer (Long range)`() {
        val yaml = "big: 9223372036854775807"
        val result = parseMap(yaml)
        assertEquals(9223372036854775807L, result["big"])
    }

    // ── Scalar: Double ────────────────────────────────────────────────────────

    @Test
    fun `reads positive double scalar`() {
        val yaml = "ratio: 3.14"
        val result = parseMap(yaml)
        assertEquals(3.14, result["ratio"] as Double, 1e-9)
    }

    @Test
    fun `reads negative double scalar`() {
        val yaml = "temp: -273.15"
        val result = parseMap(yaml)
        assertEquals(-273.15, result["temp"] as Double, 1e-9)
    }

    @Test
    fun `reads scientific notation double`() {
        val yaml = "small: 1.5e-10"
        val result = parseMap(yaml)
        assertEquals(1.5e-10, result["small"] as Double, 1e-20)
    }

    // ── Scalar: Boolean ───────────────────────────────────────────────────────

    @Test
    fun `reads boolean true (lowercase)`() {
        val yaml = "active: true"
        val result = parseMap(yaml)
        assertEquals(true, result["active"])
    }

    @Test
    fun `reads boolean false (lowercase)`() {
        val yaml = "active: false"
        val result = parseMap(yaml)
        assertEquals(false, result["active"])
    }

    @Test
    fun `reads boolean True (capitalized)`() {
        val yaml = "active: True"
        val result = parseMap(yaml)
        assertEquals(true, result["active"])
    }

    @Test
    fun `reads boolean FALSE (uppercase)`() {
        val yaml = "active: FALSE"
        val result = parseMap(yaml)
        assertEquals(false, result["active"])
    }

    // ── Scalar: Null ──────────────────────────────────────────────────────────

    @Test
    fun `reads null (null keyword)`() {
        val yaml = "value: null"
        val result = parseMap(yaml)
        assertNull(result["value"])
    }

    @Test
    fun `reads null (tilde)`() {
        val yaml = "value: ~"
        val result = parseMap(yaml)
        assertNull(result["value"])
    }

    @Test
    fun `reads null (Null capitalized)`() {
        val yaml = "value: Null"
        val result = parseMap(yaml)
        assertNull(result["value"])
    }

    @Test
    fun `reads null (empty value)`() {
        val yaml = "value:"
        val result = parseMap(yaml)
        assertNull(result["value"])
    }

    // ── Block Mapping ─────────────────────────────────────────────────────────

    @Test
    fun `reads multiple keys in block mapping`() {
        val yaml = """
            id: 1
            name: Alice
            active: true
        """.trimIndent()
        val result = parseMap(yaml)
        assertEquals(1L, result["id"])
        assertEquals("Alice", result["name"])
        assertEquals(true, result["active"])
    }

    @Test
    fun `reads nested block mapping`() {
        val yaml = """
            user:
              id: 1
              name: Alice
        """.trimIndent()
        val result = parseMap(yaml)
        @Suppress("UNCHECKED_CAST")
        val user = result["user"] as Map<String, Any?>
        assertEquals(1L, user["id"])
        assertEquals("Alice", user["name"])
    }

    @Test
    fun `reads deeply nested block mapping`() {
        val yaml = """
            a:
              b:
                c:
                  d: deep_value
        """.trimIndent()
        val result = parseMap(yaml)
        @Suppress("UNCHECKED_CAST")
        val a = result["a"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val b = a["b"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val c = b["c"] as Map<String, Any?>
        assertEquals("deep_value", c["d"])
    }

    // ── Block Sequence ────────────────────────────────────────────────────────

    @Test
    fun `reads block sequence of strings`() {
        val yaml = """
            tags:
              - kotlin
              - yaml
              - ghost
        """.trimIndent()
        val result = parseMap(yaml)
        @Suppress("UNCHECKED_CAST")
        val tags = result["tags"] as List<Any?>
        assertEquals(3, tags.size)
        assertEquals("kotlin", tags[0])
        assertEquals("yaml", tags[1])
        assertEquals("ghost", tags[2])
    }

    @Test
    fun `reads block sequence of integers`() {
        val yaml = """
            scores:
              - 10
              - 20
              - 30
        """.trimIndent()
        val result = parseMap(yaml)
        @Suppress("UNCHECKED_CAST")
        val scores = result["scores"] as List<Any?>
        assertEquals(10L, scores[0])
        assertEquals(20L, scores[1])
        assertEquals(30L, scores[2])
    }

    @Test
    fun `reads block sequence of objects`() {
        val yaml = """
            users:
              - id: 1
                name: Alice
              - id: 2
                name: Bob
        """.trimIndent()
        val result = parseMap(yaml)
        @Suppress("UNCHECKED_CAST")
        val users = result["users"] as List<Any?>
        assertEquals(2, users.size)
        @Suppress("UNCHECKED_CAST")
        val alice = users[0] as Map<String, Any?>
        assertEquals(1L, alice["id"])
        assertEquals("Alice", alice["name"])
    }

    @Test
    fun `reads nested sequence`() {
        val yaml = """
            matrix:
              - - 1
                - 2
              - - 3
                - 4
        """.trimIndent()
        val result = parseMap(yaml)
        @Suppress("UNCHECKED_CAST")
        val matrix = result["matrix"] as List<Any?>
        assertEquals(2, matrix.size)
        @Suppress("UNCHECKED_CAST")
        val row0 = matrix[0] as List<Any?>
        assertEquals(1L, row0[0])
        assertEquals(2L, row0[1])
    }

    // ── Comments ──────────────────────────────────────────────────────────────

    @Test
    fun `ignores full-line comments`() {
        val yaml = """
            # This is a comment
            name: Alice
            # Another comment
            age: 30
        """.trimIndent()
        val result = parseMap(yaml)
        assertEquals("Alice", result["name"])
        assertEquals(30L, result["age"])
    }

    @Test
    fun `ignores inline comments`() {
        val yaml = "name: Alice # this is the user name"
        val result = parseMap(yaml)
        assertEquals("Alice", result["name"])
    }

    @Test
    fun `hash in quoted string is not a comment`() {
        val yaml = """name: "Alice #1 Fan""""
        val result = parseMap(yaml)
        assertEquals("Alice #1 Fan", result["name"])
    }

    // ── Multiple Documents ────────────────────────────────────────────────────

    @Test
    fun `reads multiple documents separated by ---`() {
        val yaml = """
            name: Alice
            ---
            name: Bob
        """.trimIndent()
        val docs = parseAllDocuments(yaml)
        assertEquals(2, docs.size)
        assertEquals("Alice", (docs[0] as Map<*, *>)["name"])
        assertEquals("Bob", (docs[1] as Map<*, *>)["name"])
    }

    @Test
    fun `reads document with leading ---`() {
        val yaml = """
            ---
            name: Alice
            age: 30
        """.trimIndent()
        val result = parseMap(yaml)
        assertEquals("Alice", result["name"])
        assertEquals(30L, result["age"])
    }

    // ── Spring Boot benchmark dataset validation ──────────────────────────────

    @Test
    fun `parses spring_boot_app yaml dataset without crash`() {
        val yaml = loadTestResource("yaml/spring_boot_app.yaml")
        val result = parseMap(yaml)
        // Top-level keys must be present
        assertTrue(result.containsKey("spring"))
        assertTrue(result.containsKey("server"))
        assertTrue(result.containsKey("management"))
        assertTrue(result.containsKey("logging"))
        assertTrue(result.containsKey("ghost"))
    }

    // ── Whitespace edge cases ─────────────────────────────────────────────────

    @Test
    fun `handles trailing whitespace on value`() {
        val yaml = "name: Alice   "
        val result = parseMap(yaml)
        assertEquals("Alice", result["name"])
    }

    @Test
    fun `handles empty document`() {
        val yaml = ""
        val result = parseMap(yaml)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `handles document with only comments`() {
        val yaml = """
            # Comment only
            # Another comment
        """.trimIndent()
        val result = parseMap(yaml)
        assertTrue(result.isEmpty())
    }

    // ── Helpers (will be backed by GhostYamlFlatReader) ──────────────────────

    /**
     * Parses a YAML string and returns the top-level mapping as Map<String, Any?>.
     * Backed by [GhostYamlFlatReader] once implemented.
     */
    private fun parseMap(yaml: String): Map<String, Any?> {
        val reader = GhostYamlFlatReader(yaml.encodeToByteArray())
        return reader.readDocument() as Map<String, Any?>
    }

    /**
     * Parses a YAML string containing multiple documents.
     */
    private fun parseAllDocuments(yaml: String): List<Any?> {
        val reader = GhostYamlFlatReader(yaml.encodeToByteArray())
        return reader.readAllDocuments()
    }

    /**
     * Loads a test resource file as a String.
     * On JVM, reads from the classpath. On native/JS, reads from the filesystem.
     */
    private fun loadTestResource(path: String): String {
        // Platform-specific implementation via expect/actual
        return readTestResource(path)
    }
}

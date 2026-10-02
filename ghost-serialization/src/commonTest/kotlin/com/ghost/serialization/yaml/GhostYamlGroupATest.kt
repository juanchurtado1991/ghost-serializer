package com.ghost.serialization.yaml

import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.parser.yaml.readAllDocuments
import com.ghost.serialization.parser.yaml.readDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Group A tests: block mappings, block sequences, all scalar types,
 * quoted strings, nested objects/lists, comments, and multiple documents.
 *
 * Defines expected behavior of [GhostYamlFlatReader].
 */
class GhostYamlGroupATest {

    // ── Scalar: String ────────────────────────────────────────────────────────

    @Test
    fun `reads plain string scalar`() {
        val yaml = "name: Alice"
        val result = parseMap(yaml = yaml)
        assertEquals(
            expected = "Alice",
            actual = result["name"]
        )
    }

    @Test
    fun `reads double-quoted string scalar`() {
        val yaml = """name: "Alice Smith""""
        val result = parseMap(yaml)
        assertEquals(
            expected = "Alice Smith",
            actual = result["name"]
        )
    }

    @Test
    fun `reads single-quoted string scalar`() {
        val yaml = "name: 'Alice Smith'"
        val result = parseMap(yaml)
        assertEquals(
            expected = "Alice Smith",
            actual = result["name"]
        )
    }

    @Test
    fun `reads string with special chars in double quotes`() {
        val yaml = """message: "Hello, World! #not-a-comment: still-value""""
        val result = parseMap(yaml)
        assertEquals(
            expected = "Hello, World! #not-a-comment: still-value",
            actual = result["message"]
        )
    }

    @Test
    fun `reads string with colon inside double quotes`() {
        val yaml = """url: "http://localhost:8080/path""""
        val result = parseMap(yaml)
        assertEquals(
            expected = "http://localhost:8080/path",
            actual = result["url"]
        )
    }

    @Test
    fun `reads empty string in double quotes`() {
        val yaml = """name: """""
        val result = parseMap(yaml)
        assertEquals(
            expected = "",
            actual = result["name"]
        )
    }

    @Test
    fun `reads empty string in single quotes`() {
        val yaml = "name: ''"
        val result = parseMap(yaml)
        assertEquals(
            expected = "",
            actual = result["name"]
        )
    }

    // ── Scalar: Integer ───────────────────────────────────────────────────────

    @Test
    fun `reads positive integer scalar`() {
        val yaml = "count: 42"
        val result = parseMap(yaml)
        assertEquals(
            expected = 42L,
            actual = result["count"]
        )
    }

    @Test
    fun `reads zero`() {
        val yaml = "value: 0"
        val result = parseMap(yaml)
        assertEquals(
            expected = 0L,
            actual = result["value"]
        )
    }

    @Test
    fun `reads negative integer scalar`() {
        val yaml = "delta: -17"
        val result = parseMap(yaml)
        assertEquals(
            expected = -17L,
            actual = result["delta"]
        )
    }

    @Test
    fun `reads large integer Long range`() {
        val yaml = "big: 9223372036854775807"
        val result = parseMap(yaml)
        assertEquals(
            expected = 9223372036854775807L,
            actual = result["big"]
        )
    }

    // ── Scalar: Double ────────────────────────────────────────────────────────

    @Test
    fun `reads positive double scalar`() {
        val yaml = "ratio: 3.14"
        val result = parseMap(yaml)
        assertEquals(
            expected = 3.14,
            actual = result["ratio"] as Double,
            absoluteTolerance = 1e-9
        )
    }

    @Test
    fun `reads negative double scalar`() {
        val yaml = "temp: -273.15"
        val result = parseMap(yaml)
        assertEquals(
            expected = -273.15,
            actual = result["temp"] as Double,
            absoluteTolerance = 1e-9
        )
    }

    @Test
    fun `reads scientific notation double`() {
        val yaml = "small: 1.5e-10"
        val result = parseMap(yaml)
        assertEquals(
            expected = 1.5e-10,
            actual = result["small"] as Double,
            absoluteTolerance = 1e-20
        )
    }

    // ── Scalar: Boolean ───────────────────────────────────────────────────────

    @Test
    fun `reads boolean true lowercase`() {
        val yaml = "active: true"
        val result = parseMap(yaml)
        assertEquals(
            expected = true,
            actual = result["active"]
        )
    }

    @Test
    fun `reads boolean false lowercase`() {
        val yaml = "active: false"
        val result = parseMap(yaml)
        assertEquals(
            expected = false,
            actual = result["active"]
        )
    }

    @Test
    fun `reads boolean True capitalized`() {
        val yaml = "active: True"
        val result = parseMap(yaml)
        assertEquals(
            expected = true,
            actual = result["active"]
        )
    }

    @Test
    fun `reads boolean FALSE uppercase`() {
        val yaml = "active: FALSE"
        val result = parseMap(yaml)
        assertEquals(
            expected = false,
            actual = result["active"]
        )
    }

    // ── Scalar: Null ──────────────────────────────────────────────────────────

    @Test
    fun `reads null null keyword`() {
        val yaml = "value: null"
        val result = parseMap(yaml)
        assertNull(actual = result["value"])
    }

    @Test
    fun `reads null tilde`() {
        val yaml = "value: ~"
        val result = parseMap(yaml)
        assertNull(actual = result["value"])
    }

    @Test
    fun `reads null Null capitalized`() {
        val yaml = "value: Null"
        val result = parseMap(yaml)
        assertNull(actual = result["value"])
    }

    @Test
    fun `reads null empty value`() {
        val yaml = "value:"
        val result = parseMap(yaml)
        assertNull(actual = result["value"])
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
        assertEquals(
            expected = 1L,
            actual = result["id"]
        )
        assertEquals(
            expected = "Alice",
            actual = result["name"]
        )
        assertEquals(
            expected = true,
            actual = result["active"]
        )
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
        assertEquals(
            expected = 1L,
            actual = user["id"]
        )
        assertEquals(
            expected = "Alice",
            actual = user["name"]
        )
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
        assertEquals(
            expected = "deep_value",
            actual = c["d"]
        )
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
        assertEquals(
            expected = 3,
            actual = tags.size
        )
        assertEquals(
            expected = "kotlin",
            actual = tags[0]
        )
        assertEquals(
            expected = "yaml",
            actual = tags[1]
        )
        assertEquals(
            expected = "ghost",
            actual = tags[2]
        )
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
        assertEquals(
            expected = 10L,
            actual = scores[0]
        )
        assertEquals(
            expected = 20L,
            actual = scores[1]
        )
        assertEquals(
            expected = 30L,
            actual = scores[2]
        )
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
        assertEquals(
            expected = 2,
            actual = users.size
        )
        @Suppress("UNCHECKED_CAST")
        val alice = users[0] as Map<String, Any?>
        assertEquals(
            expected = 1L,
            actual = alice["id"]
        )
        assertEquals(
            expected = "Alice",
            actual = alice["name"]
        )
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
        assertEquals(
            expected = 2,
            actual = matrix.size
        )
        @Suppress("UNCHECKED_CAST")
        val row0 = matrix[0] as List<Any?>
        assertEquals(
            expected = 1L,
            actual = row0[0]
        )
        assertEquals(
            expected = 2L,
            actual = row0[1]
        )
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
        assertEquals(
            expected = "Alice",
            actual = result["name"]
        )
        assertEquals(
            expected = 30L,
            actual = result["age"]
        )
    }

    @Test
    fun `ignores inline comments`() {
        val yaml = "name: Alice # this is the user name"
        val result = parseMap(yaml)
        assertEquals(
            expected = "Alice",
            actual = result["name"]
        )
    }

    @Test
    fun `hash in quoted string is not a comment`() {
        val yaml = """name: "Alice #1 Fan""""
        val result = parseMap(yaml)
        assertEquals(
            expected = "Alice #1 Fan",
            actual = result["name"]
        )
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
        assertEquals(
            expected = 2,
            actual = docs.size
        )
        assertEquals(
            expected = "Alice",
            actual = (docs[0] as Map<*, *>)["name"]
        )
        assertEquals(
            expected = "Bob",
            actual = (docs[1] as Map<*, *>)["name"]
        )
    }

    @Test
    fun `reads document with leading ---`() {
        val yaml = """
            ---
            name: Alice
            age: 30
        """.trimIndent()
        val result = parseMap(yaml)
        assertEquals(
            expected = "Alice",
            actual = result["name"]
        )
        assertEquals(
            expected = 30L,
            actual = result["age"]
        )
    }

    // ── Whitespace edge cases ─────────────────────────────────────────────────

    @Test
    fun `handles trailing whitespace on value`() {
        val yaml = "name: Alice   "
        val result = parseMap(yaml)
        assertEquals(
            expected = "Alice",
            actual = result["name"]
        )
    }

    @Test
    fun `handles empty document`() {
        val yaml = ""
        val result = parseMap(yaml)
        assertTrue(actual = result.isEmpty())
    }

    @Test
    fun `handles document with only comments`() {
        val yaml = """
            # Comment only
            # Another comment
        """.trimIndent()
        val result = parseMap(yaml)
        assertTrue(actual = result.isEmpty())
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun parseMap(yaml: String): Map<String, Any?> {
        val reader = GhostYamlFlatReader(yaml.encodeToByteArray())
        return reader.readDocument() as Map<String, Any?>
    }

    private fun parseAllDocuments(yaml: String): List<Any?> {
        val reader = GhostYamlFlatReader(yaml.encodeToByteArray())
        return reader.readAllDocuments()
    }
}

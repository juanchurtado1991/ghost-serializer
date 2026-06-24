package com.ghost.serialization.yaml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

/**
 * TDD — Group D Tests (Red phase)
 *
 * Tests for: Explicit tags (!!str, !!int, !!float, !!bool, !!null, !!seq, !!map),
 * and dates/timestamps implicit checking.
 */
class GhostYamlGroupDTest {

    @Test
    fun `reads explicit tags on scalars`() {
        val yaml = """
            port_as_string: !!str 8080
            version_as_string: !!str 1.2.3
            boolean_as_string: !!str true
            hex_as_string: !!str 0xFF
            explicit_int: !!int 42
            hex_int: !!int 0xFF
            octal_int: !!int 0o17
            binary_int: !!int 0b1010
            explicit_float: !!float 3.14
            explicit_true: !!bool true
            explicit_null: !!null null
        """.trimIndent()
        val result = parseMap(yaml)
        assertEquals("8080", result["port_as_string"])
        assertEquals("1.2.3", result["version_as_string"])
        assertEquals("true", result["boolean_as_string"])
        assertEquals("0xFF", result["hex_as_string"])
        assertEquals(42L, result["explicit_int"])
        // Hex/octal/binary parsed to Long if we support them
        assertEquals(255L, result["hex_int"])
        assertEquals(15L, result["octal_int"])
        assertEquals(10L, result["binary_int"])
        assertEquals(3.14, result["explicit_float"])
        assertEquals(true, result["explicit_true"])
        assertNull(result["explicit_null"])
    }

    @Test
    fun `reads explicit collection tags`() {
        val yaml = """
            explicit_seq: !!seq
              - item1
              - item2
            explicit_map: !!map
              key1: value1
        """.trimIndent()
        val result = parseMap(yaml)
        @Suppress("UNCHECKED_CAST")
        val seq = result["explicit_seq"] as List<Any?>
        assertEquals(2, seq.size)
        assertEquals("item1", seq[0])

        @Suppress("UNCHECKED_CAST")
        val map = result["explicit_map"] as Map<String, Any?>
        assertEquals("value1", map["key1"])
    }

    @Test
    fun `parses edge_explicit_tags yaml dataset completely`() {
        val yaml = loadTestResource("yaml/edge_explicit_tags.yaml")
        val result = parseMap(yaml)

        assertEquals("8080", result["port_as_string"])
        assertEquals("1.2.3", result["version_as_string"])
        assertEquals("true", result["boolean_as_string"])
        assertEquals("null", result["null_as_string"]) // !!str null is actually parsed as null or string "null"? Wait, in YAML 1.2, tag !!str forces scalar to be string. So it should be string "null".
        assertEquals("0xFF", result["hex_as_string"])

        assertEquals(42L, result["explicit_int"])
        assertEquals(255L, result["hex_int"])
        assertEquals(15L, result["octal_int"])
        assertEquals(10L, result["binary_int"])

        assertEquals(3.14, result["explicit_float"])
        assertEquals(1.5e10, result["sci_float"])
        assertEquals(Double.POSITIVE_INFINITY, result["infinity_pos"])
        assertEquals(Double.NEGATIVE_INFINITY, result["infinity_neg"])
        assertTrue((result["not_a_number"] as Double).isNaN())

        assertEquals(true, result["explicit_true"])
        assertEquals(false, result["explicit_false"])

        assertNull(result["explicit_null"])
        assertNull(result["explicit_null2"])

        assertEquals("2024-01-15", result["date_simple"])
        assertEquals("2024-01-15T10:30:00Z", result["date_with_time"])

        @Suppress("UNCHECKED_CAST")
        val seq = result["explicit_seq"] as List<Any?>
        assertEquals("item1", seq[0])

        @Suppress("UNCHECKED_CAST")
        val map = result["explicit_map"] as Map<String, Any?>
        assertEquals("value1", map["key1"])
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

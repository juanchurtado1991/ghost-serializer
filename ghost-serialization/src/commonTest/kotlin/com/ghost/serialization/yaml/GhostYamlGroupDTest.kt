package com.ghost.serialization.yaml
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.yaml.GhostYamlFlatWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

/**
 * Group D tests: explicit tags (`!!str`, `!!int`, `!!float`, `!!bool`, `!!null`, `!!seq`, `!!map`),
 * and date/timestamp implicit checking.
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

    private fun parseMap(yaml: String): Map<String, Any?> {
        val reader = GhostYamlFlatReader(yaml.encodeToByteArray())
        @Suppress("UNCHECKED_CAST")
        return reader.readDocument() as Map<String, Any?>
    }

}

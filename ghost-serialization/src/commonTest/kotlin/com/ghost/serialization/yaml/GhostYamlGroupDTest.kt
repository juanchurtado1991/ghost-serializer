package com.ghost.serialization.yaml

import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun `reads negative hex octal and binary scalars`() {
        // readNumber()'s digit scanner used to stop right after the leading "-0", leaving
        // "x10"/"o17"/"b1010" unconsumed and corrupting the next key. Regression test.
        val yaml = """
            hex_negative: -0x10
            octal_negative: -0o17
            binary_negative: -0b1010
            next: 5
        """.trimIndent()
        val result = parseMap(yaml)
        assertEquals(-16L, result["hex_negative"])
        assertEquals(-15L, result["octal_negative"])
        assertEquals(-10L, result["binary_negative"])
        assertEquals(5L, result["next"])
    }

    @Test
    fun `reads negative hex octal and binary in flow style and as array items`() {
        // Same readNumber() code path as the block-mapping case above, but exercised through
        // flow collections and block-sequence items to make sure the fix isn't accidentally
        // scoped to just one caller.
        val flow = parseMap("v: {a: -0x10, b: -0o17, c: -0b1010}")

        @Suppress("UNCHECKED_CAST")
        val flowMap = flow["v"] as Map<String, Any?>
        assertEquals(-16L, flowMap["a"])
        assertEquals(-15L, flowMap["b"])
        assertEquals(-10L, flowMap["c"])

        val flowSeq = parseMap("v: [-0x10, -0o17, -0b1010, 5]")
        assertEquals(listOf(-16L, -15L, -10L, 5L), flowSeq["v"])

        val blockSeq = parseMap(
            """
            v:
              - -0x10
              - -0o17
              - -0b1010
              - 5
            """.trimIndent()
        )
        assertEquals(listOf(-16L, -15L, -10L, 5L), blockSeq["v"])
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

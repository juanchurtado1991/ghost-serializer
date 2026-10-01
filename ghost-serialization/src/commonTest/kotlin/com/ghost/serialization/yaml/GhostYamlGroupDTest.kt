package com.ghost.serialization.yaml

import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.parser.yaml.readDocument
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
        val result = parseMap(yaml = yaml)
        assertEquals(
            expected = "8080",
            actual = result["port_as_string"]
        )
        assertEquals(
            expected = "1.2.3",
            actual = result["version_as_string"]
        )
        assertEquals(
            expected = "true",
            actual = result["boolean_as_string"]
        )
        assertEquals(
            expected = "0xFF",
            actual = result["hex_as_string"]
        )
        assertEquals(
            expected = 42L,
            actual = result["explicit_int"]
        )
        assertEquals(
            expected = 255L,
            actual = result["hex_int"]
        )
        assertEquals(
            expected = 15L,
            actual = result["octal_int"]
        )
        assertEquals(
            expected = 10L,
            actual = result["binary_int"]
        )
        assertEquals(
            expected = 3.14,
            actual = result["explicit_float"]
        )
        assertEquals(
            expected = true,
            actual = result["explicit_true"]
        )
        assertNull(actual = result["explicit_null"])
    }

    @Test
    fun `reads negative hex octal and binary scalars`() {
        // Regression: readNumber() used to stop after leading "-0", leaving "x10"/"o17"/"b1010"
        // unconsumed and corrupting the next key.
        val yaml = """
            hex_negative: -0x10
            octal_negative: -0o17
            binary_negative: -0b1010
            next: 5
        """.trimIndent()
        val result = parseMap(yaml = yaml)
        assertEquals(
            expected = -16L,
            actual = result["hex_negative"]
        )
        assertEquals(
            expected = -15L,
            actual = result["octal_negative"]
        )
        assertEquals(
            expected = -10L,
            actual = result["binary_negative"]
        )
        assertEquals(
            expected = 5L,
            actual = result["next"]
        )
    }

    @Test
    fun `reads negative hex octal and binary in flow style and as array items`() {
        // Same readNumber() path as above, exercised via flow/sequence to confirm the fix isn't
        // scoped to just the block-mapping caller.
        val flow = parseMap(yaml = "v: {a: -0x10, b: -0o17, c: -0b1010}")

        @Suppress("UNCHECKED_CAST")
        val flowMap = flow["v"] as Map<String, Any?>
        assertEquals(
            expected = -16L,
            actual = flowMap["a"]
        )
        assertEquals(
            expected = -15L,
            actual = flowMap["b"]
        )
        assertEquals(
            expected = -10L,
            actual = flowMap["c"]
        )

        val flowSeq = parseMap(yaml = "v: [-0x10, -0o17, -0b1010, 5]")
        assertEquals(
            expected = listOf(-16L, -15L, -10L, 5L),
            actual = flowSeq["v"]
        )

        val blockSeq = parseMap(
            yaml = """
            v:
              - -0x10
              - -0o17
              - -0b1010
              - 5
            """.trimIndent()
        )
        assertEquals(
            expected = listOf(-16L, -15L, -10L, 5L),
            actual = blockSeq["v"]
        )
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
        val result = parseMap(yaml = yaml)

        @Suppress("UNCHECKED_CAST")
        val seq = result["explicit_seq"] as List<Any?>
        assertEquals(
            expected = 2,
            actual = seq.size
        )
        assertEquals(
            expected = "item1",
            actual = seq[0]
        )

        @Suppress("UNCHECKED_CAST")
        val map = result["explicit_map"] as Map<String, Any?>
        assertEquals(
            expected = "value1",
            actual = map["key1"]
        )
    }

    private fun parseMap(yaml: String): Map<String, Any?> {
        val reader = GhostYamlFlatReader(rawData = yaml.encodeToByteArray())
        @Suppress("UNCHECKED_CAST")
        return reader.readDocument() as Map<String, Any?>
    }

}

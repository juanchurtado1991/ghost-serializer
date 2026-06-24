package com.ghost.serialization.yaml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TDD — Group B Tests (Red phase)
 *
 * Tests for: multiline block scalars (literal '|' and folded '>'),
 * chomp style indicators ('-', '+'), and explicit indentation indicators.
 */
class GhostYamlGroupBTest {

    @Test
    fun `reads literal block scalar clip`() {
        val yaml = """
            literal_block: |
              Line one
              Line two
              Line three
        """.trimIndent()
        val result = parseMap(yaml)
        assertEquals("Line one\nLine two\nLine three\n", result["literal_block"])
    }

    @Test
    fun `reads literal block scalar strip`() {
        val yaml = """
            literal_strip: |-
              Line one
              Line two
              Line three
        """.trimIndent()
        val result = parseMap(yaml)
        assertEquals("Line one\nLine two\nLine three", result["literal_strip"])
    }

    @Test
    fun `reads literal block scalar keep`() {
        val yaml = """
            literal_keep: |+
              Line one
              Line two
              Line three


        """.trimIndent()
        val result = parseMap(yaml)
        assertEquals("Line one\nLine two\nLine three\n\n", result["literal_keep"])
    }

    @Test
    fun `reads folded block scalar clip`() {
        val yaml = """
            folded_block: >
              This is the first
              paragraph which gets
              folded into one line.

              This is a second
              paragraph after
              a blank line.
        """.trimIndent()
        val result = parseMap(yaml)
        assertEquals(
            "This is the first paragraph which gets folded into one line.\nThis is a second paragraph after a blank line.\n",
            result["folded_block"]
        )
    }

    @Test
    fun `reads folded block scalar strip`() {
        val yaml = """
            folded_strip: >-
              This line
              is folded
              and trailing newlines stripped
        """.trimIndent()
        val result = parseMap(yaml)
        assertEquals("This line is folded and trailing newlines stripped", result["folded_strip"])
    }

    @Test
    fun `reads folded block scalar keep`() {
        val yaml = """
            folded_keep: >+
              This line
              is folded
              with trailing newlines kept


        """.trimIndent()
        val result = parseMap(yaml)
        assertEquals("This line is folded with trailing newlines kept\n\n", result["folded_keep"])
    }

    @Test
    fun `reads literal block scalar with explicit indent 2`() {
        val yaml = """
            indented_2: |2
              This block starts at column 2
              and preserves relative indentation
                inner indent here
        """.trimIndent()
        val result = parseMap(yaml)
        assertEquals(
            "This block starts at column 2\nand preserves relative indentation\n  inner indent here\n",
            result["indented_2"]
        )
    }

    @Test
    fun `parses edge_multiline yaml dataset completely`() {
        val yaml = loadTestResource("yaml/edge_multiline.yaml")
        val result = parseMap(yaml)
        assertEquals("Line one\nLine two\nLine three\n", result["literal_block"])
        assertEquals("Line one\nLine two\nLine three", result["literal_strip"])
        assertEquals("Line one\nLine two\nLine three\n\n\n", result["literal_keep"])
        assertEquals(
            "This is the first paragraph which gets folded into one line.\nThis is a second paragraph after a blank line.\n",
            result["folded_block"]
        )
        assertEquals("This line is folded and trailing newlines stripped", result["folded_strip"])
        assertEquals("This line is folded with trailing newlines kept\n\n\n", result["folded_keep"])
        assertEquals(
            "This block starts at column 2\nand preserves relative indentation\n  inner indent here\n",
            result["indented_2"]
        )
        assertEquals(
            "This block starts at column 4\nand preserves relative indentation\n",
            result["indented_4"]
        )
        assertEquals("", result["empty_literal"])
        assertEquals("only one line\n", result["single_newline"])
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

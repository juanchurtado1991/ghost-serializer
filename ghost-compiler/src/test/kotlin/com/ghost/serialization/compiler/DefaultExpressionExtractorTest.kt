package com.ghost.serialization.compiler

import com.ghost.serialization.compiler.analysis.DefaultExpressionExtractor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultExpressionExtractorTest {

    @Test
    fun whitelistAcceptsLiteralsAndEmptyCollections() {
        assertEquals("null", DefaultExpressionExtractor.whitelist("null"))
        assertEquals("true", DefaultExpressionExtractor.whitelist("true"))
        assertEquals("false", DefaultExpressionExtractor.whitelist("false"))
        assertEquals("0", DefaultExpressionExtractor.whitelist("0"))
        assertEquals("1", DefaultExpressionExtractor.whitelist("1"))
        assertEquals("42L", DefaultExpressionExtractor.whitelist("42L"))
        assertEquals("3.14", DefaultExpressionExtractor.whitelist("3.14"))
        assertEquals("1.0f", DefaultExpressionExtractor.whitelist("1.0f"))
        assertEquals("'x'", DefaultExpressionExtractor.whitelist("'x'"))
        assertEquals("'\\n'", DefaultExpressionExtractor.whitelist("'\\n'"))
        assertEquals("\"viewer\"", DefaultExpressionExtractor.whitelist("\"viewer\""))
        assertEquals("\"a,b\"", DefaultExpressionExtractor.whitelist("\"a,b\""))
        assertEquals("emptyList()", DefaultExpressionExtractor.whitelist("emptyList()"))
        assertEquals("emptyMap()", DefaultExpressionExtractor.whitelist("emptyMap()"))
        assertEquals("listOf()", DefaultExpressionExtractor.whitelist("listOf()"))
        assertEquals("Priority.LOW", DefaultExpressionExtractor.whitelist("Priority.LOW"))
        assertEquals("FOO_BAR", DefaultExpressionExtractor.whitelist("FOO_BAR"))
    }

    @Test
    fun whitelistRejectsUnsafeExpressions() {
        assertNull(DefaultExpressionExtractor.whitelist("a + 1"))
        assertNull(DefaultExpressionExtractor.whitelist("listOf(1)"))
        assertNull(DefaultExpressionExtractor.whitelist("foo()"))
        assertNull(DefaultExpressionExtractor.whitelist("\"hello \$name\""))
        assertNull(DefaultExpressionExtractor.whitelist("\"\"\"multi\"\"\""))
        assertNull(DefaultExpressionExtractor.whitelist("MyVC(0)"))
        assertNull(DefaultExpressionExtractor.whitelist("lowercase"))
        assertNull(DefaultExpressionExtractor.whitelist(""))
    }

    @Test
    fun extractRawHandlesCommaInsideString() {
        val source = """
            data class Sample(
                val label: String = "a,b,c",
                val count: Int = 1,
            )
        """.trimIndent()
        assertEquals("\"a,b,c\"", DefaultExpressionExtractor.extractRawDefault(source, "label", 2))
        assertEquals("1", DefaultExpressionExtractor.extractRawDefault(source, "count", 3))
    }

    @Test
    fun extractRawHandlesNestedGenericsAndEmptyMap() {
        val source = """
            data class Sample(
                val meta: Map<String, Int> = emptyMap(),
            )
        """.trimIndent()
        assertEquals("emptyMap()", DefaultExpressionExtractor.extractRawDefault(source, "meta", 2))
    }

    @Test
    fun extractRawHandlesNestedParensInTypeAndTrailingComma() {
        val source = """
            data class Sample(
                val tags: List<List<String>> = emptyList(),
            )
        """.trimIndent()
        assertEquals("emptyList()", DefaultExpressionExtractor.extractRawDefault(source, "tags", 2))
    }

    @Test
    fun extractRawHandlesInlineCommentAndAnnotation() {
        val source = """
            data class Sample(
                @Suppress("unused") val x: Int = 1, // trailing
                val y: Boolean = false,
            )
        """.trimIndent()
        assertEquals("1", DefaultExpressionExtractor.extractRawDefault(source, "x", 2))
        assertEquals("false", DefaultExpressionExtractor.extractRawDefault(source, "y", 3))
    }

    @Test
    fun extractRawHandlesMultilineDefault() {
        val source = """
            data class Sample(
                val role: String =
                    "viewer",
                val n: Int = 2,
            )
        """.trimIndent()
        assertEquals("\"viewer\"", DefaultExpressionExtractor.extractRawDefault(source, "role", 2))
    }

    @Test
    fun extractRawHandlesDependentDefaultForFallbackDetection() {
        val source = """
            data class Sample(
                val a: Int = 1,
                val b: Int = a + 1,
            )
        """.trimIndent()
        val raw = DefaultExpressionExtractor.extractRawDefault(source, "b", 3)
        assertEquals("a + 1", raw)
        assertNull(DefaultExpressionExtractor.whitelist(raw!!))
    }

    @Test
    fun whitelistThenExtractPipelineForObject40Style() {
        val props = (1..5).joinToString(",\n") { "    val p$it: Int = $it" }
        val source = "data class ObjectN(\n$props\n)"
        for (i in 1..5) {
            val raw = DefaultExpressionExtractor.extractRawDefault(source, "p$i", i + 1)
            assertEquals("$i", raw)
            assertEquals("$i", DefaultExpressionExtractor.whitelist(raw!!))
        }
    }

    @Test
    fun extractRawIgnoresNameInsideStringLiteral() {
        val source = """
            data class Sample(
                val note: String = "val count: Int = 9",
                val count: Int = 3,
            )
        """.trimIndent()
        assertEquals("3", DefaultExpressionExtractor.extractRawDefault(source, "count", 3))
    }
}

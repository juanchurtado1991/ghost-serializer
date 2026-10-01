package com.ghost.serialization.compiler

import com.ghost.serialization.compiler.analysis.DefaultExpressionExtractor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DefaultExpressionExtractorTest {

    @Test
    fun whitelistAcceptsLiteralsAndEmptyCollections() {
        assertEquals(expected = "null", actual = DefaultExpressionExtractor.whitelist(expr = "null"))
        assertEquals(expected = "true", actual = DefaultExpressionExtractor.whitelist(expr = "true"))
        assertEquals(expected = "false", actual = DefaultExpressionExtractor.whitelist(expr = "false"))
        assertEquals(expected = "0", actual = DefaultExpressionExtractor.whitelist(expr = "0"))
        assertEquals(expected = "1", actual = DefaultExpressionExtractor.whitelist(expr = "1"))
        assertEquals(expected = "42L", actual = DefaultExpressionExtractor.whitelist(expr = "42L"))
        assertEquals(expected = "3.14", actual = DefaultExpressionExtractor.whitelist(expr = "3.14"))
        assertEquals(expected = "1.0f", actual = DefaultExpressionExtractor.whitelist(expr = "1.0f"))
        assertEquals(expected = "'x'", actual = DefaultExpressionExtractor.whitelist(expr = "'x'"))
        assertEquals(expected = "'\\n'", actual = DefaultExpressionExtractor.whitelist(expr = "'\\n'"))
        assertEquals(expected = "\"viewer\"", actual = DefaultExpressionExtractor.whitelist(expr = "\"viewer\""))
        assertEquals(expected = "\"a,b\"", actual = DefaultExpressionExtractor.whitelist(expr = "\"a,b\""))
        assertEquals(expected = "emptyList()", actual = DefaultExpressionExtractor.whitelist(expr = "emptyList()"))
        assertEquals(expected = "emptyMap()", actual = DefaultExpressionExtractor.whitelist(expr = "emptyMap()"))
        assertEquals(expected = "listOf()", actual = DefaultExpressionExtractor.whitelist(expr = "listOf()"))
        assertEquals(expected = "Priority.LOW", actual = DefaultExpressionExtractor.whitelist(expr = "Priority.LOW"))
        assertEquals(expected = "FOO_BAR", actual = DefaultExpressionExtractor.whitelist(expr = "FOO_BAR"))
    }

    @Test
    fun whitelistRejectsUnsafeExpressions() {
        assertNull(actual = DefaultExpressionExtractor.whitelist(expr = "a + 1"))
        assertNull(actual = DefaultExpressionExtractor.whitelist(expr = "listOf(1)"))
        assertNull(actual = DefaultExpressionExtractor.whitelist(expr = "foo()"))
        assertNull(actual = DefaultExpressionExtractor.whitelist(expr = "\"hello \$name\""))
        assertNull(actual = DefaultExpressionExtractor.whitelist(expr = "\"\"\"multi\"\"\""))
        assertNull(actual = DefaultExpressionExtractor.whitelist(expr = "MyVC(0)"))
        assertNull(actual = DefaultExpressionExtractor.whitelist(expr = "lowercase"))
        assertNull(actual = DefaultExpressionExtractor.whitelist(expr = ""))
    }

    @Test
    fun extractRawHandlesCommaInsideString() {
        val source = """
            data class Sample(
                val label: String = "a,b,c",
                val count: Int = 1,
            )
        """.trimIndent()
        assertEquals(
            expected = "\"a,b,c\"",
            actual = DefaultExpressionExtractor.extractRawDefault(source = source, paramName = "label", lineNumber = 2)
        )
        assertEquals(
            expected = "1",
            actual = DefaultExpressionExtractor.extractRawDefault(source = source, paramName = "count", lineNumber = 3)
        )
    }

    @Test
    fun extractRawHandlesNestedGenericsAndEmptyMap() {
        val source = """
            data class Sample(
                val meta: Map<String, Int> = emptyMap(),
            )
        """.trimIndent()
        assertEquals(
            expected = "emptyMap()",
            actual = DefaultExpressionExtractor.extractRawDefault(source = source, paramName = "meta", lineNumber = 2)
        )
    }

    @Test
    fun extractRawHandlesNestedParensInTypeAndTrailingComma() {
        val source = """
            data class Sample(
                val tags: List<List<String>> = emptyList(),
            )
        """.trimIndent()
        assertEquals(
            expected = "emptyList()",
            actual = DefaultExpressionExtractor.extractRawDefault(source = source, paramName = "tags", lineNumber = 2)
        )
    }

    @Test
    fun extractRawHandlesInlineCommentAndAnnotation() {
        val source = """
            data class Sample(
                @Suppress("unused") val x: Int = 1, // trailing
                val y: Boolean = false,
            )
        """.trimIndent()
        assertEquals(
            expected = "1",
            actual = DefaultExpressionExtractor.extractRawDefault(source = source, paramName = "x", lineNumber = 2)
        )
        assertEquals(
            expected = "false",
            actual = DefaultExpressionExtractor.extractRawDefault(source = source, paramName = "y", lineNumber = 3)
        )
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
        assertEquals(
            expected = "\"viewer\"",
            actual = DefaultExpressionExtractor.extractRawDefault(source = source, paramName = "role", lineNumber = 2)
        )
    }

    @Test
    fun extractRawHandlesDependentDefaultForFallbackDetection() {
        val source = """
            data class Sample(
                val a: Int = 1,
                val b: Int = a + 1,
            )
        """.trimIndent()
        val raw = DefaultExpressionExtractor.extractRawDefault(source = source, paramName = "b", lineNumber = 3)
        assertEquals(expected = "a + 1", actual = raw)
        assertNull(actual = DefaultExpressionExtractor.whitelist(expr = raw!!))
    }

    @Test
    fun whitelistThenExtractPipelineForObject40Style() {
        val props = (1..5).joinToString(",\n") { "    val p$it: Int = $it" }
        val source = "data class ObjectN(\n$props\n)"
        for (i in 1..5) {
            val raw = DefaultExpressionExtractor.extractRawDefault(
                source = source,
                paramName = "p$i",
                lineNumber = i + 1
            )
            assertEquals(expected = "$i", actual = raw)
            assertEquals(expected = "$i", actual = DefaultExpressionExtractor.whitelist(expr = raw!!))
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
        assertEquals(
            expected = "3",
            actual = DefaultExpressionExtractor.extractRawDefault(source = source, paramName = "count", lineNumber = 3)
        )
    }
}

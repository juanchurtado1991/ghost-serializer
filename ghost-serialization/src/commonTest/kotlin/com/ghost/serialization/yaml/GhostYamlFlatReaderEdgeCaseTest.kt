@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.json.JsonReaderOptions
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.parser.yaml.readDocument
import com.ghost.serialization.parser.yaml.reset
import com.ghost.serialization.yaml.exception.GhostYamlException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Malformed YAML, depth limits, duplicate keys, and strict traversal edge cases.
 * YAML counterpart to `GhostCrashProofTest` and
 * `GhostFlatReaderEdgeCaseTest`.
 */
class GhostYamlFlatReaderEdgeCaseTest {

    private fun readerOf(yaml: String): GhostYamlFlatReader {
        return GhostYamlFlatReader(rawData = yaml.encodeToByteArray())
    }

    private fun parseMap(yaml: String): Map<String, Any?> {
        return readerOf(yaml).readDocument() as Map<String, Any?>
    }

    // ── A. NUMERIC EDGE CASES ────────────────────────────────────────

    @Test
    fun readsExactLongMaxValue() {
        val reader = readerOf("v: ${Long.MAX_VALUE}")
        reader.beginObject()
        reader.selectNameAndConsume(options = JsonReaderOptions.of("v"))
        assertEquals(
            expected = Long.MAX_VALUE,
            actual = reader.nextLong()
        )
    }

    @Test
    fun readsExactLongMinValue() {
        val reader = readerOf("v: ${Long.MIN_VALUE}")
        reader.beginObject()
        reader.selectNameAndConsume(options = JsonReaderOptions.of("v"))
        assertEquals(
            expected = Long.MIN_VALUE,
            actual = reader.nextLong()
        )
    }

    @Test
    fun veryLargeNumberClampsToLongMaxValue() {
        val reader = readerOf("v: 99999999999999999999")
        reader.beginObject()
        reader.selectNameAndConsume(options = JsonReaderOptions.of("v"))
        assertEquals(
            expected = Long.MAX_VALUE,
            actual = reader.nextLong()
        )
    }

    @Test
    fun readsScientificNotationDouble() {
        val reader = readerOf("v: 1e10")
        reader.beginObject()
        reader.selectNameAndConsume(options = JsonReaderOptions.of("v"))
        assertEquals(
            expected = 1e10,
            actual = reader.nextDouble(),
            absoluteTolerance = 0.1
        )
    }

    // ── B. MALFORMED YAML ────────────────────────────────────────────

    @Test
    fun unclosedDoubleQuoteThrows() {
        assertFailsWith<GhostYamlException> {
            parseMap(yaml = """name: "unclosed""")
        }
    }

    @Test
    fun unclosedSingleQuoteThrows() {
        assertFailsWith<GhostYamlException> {
            parseMap(yaml = "name: 'unclosed")
        }
    }

    @Test
    fun missingColonAfterKeyIsPlainScalarAtRoot() {
        val doc = readerOf("name Alice").readDocument()
        assertEquals(
            expected = "name Alice",
            actual = doc
        )
    }

    @Test
    fun invalidUnicodeEscapeInDoubleQuotedStringThrows() {
        assertFailsWith<GhostYamlException> {
            parseMap("""v: "\u00GG"""")
        }
    }

    @Test
    fun truncatedUnicodeEscapeThrows() {
        assertFailsWith<GhostYamlException> {
            parseMap("""v: "\u00"""")
        }
    }

    @Test
    fun truncatedDocumentWithOpenMappingThrowsOnTraversal() {
        val reader = readerOf("user:\n  name: Bob")
        assertFailsWith<GhostYamlException> {
            reader.beginObject()
            while (reader.hasNext()) {
                reader.nextKey()
                reader.beginObject()
            }
        }
    }

    @Test
    fun malformedFlowMappingMissingClosingBraceThrows() {
        assertFailsWith<GhostYamlException> {
            parseMap("{a: 1, b: 2")
        }
    }

    @Test
    fun malformedFlowSequenceMissingClosingBracketThrows() {
        assertFailsWith<GhostYamlException> {
            parseMap("[1, 2, 3")
        }
    }

    // ── C. EMPTY / WHITESPACE DOCUMENTS ──────────────────────────────

    @Test
    fun emptyDocumentReturnsEmptyMap() {
        assertTrue(actual = parseMap("").isEmpty())
    }

    @Test
    fun whitespaceOnlyDocumentReturnsEmptyMap() {
        assertTrue(actual = parseMap("   \n  \t  \n").isEmpty())
    }

    @Test
    fun commentsOnlyDocumentReturnsEmptyMap() {
        assertTrue(actual = parseMap("# just a comment\n# another").isEmpty())
    }

    // ── D. DUPLICATE KEYS ──────────────────────────────────────────────

    @Test
    fun duplicateKeysLastValueWins() {
        val result = parseMap(
            """
            name: first
            name: second
            """.trimIndent()
        )
        assertEquals(
            expected = "second",
            actual = result["name"]
        )
    }

    // ── E. DEPTH PROTECTION ──────────────────────────────────────────

    @Test
    fun readerRespectsMaxDepthOnFlowNesting() {
        val nested = "{".repeat(70) + "\"v\":1" + "}".repeat(70)
        assertFailsWith<Throwable> {
            GhostYamlFlatReader(nested.encodeToByteArray()).readDocument()
        }
    }

    @Test
    fun beginObjectOnScalarThrows() {
        val reader = readerOf("v: hello")
        reader.beginObject()
        reader.selectNameAndConsume(JsonReaderOptions.of("v"))
        assertFailsWith<GhostYamlException> { reader.beginObject() }
    }

    @Test
    fun nextIntOnStringThrows() {
        val reader = readerOf("v: not-a-number")
        reader.beginObject()
        reader.selectNameAndConsume(JsonReaderOptions.of("v"))
        assertFailsWith<Throwable> { reader.nextInt() }
    }

    @Test
    fun nextBooleanOnNonBooleanStringReturnsFalse() {
        val reader = readerOf("v: maybe")
        reader.beginObject()
        reader.selectNameAndConsume(JsonReaderOptions.of("v"))
        assertEquals(
            expected = false,
            actual = reader.nextBoolean()
        )
    }

    // ── F. NULL DETECTION ─────────────────────────────────────────────

    @Test
    fun readsExplicitNullScalar() {
        val result = parseMap("v: null")
        assertNull(actual = result["v"])
    }

    @Test
    fun readsEmptyValueAsNull() {
        val result = parseMap("v:")
        assertNull(actual = result["v"])
    }

    @Test
    fun isNextNullValueDetectsExplicitNull() {
        val reader = readerOf("v: null")
        reader.beginObject()
        reader.selectNameAndConsume(JsonReaderOptions.of("v"))
        assertTrue(actual = reader.isNextNullValue())
    }

    // ── G. BAD INDENTATION ────────────────────────────────────────────

    @Test
    fun inconsistentIndentEndsNestedBlockEarly() {
        val yaml = """
            user:
              name: Alice
             rogue: bad
        """.trimIndent()
        val user = parseMap(yaml)["user"] as Map<*, *>
        assertEquals(
            expected = "Alice",
            actual = user["name"]
        )
        assertNull(actual = user["rogue"])
    }

    // ── H. SLICE / LIMIT ──────────────────────────────────────────────

    @Test
    fun limitSliceIgnoresTrailingBytes() {
        val full = "a: 1\nb: 2".encodeToByteArray()
        val reader = GhostYamlFlatReader(full)
        reader.limit = "a: 1".encodeToByteArray().size
        val map = reader.readDocument() as Map<*, *>
        assertEquals(
            expected = 1L,
            actual = map["a"]
        )
        assertNull(actual = map["b"])
    }

    @Test
    fun resetClearsTraversalState() {
        val reader = readerOf("a: 1")
        reader.beginObject()
        reader.selectNameAndConsume(JsonReaderOptions.of("a"))
        reader.nextInt()
        reader.endObject()

        reader.reset("b: 2".encodeToByteArray())
        reader.beginObject()
        reader.selectNameAndConsume(JsonReaderOptions.of("b"))
        assertEquals(
            expected = 2,
            actual = reader.nextInt()
        )
    }
}

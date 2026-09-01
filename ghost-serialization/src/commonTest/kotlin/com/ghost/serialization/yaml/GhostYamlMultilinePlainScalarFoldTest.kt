package com.ghost.serialization.yaml

import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.yaml.exception.GhostYamlException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * A plain scalar value starting on its own line failed to fold continuation lines sitting at the
 * *same* indent as its first line. `resolveValueAfterColon`'s newline branch used the value's own
 * auto-detected column as the fold boundary instead of the enclosing mapping's indent — a
 * continuation line only needs to be indented more than the enclosing mapping. Covers
 * yaml-test-suite cases `4CQQ`/`NB6Z`/`RZT7`/`UGM3`/`HU3P`.
 */
class GhostYamlMultilinePlainScalarFoldTest {

    private fun readerOf(yaml: String) = GhostYamlFlatReader(yaml.encodeToByteArray())

    @Test
    fun foldsSameIndentContinuationLines() {
        val doc = readerOf("plain:\n  This unquoted scalar\n  spans many lines.\n").readDocument()
        assertEquals(mapOf("plain" to "This unquoted scalar spans many lines."), doc)
    }

    @Test
    fun matchesInlineStartEquivalent() {
        val fresh = readerOf("plain:\n  This unquoted scalar\n  spans many lines.\n").readDocument()
        val inline = readerOf("plain: This unquoted scalar\n  spans many lines.\n").readDocument()
        assertEquals(inline, fresh)
    }

    @Test
    fun foldsAcrossBlankLineWithNewlineSeparator() {
        val doc = readerOf("key:\n  line one\n\n  line two\n").readDocument()
        assertEquals(mapOf("key" to "line one\nline two"), doc)
    }

    @Test
    fun stillDedentsToASiblingKey() {
        val doc = readerOf("plain:\n  value line\nsibling: next\n").readDocument()
        assertEquals(mapOf("plain" to "value line", "sibling" to "next"), doc)
    }

    @Test
    fun continuationLineLookingLikeAKeyStillRejected() {
        // yaml-test-suite HU3P — a continuation line can't itself look like "key: value".
        assertFailsWith<GhostYamlException> {
            readerOf("key:\n  word1 word2\n  no: key\n").readDocument()
        }
    }

    @Test
    fun nestedMappingValueStillUsesItsOwnColumnAsBlockIndent() {
        // Must not regress: a *nested block mapping* (not a plain scalar) as a fresh-line value
        // still needs its own auto-detected column as ITS blockIndent, for its own sibling-dedent
        // detection -- this is deliberately a different "indent" than the fold-boundary fix above.
        val doc = readerOf("outer:\n  a: 1\n  b: 2\nsibling: 3\n").readDocument()
        assertEquals(mapOf("outer" to mapOf("a" to 1L, "b" to 2L), "sibling" to 3L), doc)
    }
}

package com.ghost.serialization.yaml

import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.yaml.exception.GhostYamlException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * `key: value` only redirects into a nested block mapping when `value` starts a fresh,
 * more-indented line — never inline on the same line as an enclosing key's own `:`.
 * `readPlainScalarOrMapping`'s colon-redirect used to fire unconditionally, silently parsing
 * `a: b: c: d` as nested maps instead of rejecting it. Covers yaml-test-suite case `ZCZ6`.
 */
class GhostYamlChainedColonTest {

    private fun readerOf(yaml: String) = GhostYamlFlatReader(yaml.encodeToByteArray())

    @Test
    fun rejectsChainedColonOnSingleLine() {
        assertFailsWith<GhostYamlException> {
            readerOf("a: b: c: d").readDocument()
        }
    }

    @Test
    fun rejectsChainedColonWithTwoLevels() {
        assertFailsWith<GhostYamlException> {
            readerOf("a: b: c").readDocument()
        }
    }

    @Test
    fun allowsFreshLineNestedMapping() {
        val doc = readerOf("a:\n  b:\n    c: d").readDocument()
        assertEquals(mapOf("a" to mapOf("b" to mapOf("c" to "d"))), doc)
    }

    @Test
    fun allowsPlainScalarValueContainingColonNotFollowedBySpace() {
        // "12:30" has no "colon + space/EOL" byte pattern anywhere, so it's never ambiguous with
        // a mapping key — must keep working unchanged.
        val doc = readerOf("a: 12:30").readDocument()
        assertEquals(mapOf("a" to "12:30"), doc)
    }
}

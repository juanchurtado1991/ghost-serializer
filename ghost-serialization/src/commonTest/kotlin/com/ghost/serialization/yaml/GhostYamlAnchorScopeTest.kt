package com.ghost.serialization.yaml

import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An anchor/alias at the start of a block-context line is ambiguous on sight: it may anchor a
 * *value*, or it may anchor (or resolve to) the *key* of an implicit mapping entry. Before this
 * fix, `readValue`'s `&`/`*` dispatch always assumed the former — when the anchored/aliased
 * content actually redirected into a nested block mapping, that mapping greedily consumed every
 * sibling entry at its indent before ever returning, so the anchor ended up bound to (or the
 * result silently merged with) the whole resulting map instead of just the bare key. yaml-test-
 * suite cases `E76Z`, `HMQ5`, `26DV` (see `YamlTestSuiteDeviations.kt`'s git history for the
 * removed entries).
 */
class GhostYamlAnchorScopeTest {

    private fun readerOf(yaml: String) = GhostYamlFlatReader(yaml.encodeToByteArray())

    @Test
    fun anchorOnKeyDoesNotSwallowFollowingAliasEntry() {
        // yaml-test-suite E76Z
        val doc = readerOf("&a a: &b b\n*b : *a").readDocument()
        assertEquals(mapOf("a" to "b", "b" to "a"), doc)
    }

    @Test
    fun taggedAnchoredKeyResolvesCorrectly() {
        // yaml-test-suite HMQ5
        val doc = readerOf("!!str &a1 \"foo\":\n  !!str bar\n&a2 baz : *a1").readDocument()
        assertEquals(mapOf("foo" to "bar", "baz" to "foo"), doc)
    }

    @Test
    fun aliasResolvingToAKeyStartsANestedMapping() {
        // yaml-test-suite 26DV (trimmed to the alias-as-key shape specifically) — the anchor must
        // be defined before its alias is used, so "alias1" comes first.
        val doc = readerOf("alias1: &alias1 scalar1\ntop3: &node3\n  *alias1 : scalar3").readDocument()
        assertEquals(mapOf("alias1" to "scalar1", "top3" to mapOf("scalar1" to "scalar3")), doc)
    }

    @Test
    fun ordinaryAnchoredValueStillWorks() {
        val doc = readerOf("key: &a value\nother: *a").readDocument()
        assertEquals(mapOf("key" to "value", "other" to "value"), doc)
    }

    @Test
    fun bareAnchoredSequenceItemStillWorks() {
        val doc = readerOf("- &a value\n- *a").readDocument() as List<*>
        assertEquals(listOf("value", "value"), doc)
    }
}

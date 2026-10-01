package com.ghost.serialization.yaml

import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.parser.yaml.readDocument
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An anchor/alias at the start of a block-context line is ambiguous: it may anchor a *value*, or
 * resolve to the *key* of an implicit mapping entry. `readValue`'s `&`/`*` dispatch used to always
 * assume the former, so a nested-mapping redirect would greedily swallow sibling entries instead of
 * binding just the key. Covers yaml-test-suite `E76Z`, `HMQ5`, `26DV`.
 */
class GhostYamlAnchorScopeTest {

    private fun readerOf(yaml: String) = GhostYamlFlatReader(rawData = yaml.encodeToByteArray())

    @Test
    fun anchorOnKeyDoesNotSwallowFollowingAliasEntry() {
        // yaml-test-suite E76Z
        val doc = readerOf("&a a: &b b\n*b : *a").readDocument()
        assertEquals(
            expected = mapOf("a" to "b", "b" to "a"),
            actual = doc
        )
    }

    @Test
    fun taggedAnchoredKeyResolvesCorrectly() {
        // yaml-test-suite HMQ5
        val doc = readerOf("!!str &a1 \"foo\":\n  !!str bar\n&a2 baz : *a1").readDocument()
        assertEquals(
            expected = mapOf("foo" to "bar", "baz" to "foo"),
            actual = doc
        )
    }

    @Test
    fun aliasResolvingToAKeyStartsANestedMapping() {
        // yaml-test-suite 26DV (alias-as-key shape only) — anchor must be defined before its
        // alias is used, hence "alias1" comes first.
        val doc = readerOf("alias1: &alias1 scalar1\ntop3: &node3\n  *alias1 : scalar3").readDocument()
        assertEquals(
            expected = mapOf("alias1" to "scalar1", "top3" to mapOf("scalar1" to "scalar3")),
            actual = doc
        )
    }

    @Test
    fun ordinaryAnchoredValueStillWorks() {
        val doc = readerOf("key: &a value\nother: *a").readDocument()
        assertEquals(
            expected = mapOf("key" to "value", "other" to "value"),
            actual = doc
        )
    }

    @Test
    fun bareAnchoredSequenceItemStillWorks() {
        val doc = readerOf("- &a value\n- *a").readDocument() as List<*>
        assertEquals(
            expected = listOf("value", "value"),
            actual = doc
        )
    }
}

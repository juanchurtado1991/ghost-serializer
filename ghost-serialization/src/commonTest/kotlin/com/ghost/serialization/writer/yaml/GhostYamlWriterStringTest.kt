@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.writer.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.parser.yaml.readDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [GhostYamlWriter] edge cases: string escaping and key quoting. */
class GhostYamlWriterStringTest {

    // ── STRING ESCAPING ───────────────────────────────────────────

    @Test
    fun writesEmptyString() {
        val yaml = yamlWriterToString { w -> w.beginObject().name(key = "v").value("").endObject() }
        assertTrue(
            actual = yaml.contains("\"\""),
            message = yaml
        )
    }

    @Test
    fun escapesQuotesInString() {
        val yaml =
            yamlWriterToString { w -> w.beginObject().name(key = "v").value("say \"hello\"").endObject() }
        assertTrue(
            actual = yaml.contains("\\\"hello\\\""),
            message = yaml
        )
    }

    @Test
    fun escapesBackslash() {
        val yaml = yamlWriterToString { w -> w.beginObject().name(key = "v").value("path\\to").endObject() }
        assertTrue(
            actual = yaml.contains("\\\\"),
            message = yaml
        )
    }

    @Test
    fun escapesControlCharacters() {
        val yaml = yamlWriterToString { w -> w.beginObject().name(key = "v").value("a\nb\tc\rd").endObject() }
        assertTrue(
            actual = yaml.contains("\\n") && yaml.contains("\\t") && yaml.contains("\\r"),
            message = yaml
        )
    }

    @Test
    fun escapesBackspaceAndFormFeed() {
        val yaml = yamlWriterToString { w -> w.beginObject().name(key = "v").value("\b\u000C").endObject() }
        assertTrue(
            actual = yaml.contains("\\b") && yaml.contains("\\f"),
            message = yaml
        )
    }

    @Test
    fun writesUnicodeDirectly() {
        val yaml = yamlWriterToString { w -> w.beginObject().name(key = "v").value("漢字").endObject() }
        assertTrue(
            actual = yaml.contains("漢字"),
            message = yaml
        )
    }

    @Test
    fun writesAsciiPrefixThenUnicodeWithoutRescanLoss() {
        val yaml = yamlWriterToString { w -> w.beginObject().name(key = "v").value("hello漢字").endObject() }
        assertTrue(
            actual = yaml.contains("hello漢字"),
            message = yaml
        )
    }

    @Test
    fun writesAsciiPrefixThenEscapedQuote() {
        val yaml = yamlWriterToString { w -> w.beginObject().name(key = "v").value("hi\"漢字").endObject() }
        assertTrue(
            actual = yaml.contains("hi\\\"漢字"),
            message = yaml
        )
    }

    @Test
    fun writesEmojiSurrogatePairDirectly() {
        val yaml = yamlWriterToString { w -> w.beginObject().name(key = "v").value("🚀🔥").endObject() }
        assertTrue(
            actual = yaml.contains("🚀") && yaml.contains("🔥"),
            message = yaml
        )
    }

    @Test
    fun writesLongPlainAsciiStringPastScratchCapacity() {
        val longStr = "a".repeat(600)
        val yaml = yamlWriterToString { w -> w.beginObject().name(key = "v").value(longStr).endObject() }
        assertTrue(
            actual = yaml.contains(longStr),
            message = yaml
        )
    }

    @Test
    fun writesLongStringNeedingEscapesPastScratchCapacity() {
        val longStr = "a".repeat(600) + "\"quoted\""
        val yaml = yamlWriterToString { w -> w.beginObject().name(key = "v").value(longStr).endObject() }
        assertTrue(
            actual = yaml.contains("\\\"quoted\\\""),
            message = yaml
        )
    }

    @Test
    fun writesShortStringNeedingEscapeWithinScratchCapacity() {
        val yaml = yamlWriterToString { w -> w.beginObject().name(key = "v").value("a\"b").endObject() }
        assertTrue(
            actual = yaml.contains("a\\\"b"),
            message = yaml
        )
    }

    // ── KEY QUOTING (name() must quote/escape keys that aren't safe bare) ──

    private fun readsKeyBackVerbatim(key: String, value: String = "v"): Boolean {
        val yaml = yamlWriterToString { w -> w.beginObject(); w.name(key = key); w.value(value); w.endObject() }
        val decoded = GhostYamlFlatReader(rawData = yaml.encodeToByteArray()).readDocument() as Map<*, *>
        return decoded.size == 1 && decoded[key] == value
    }

    @Test
    fun ordinaryKeyStaysUnquoted() {
        val yaml = yamlWriterToString { w -> w.beginObject(); w.name(key = "userId"); w.value(1); w.endObject() }
        assertEquals(
            expected = "userId: 1",
            actual = yaml.trim()
        )
    }

    @Test
    fun emptyKeyStaysUnquotedAndRoundTrips() {
        val yaml = yamlWriterToString { w -> w.beginObject(); w.name(key = ""); w.value("x"); w.endObject() }
        assertEquals(
            expected = ": \"x\"",
            actual = yaml.trim()
        )
        assertTrue(actual = readsKeyBackVerbatim(key = ""))
    }

    @Test
    fun keyWithColonSpaceRoundTrips() {
        assertTrue(actual = readsKeyBackVerbatim(key = "foo: bar"))
    }

    @Test
    fun keyWithEmbeddedNewlineRoundTrips() {
        assertTrue(actual = readsKeyBackVerbatim(key = "foo\nbar"))
    }

    @Test
    fun keyStartingWithAnchorSigilRoundTrips() {
        assertTrue(actual = readsKeyBackVerbatim(key = "&notAnAnchor"))
    }

    @Test
    fun keyStartingWithAliasSigilRoundTrips() {
        assertTrue(actual = readsKeyBackVerbatim(key = "*notAnAlias"))
    }

    @Test
    fun keyStartingWithTagSigilRoundTrips() {
        assertTrue(actual = readsKeyBackVerbatim(key = "!notATag"))
    }

    @Test
    fun bareQuestionMarkKeyRoundTrips() {
        assertTrue(actual = readsKeyBackVerbatim(key = "?"))
    }

    @Test
    fun keyStartingWithBracketRoundTrips() {
        assertTrue(actual = readsKeyBackVerbatim(key = "[a, b]"))
    }

    @Test
    fun keyStartingWithBraceRoundTrips() {
        assertTrue(actual = readsKeyBackVerbatim(key = "{a=b}"))
    }

    @Test
    fun keyWithLeadingSpaceRoundTrips() {
        // Found by GhostYamlWriterFuzzTest: " ?xup" wrote bare as " ?xup: 1" and re-read as "?xup"
        // — a plain scalar's leading whitespace is not part of its content, so it was silently
        // dropped.
        assertTrue(actual = readsKeyBackVerbatim(key = " ?xup"))
    }

    @Test
    fun keyWithTrailingSpaceRoundTrips() {
        assertTrue(actual = readsKeyBackVerbatim(key = "foo "))
    }

    @Test
    fun keyStartingWithPercentRoundTrips() {
        // Found by GhostYamlStreamingWriterFuzzTest: "%foo" wrote bare as the document's first
        // line ("%foo: 1") and was read back as an invalid %YAML/%TAG directive instead of a
        // mapping key, throwing instead of round-tripping.
        assertTrue(actual = readsKeyBackVerbatim(key = "%foo"))
    }

    @Test
    fun keyWithLeadingTabRoundTrips() {
        assertTrue(actual = readsKeyBackVerbatim(key = "\tfoo"))
    }

    @Test
    fun keyWithTrailingTabRoundTrips() {
        assertTrue(actual = readsKeyBackVerbatim(key = "foo\t"))
    }
}

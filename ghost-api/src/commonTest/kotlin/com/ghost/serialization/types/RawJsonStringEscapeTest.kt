package com.ghost.serialization.types

import kotlin.test.Test
import kotlin.test.assertEquals

class RawJsonStringEscapeTest {

    private fun raw(json: String): RawJson = RawJson.fromString(json)

    @Test
    fun asStringOrNull_asciiFastPathWithNoEscapes() {
        assertEquals("hello", raw("\"hello\"").asStringOrNull())
        assertEquals("", raw("\"\"").asStringOrNull())
    }

    @Test
    fun asStringOrNull_decodesSimpleEscapes() {
        assertEquals("a\"b", raw("\"a\\\"b\"").asStringOrNull())
        assertEquals("a\\b", raw("\"a\\\\b\"").asStringOrNull())
        assertEquals("a\nb", raw("\"a\\nb\"").asStringOrNull())
        assertEquals("a\tb", raw("\"a\\tb\"").asStringOrNull())
        assertEquals("a\rb", raw("\"a\\rb\"").asStringOrNull())
        assertEquals("a\bb", raw("\"a\\bb\"").asStringOrNull())
        assertEquals("ab", raw("\"a\\fb\"").asStringOrNull())
    }

    @Test
    fun asStringOrNull_decodesUnicodeEscape() {
        assertEquals("A", raw("\"\\u0041\"").asStringOrNull())
    }

    @Test
    fun asStringOrNull_decodesSurrogatePairEscapeAsSingleCodePoint() {
        // U+1F600 GRINNING FACE, encoded as a UTF-16 surrogate pair in the JSON escape form.
        assertEquals("😀", raw("\"\\uD83D\\uDE00\"").asStringOrNull())
    }

    @Test
    fun asStringOrNull_invalidHexDigitsYieldReplacementChar() {
        assertEquals("�", raw("\"\\uZZZZ\"").asStringOrNull())
    }

    @Test
    fun asStringOrNull_truncatedUnicodeEscapeAtBufferBoundaryStopsGracefully() {
        // "\uD8" has only 2 of the 4 required hex digits before the closing quote.
        assertEquals("", raw("\"\\uD8\"").asStringOrNull())
    }

    @Test
    fun asStringOrNull_unrecognizedEscapeKeepsCharLiterally() {
        // Not a JSON-standard escape; the scanner is lenient and drops the backslash.
        assertEquals("x", raw("\"\\x\"").asStringOrNull())
    }

    @Test
    fun asStringOrNull_nullForNonStringPayloads() {
        assertEquals(null, raw("true").asStringOrNull())
        assertEquals(null, raw("42").asStringOrNull())
    }
}

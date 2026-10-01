package com.ghost.serialization.types

import kotlin.test.Test
import kotlin.test.assertEquals

class RawJsonStringEscapeTest {

    private fun raw(
        json: String
    ): RawJson = RawJson.fromString(json = json)

    @Test
    fun asStringOrNull_asciiFastPathWithNoEscapes() {
        assertEquals(
            expected = "hello",
            actual = raw(json = "\"hello\"").asStringOrNull()
        )
        assertEquals(
            expected = "",
            actual = raw(json = "\"\"").asStringOrNull()
        )
    }

    @Test
    fun asStringOrNull_decodesSimpleEscapes() {
        assertEquals(
            expected = "a\"b",
            actual = raw(json = "\"a\\\"b\"").asStringOrNull()
        )
        assertEquals(
            expected = "a\\b",
            actual = raw(json = "\"a\\\\b\"").asStringOrNull()
        )
        assertEquals(
            expected = "a\nb",
            actual = raw(json = "\"a\\nb\"").asStringOrNull()
        )
        assertEquals(
            expected = "a\tb",
            actual = raw(json = "\"a\\tb\"").asStringOrNull()
        )
        assertEquals(
            expected = "a\rb",
            actual = raw(json = "\"a\\rb\"").asStringOrNull()
        )
        assertEquals(
            expected = "a\bb",
            actual = raw(json = "\"a\\bb\"").asStringOrNull()
        )
        assertEquals(
            expected = "ab",
            actual = raw(json = "\"a\\fb\"").asStringOrNull()
        )
    }

    @Test
    fun asStringOrNull_decodesUnicodeEscape() {
        assertEquals(
            expected = "A",
            actual = raw(json = "\"\\u0041\"").asStringOrNull()
        )
    }

    @Test
    fun asStringOrNull_decodesSurrogatePairEscapeAsSingleCodePoint() {
        // U+1F600, encoded as a UTF-16 surrogate pair in the JSON escape form.
        assertEquals(
            expected = "😀",
            actual = raw(json = "\"\\uD83D\\uDE00\"").asStringOrNull()
        )
    }

    @Test
    fun asStringOrNull_invalidHexDigitsYieldReplacementChar() {
        assertEquals(
            expected = "�",
            actual = raw(json = "\"\\uZZZZ\"").asStringOrNull()
        )
    }

    @Test
    fun asStringOrNull_truncatedUnicodeEscapeAtBufferBoundaryStopsGracefully() {
        // Only 2 of 4 required hex digits before the closing quote.
        assertEquals(
            expected = "",
            actual = raw(json = "\"\\uD8\"").asStringOrNull()
        )
    }

    @Test
    fun asStringOrNull_unrecognizedEscapeKeepsCharLiterally() {
        // Not a JSON-standard escape; the scanner is lenient and drops the backslash.
        assertEquals(
            expected = "x",
            actual = raw(json = "\"\\x\"").asStringOrNull()
        )
    }

    @Test
    fun asStringOrNull_nullForNonStringPayloads() {
        assertEquals(
            expected = null,
            actual = raw(json = "true").asStringOrNull()
        )
        assertEquals(
            expected = null,
            actual = raw(json = "42").asStringOrNull()
        )
    }
}

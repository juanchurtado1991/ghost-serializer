@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.strings.beginObject
import com.ghost.serialization.parser.strings.consumeKeySeparator
import com.ghost.serialization.parser.strings.nextKey
import com.ghost.serialization.parser.strings.nextString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** [GhostJsonStringReader] contract: string escape and unicode decoding. */
@OptIn(InternalGhostApi::class)
class GhostStringReaderEscapeTest {

    // ══════════════════════════════════════════════════════════════════
    // Unicode escape sequences
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun readsValidUnicodeEscapeAscii() {
        val reader = stringReaderOf(json = """{"v":"\u0041"}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = "A",
            actual = reader.nextString()
        )
    }

    @Test
    fun readsValidUnicodeEscapeLatinExtended() {
        // U+00E9 = é
        val reader = stringReaderOf(json = "{\"v\":\"\\u00E9\"}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = "\u00E9",
            actual = reader.nextString()
        )
    }

    @Test
    fun readsValidUnicodeEscapeCJK() {
        // U+6F22 = 漢
        val reader = stringReaderOf(json = "{\"v\":\"\\u6F22\"}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = "\u6F22",
            actual = reader.nextString()
        )
    }

    @Test
    fun readsSurrogatePairEmoji() {
        // U+1F680 = 🚀 encoded as surrogate pair \uD83D\uDE80
        val json = "{\"v\":\"\\uD83D\\uDE80\"}"
        val reader = stringReaderOf(json = json)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        val result = reader.nextString()
        assertEquals(
            expected = "\uD83D\uDE80",
            actual = result
        )
    }

    @Test
    fun readsDirectEmojiWithoutSurrogatePairEscape() {
        val reader = stringReaderOf(json = "{\"v\":\"\uD83D\uDD25\uD83D\uDC80\uD83C\uDF89\"}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = "\uD83D\uDD25\uD83D\uDC80\uD83C\uDF89",
            actual = reader.nextString()
        )
    }

    @Test
    fun invalidUnicodeEscapeThrowsException() {
        val json = "{\"v\":\"\\u00GG\"}"
        val reader = stringReaderOf(json = json)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<Exception> { reader.nextString() }
    }

    @Test
    fun truncatedUnicodeEscapeThrowsException() {
        val json = "{\"v\":\"\\u00\"}"
        val reader = stringReaderOf(json = json)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<Exception> { reader.nextString() }
    }

    @Test
    fun unicodeEscapeWithNonLatin1DigitThrowsCleanlyInsteadOfCrashing() {
        // Found by GhostJsonStringChannelFuzzTest: a Char's .code can exceed HEX_LUT's 256
        // entries (unlike a byte, which is 0..255), so a CJK char after `\u` used to crash
        // with ArrayIndexOutOfBoundsException instead of throwing cleanly.
        val reader = stringReaderOf(json = "{\"v\":\"\\u00\u6f22G\"}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> { reader.nextString() }
    }

    @Test
    fun orphanedHighSurrogateThrowsException() {
        // \uD83D is a high surrogate with no following low surrogate
        val reader = stringReaderOf(json = "{\"v\":\"abc\\uD83D\"}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> { reader.nextString() }
    }

    @Test
    fun readsMultiByteUnicodeDirectly() {
        val reader = stringReaderOf(json = """{"v":"漢字テスト"}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = "漢字テスト",
            actual = reader.nextString()
        )
    }

    @Test
    fun readsSupplementaryPlane4ByteChar() {
        // U+1F600 GRINNING FACE — 4-byte UTF-8, 2-char Kotlin String (surrogate pair)
        val reader = stringReaderOf(json = """{"v":"😀"}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = "😀",
            actual = reader.nextString()
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // All RFC JSON escape sequences
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun readsNewlineAndTabEscapes() {
        val reader = stringReaderOf(json = "{\"v\":\"line1\\nline2\\ttab\"}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = "line1\nline2\ttab",
            actual = reader.nextString()
        )
    }

    @Test
    fun readsBackslashAndQuoteEscapes() {
        val reader = stringReaderOf(json = "{\"v\":\"back\\\\slash and \\\"quotes\\\"\"}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = "back\\slash and \"quotes\"",
            actual = reader.nextString()
        )
    }

    @Test
    fun readsAllRfcEscapes() {
        val reader = stringReaderOf(json = "{\"v\":\"\\b\\f\\n\\r\\t\"}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = "\b\u000C\n\r\t",
            actual = reader.nextString()
        )
    }

    @Test
    fun readsStringOfOnlyBackslashes() {
        val json = "{\"v\":\"\\\\\\\\\"}"
        val reader = stringReaderOf(json = json)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = "\\\\",
            actual = reader.nextString()
        )
    }

    @Test
    fun readsStringWithConsecutiveJsonEscapes() {
        val bs = '\\'
        val q = '"'
        val json = "${q}v${q}:${q}${bs}n${bs}t${bs}r${bs}b${q}"
        val reader = stringReaderOf(json = "{$json}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = "\n\t\r\b",
            actual = reader.nextString()
        )
    }
}

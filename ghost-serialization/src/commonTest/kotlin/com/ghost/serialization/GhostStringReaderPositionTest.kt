@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.parser.common.byteToCharPosition
import com.ghost.serialization.parser.common.charToBytePosition
import kotlin.test.Test
import kotlin.test.assertEquals

/** [GhostJsonStringReader] contract: char/byte position conversion. */
@OptIn(InternalGhostApi::class)
class GhostStringReaderPositionTest {

    // ══════════════════════════════════════════════════════════════════
    // charToBytePosition / byteToCharPosition
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun charToBytePositionAsciiOnly() {
        val s = "hello"
        // Each ASCII char is 1 byte in UTF-8
        assertEquals(
            expected = 0,
            actual = charToBytePosition(s = s, charPos = 0)
        )
        assertEquals(
            expected = 1,
            actual = charToBytePosition(s = s, charPos = 1)
        )
        assertEquals(
            expected = 5,
            actual = charToBytePosition(s = s, charPos = 5)
        )
    }

    @Test
    fun charToBytePositionWithTwoByteChars() {
        // é = U+00E9 → 2 bytes in UTF-8
        val s = "aéb"
        assertEquals(
            expected = 0,
            actual = charToBytePosition(s = s, charPos = 0)
        )
        assertEquals(
            expected = 1,
            actual = charToBytePosition(s = s, charPos = 1)
        ) // after 'a'
        assertEquals(
            expected = 3,
            actual = charToBytePosition(s = s, charPos = 2)
        ) // after 'é' (2 bytes)
        assertEquals(
            expected = 4,
            actual = charToBytePosition(s = s, charPos = 3)
        ) // after 'b'
    }

    @Test
    fun charToBytePositionWithThreeByteChars() {
        // 漢 = U+6F22 → 3 bytes in UTF-8
        val s = "a漢b"
        assertEquals(
            expected = 1,
            actual = charToBytePosition(s = s, charPos = 1)
        ) // after 'a'
        assertEquals(
            expected = 4,
            actual = charToBytePosition(s = s, charPos = 2)
        ) // after '漢' (3 bytes)
        assertEquals(
            expected = 5,
            actual = charToBytePosition(s = s, charPos = 3)
        ) // after 'b'
    }

    @Test
    fun charToBytePositionWithSurrogatePair() {
        // 🚀 = U+1F680 → surrogate pair in Kotlin String (2 chars), 4 bytes UTF-8
        val s = "a🚀b"
        assertEquals(
            expected = 1,
            actual = charToBytePosition(s = s, charPos = 1)
        )  // after 'a'
        assertEquals(
            expected = 5,
            actual = charToBytePosition(s = s, charPos = 3)
        )  // after '🚀' (2 chars, 4 bytes)
        assertEquals(
            expected = 6,
            actual = charToBytePosition(s = s, charPos = 4)
        )  // after 'b'
    }

    @Test
    fun byteToCharPositionAsciiOnly() {
        val s = "hello"
        assertEquals(
            expected = 0,
            actual = byteToCharPosition(s = s, targetBytePos = 0)
        )
        assertEquals(
            expected = 1,
            actual = byteToCharPosition(s = s, targetBytePos = 1)
        )
        assertEquals(
            expected = 5,
            actual = byteToCharPosition(s = s, targetBytePos = 5)
        )
    }

    @Test
    fun byteToCharPositionWithTwoByteChars() {
        val s = "aéb"
        assertEquals(
            expected = 1,
            actual = byteToCharPosition(s = s, targetBytePos = 1)
        )  // after 'a'
        assertEquals(
            expected = 2,
            actual = byteToCharPosition(s = s, targetBytePos = 3)
        )  // after 'é' (2 bytes)
        assertEquals(
            expected = 3,
            actual = byteToCharPosition(s = s, targetBytePos = 4)
        )  // after 'b'
    }

    @Test
    fun byteToCharPositionWithSurrogatePair() {
        val s = "a🚀b"
        assertEquals(
            expected = 1,
            actual = byteToCharPosition(s = s, targetBytePos = 1)
        )  // after 'a'
        assertEquals(
            expected = 3,
            actual = byteToCharPosition(s = s, targetBytePos = 5)
        )  // after '🚀' (4 bytes → 2 chars)
        assertEquals(
            expected = 4,
            actual = byteToCharPosition(s = s, targetBytePos = 6)
        )  // after 'b'
    }

    @Test
    fun charToByteAndByteToCharAreInverses() {
        val s = "Hello \u6F22\u5B57 \uD83D\uDE80 world"
        // Iterate only up to s.length (inclusive) but skip the trailing surrogate
        // index since iterating charPos = s.length is safe (returns full byte count)
        var charPos = 0
        while (charPos <= s.length) {
            val bytePos = charToBytePosition(s = s, charPos = charPos)
            val recoveredCharPos = byteToCharPosition(s = s, targetBytePos = bytePos)
            assertEquals(
                expected = charPos,
                actual = recoveredCharPos,
                message = "Round-trip failed at charPos=$charPos"
            )
            // Skip the low surrogate index — it doesn't have a standalone byte boundary
            if (charPos < s.length && s[charPos].isHighSurrogate()) {
                charPos += 2
            } else {
                charPos++
            }
        }
    }
}

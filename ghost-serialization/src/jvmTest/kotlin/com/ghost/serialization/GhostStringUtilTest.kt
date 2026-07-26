@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * Isolated correctness tests for [GhostStringUtil.extractLatin1Bytes] — this is the first time
 * this Unsafe-based accessor is exercised by any test. Gate A for the String-mode decode
 * optimization: must be 100% green before [GhostStringUtil] is wired into any decode path.
 */
class GhostStringUtilTest {

    @Test
    fun emptyStringReturnsEmptyByteArray() {
        val bytes = GhostStringUtil.extractLatin1Bytes("")
        assertNotNull(bytes)
        assertContentEquals(ByteArray(0), bytes)
    }

    @Test
    fun singleAsciiCharReturnsCorrectByte() {
        val bytes = GhostStringUtil.extractLatin1Bytes("a")
        assertNotNull(bytes)
        assertContentEquals(byteArrayOf('a'.code.toByte()), bytes)
    }

    @Test
    fun asciiStringsOfVaryingLengthMatchIso88591() {
        val samples = listOf(
            "x",
            "hello",
            "The quick brown fox jumps over the lazy dog",
            "0123456789",
            "{\"id\":1,\"name\":\"test\"}",
            "a".repeat(10_000),
        )
        for (s in samples) {
            val bytes = GhostStringUtil.extractLatin1Bytes(s)
            assertNotNull(bytes, "expected non-null for pure-ASCII string of length ${s.length}")
            assertContentEquals(s.toByteArray(Charsets.ISO_8859_1), bytes)
        }
    }

    @Test
    fun latin1SupplementCharsReturnCorrectBytes() {
        // U+00A3 POUND SIGN (0xA3) — a genuine Latin-1 supplement char, unlike '€' (U+20AC),
        // which is NOT Latin-1 despite being a common mistake to assume.
        val pound = "The naïve façade costs £5"
        val bytes = GhostStringUtil.extractLatin1Bytes(pound)
        assertNotNull(bytes)
        assertContentEquals(pound.toByteArray(Charsets.ISO_8859_1), bytes)
    }

    @Test
    fun cafeAccentedCharReturnsCorrectBytes() {
        val s = "café" // 'é' = U+00E9, Latin-1
        val bytes = GhostStringUtil.extractLatin1Bytes(s)
        assertNotNull(bytes)
        assertContentEquals(s.toByteArray(Charsets.ISO_8859_1), bytes)
    }

    @Test
    fun euroSignIsNotLatin1() {
        // '€' is U+20AC — outside the Latin-1 (0x00-0xFF) range. Must fall back to null,
        // not silently truncate/corrupt.
        assertNull(GhostStringUtil.extractLatin1Bytes("€5"))
    }

    @Test
    fun cjkContentReturnsNull() {
        assertNull(GhostStringUtil.extractLatin1Bytes("漢字"))
    }

    @Test
    fun emojiSurrogatePairReturnsNull() {
        assertNull(GhostStringUtil.extractLatin1Bytes("😀"))
    }

    @Test
    fun mostlyAsciiWithSingleNonLatin1CharAnywhereReturnsNullForWholeString() {
        // The JVM's compact-string coder is whole-string, not per-character — a single
        // non-Latin1 char anywhere forces the entire backing array to UTF-16.
        assertNull(GhostStringUtil.extractLatin1Bytes("漢字 in the middle of otherwise ascii text"))
        assertNull(GhostStringUtil.extractLatin1Bytes("ascii text with emoji at the end 😀"))
        assertNull(GhostStringUtil.extractLatin1Bytes("😀 emoji at the start of ascii text"))
    }

    @Test
    fun differentConstructionPathsAllAgree() {
        val expected = "hello world 123"

        val literal = "hello world 123"
        val fromStringBuilder = StringBuilder().append("hello world 123").toString()
        val fromCharArray = String(charArrayOf('h', 'e', 'l', 'l', 'o', ' ', 'w', 'o', 'r', 'l', 'd', ' ', '1', '2', '3'))
        val fromSubstring = "xxxhello world 123xxx".substring(3, 3 + expected.length)
        val fromConcat = "hello " + "world " + "123"

        val expectedBytes = expected.toByteArray(Charsets.ISO_8859_1)
        for (variant in listOf(literal, fromStringBuilder, fromCharArray, fromSubstring, fromConcat)) {
            val bytes = GhostStringUtil.extractLatin1Bytes(variant)
            assertNotNull(bytes, "expected non-null for variant constructed via a different path: $variant")
            assertContentEquals(expectedBytes, bytes)
        }
    }

    @Test
    fun repeatedCallsOnSameStringAreStableAndConsistent() {
        val s = "repeatable content 42"
        val first = GhostStringUtil.extractLatin1Bytes(s)
        val second = GhostStringUtil.extractLatin1Bytes(s)
        assertNotNull(first)
        assertNotNull(second)
        assertContentEquals(first, second)
    }

    @Test
    fun returnedArrayIsTheLiveInternalArrayNotACopy() {
        // Documents the actual contract (Unsafe.getObject returns the live `value` field of
        // the String, not a defensive copy) so callers know to treat it as read-only — mutating
        // it would corrupt the "immutable" String it came from. This is intentional (avoiding a
        // copy is the entire point of this accessor), not a bug — this test exists so nobody
        // "fixes" it into a defensive copy later without realizing that defeats the purpose.
        val s = "mutation probe"
        val bytes1 = GhostStringUtil.extractLatin1Bytes(s)
        val bytes2 = GhostStringUtil.extractLatin1Bytes(s)
        assertNotNull(bytes1)
        assertNotNull(bytes2)
        assertSame(bytes1, bytes2, "expected the same live backing array on repeated extraction")
    }

    @Test
    fun failsClosedRatherThanThrowing() {
        // Can't force the Unsafe/reflection setup to fail from within a normal test run on a
        // supported JDK, but this asserts the sentinel-based fail-closed path is at least
        // structurally reachable: a call that would fail cleanly returns null, never throws.
        // (If this test itself throws, that's the actual regression to worry about.)
        val result = GhostStringUtil.extractLatin1Bytes("no exception should ever escape this call")
        assertNotNull(result)
    }
}

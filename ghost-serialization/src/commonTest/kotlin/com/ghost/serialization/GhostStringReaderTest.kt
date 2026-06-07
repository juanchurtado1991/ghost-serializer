@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.GhostJsonConstants
import com.ghost.serialization.parser.GhostJsonStringReader
import com.ghost.serialization.parser.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Comprehensive unit tests for [GhostJsonStringReader].
 *
 * Mirrors the coverage depth of [GhostFlatReaderEdgeCaseTest], [GhostCrashProofTest],
 * and [GhostReaderAdvancedTest] so that the String reader is held to the exact same
 * correctness contract as its byte-based siblings.
 *
 * Sections:
 *   1.  isNextNullValue correctness
 *   2.  Unicode escape sequences (valid + invalid)
 *   3.  Long/Int overflow detection
 *   4.  Truncated literals (true/false/null)
 *   5.  Surrogate pair handling
 *   6.  selectString / selectNameAndConsume
 *   7.  Whitespace-only input
 *   8.  Stream exhaustion mid-parse
 *   9.  Escape character roundtrips
 *   10. Numbers at end of stream
 *   11. skipValue gauntlet
 *   12. Depth tracking
 *   13. Strict mode enforcement
 *   14. Float precision
 *   15. Reset and reuse
 *   16. Resilient decoder
 *   17. List and map reading
 *   18. peekStringField
 *   19. charToBytePosition / byteToCharPosition
 *   20. Adjacent null values
 *   21. Mixed-type arrays
 *   22. Prefix-sharing field names
 *   23. Boolean coercion
 *   24. String coercion to numbers
 */
@OptIn(InternalGhostApi::class)
class GhostStringReaderTest {

    // ── Factory helpers ───────────────────────────────────────────────

    private fun readerOf(json: String): GhostJsonStringReader =
        GhostJsonStringReader(json)

    // ══════════════════════════════════════════════════════════════════
    // 1. isNextNullValue correctness
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun isNextNullDetectsActualNull() {
        val reader = readerOf("""{"v":null}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertTrue(reader.isNextNullValue())
    }

    @Test
    fun isNextNullReturnsFalseForString() {
        val reader = readerOf("""{"v":"hello"}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFalse(reader.isNextNullValue())
    }

    @Test
    fun isNextNullReturnsFalseForNumber() {
        val reader = readerOf("""{"v":42}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFalse(reader.isNextNullValue())
    }

    @Test
    fun isNextNullReturnsFalseForObject() {
        val reader = readerOf("""{"v":{}}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFalse(reader.isNextNullValue())
    }

    @Test
    fun isNextNullReturnsFalseForArray() {
        val reader = readerOf("""{"v":[]}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFalse(reader.isNextNullValue())
    }

    @Test
    fun isNextNullReturnsFalseForTrue() {
        val reader = readerOf("""{"v":true}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFalse(reader.isNextNullValue())
    }

    @Test
    fun isNextNullReturnsFalseForFalse() {
        val reader = readerOf("""{"v":false}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFalse(reader.isNextNullValue())
    }

    // ══════════════════════════════════════════════════════════════════
    // 2. Unicode escape sequences
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun readsValidUnicodeEscapeAscii() {
        val reader = readerOf("""{"v":"\u0041"}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals("A", reader.nextString())
    }

    @Test
    fun readsValidUnicodeEscapeLatinExtended() {
        // U+00E9 = é
        val reader = readerOf("{\"v\":\"\\u00E9\"}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals("\u00E9", reader.nextString())
    }

    @Test
    fun readsValidUnicodeEscapeCJK() {
        // U+6F22 = 漢
        val reader = readerOf("{\"v\":\"\\u6F22\"}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals("\u6F22", reader.nextString())
    }

    @Test
    fun readsSurrogatePairEmoji() {
        // U+1F680 = 🚀 encoded as surrogate pair \uD83D\uDE80
        val json = "{\"v\":\"\\uD83D\\uDE80\"}"
        val reader = readerOf(json)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        val result = reader.nextString()
        assertEquals("\uD83D\uDE80", result)
    }

    @Test
    fun readsDirectEmojiWithoutSurrogatePairEscape() {
        val reader = readerOf("{\"v\":\"\uD83D\uDD25\uD83D\uDC80\uD83C\uDF89\"}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals("\uD83D\uDD25\uD83D\uDC80\uD83C\uDF89", reader.nextString())
    }

    @Test
    fun invalidUnicodeEscapeThrowsException() {
        val json = "{\"v\":\"\\u00GG\"}"
        val reader = readerOf(json)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<Exception> { reader.nextString() }
    }

    @Test
    fun truncatedUnicodeEscapeThrowsException() {
        val json = "{\"v\":\"\\u00\"}"
        val reader = readerOf(json)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<Exception> { reader.nextString() }
    }

    @Test
    fun orphanedHighSurrogateThrowsException() {
        // \uD83D is a high surrogate with no following low surrogate
        val reader = readerOf("{\"v\":\"abc\\uD83D\"}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> { reader.nextString() }
    }

    @Test
    fun readsMultiByteUnicodeDirectly() {
        val reader = readerOf("""{"v":"漢字テスト"}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals("漢字テスト", reader.nextString())
    }

    @Test
    fun readsSupplementaryPlane4ByteChar() {
        // U+1F600 GRINNING FACE — 4-byte UTF-8, 2-char Kotlin String (surrogate pair)
        val reader = readerOf("""{"v":"😀"}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals("😀", reader.nextString())
    }

    // ══════════════════════════════════════════════════════════════════
    // 3. Long / Int overflow detection
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun readsZeroInt() {
        val reader = readerOf("""{"v":0}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(0, reader.nextInt())
    }

    @Test
    fun readsPositiveInt() {
        val reader = readerOf("""{"v":42}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(42, reader.nextInt())
    }

    @Test
    fun readsNegativeInt() {
        val reader = readerOf("""{"v":-99}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(-99, reader.nextInt())
    }

    @Test
    fun readsExactLongMaxValue() {
        val reader = readerOf("""{"v":${Long.MAX_VALUE}}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(Long.MAX_VALUE, reader.nextLong())
    }

    @Test
    fun readsExactLongMinValue() {
        val reader = readerOf("""{"v":${Long.MIN_VALUE}}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(Long.MIN_VALUE, reader.nextLong())
    }

    @Test
    fun veryLargeNumberThrowsLongOverflow() {
        val reader = readerOf("""{"v":99999999999999999999}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> { reader.nextLong() }
    }

    @Test
    fun intOverflowFromLongThrows() {
        val reader = readerOf("""{"v":${Long.MAX_VALUE}}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> { reader.nextInt() }
    }

    @Test
    fun longNegativeOverflowThrows() {
        val reader = readerOf("""{"v":-92233720368547758089}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> { reader.nextLong() }
    }

    // ══════════════════════════════════════════════════════════════════
    // 4. Truncated literals
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun truncatedNullFailsOnConsume() {
        val reader = readerOf("""{"v":nul}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertTrue(reader.isNextNullValue())
        assertFailsWith<Exception> { reader.consumeNull() }
    }

    @Test
    fun truncatedTrueThrowsException() {
        val reader = readerOf("tru")
        assertFailsWith<Exception> { reader.nextBoolean() }
    }

    @Test
    fun truncatedFalseThrowsException() {
        val reader = readerOf("fals")
        assertFailsWith<Exception> { reader.nextBoolean() }
    }

    // ══════════════════════════════════════════════════════════════════
    // 5. All RFC JSON escape sequences
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun readsNewlineAndTabEscapes() {
        val reader = readerOf("{\"v\":\"line1\\nline2\\ttab\"}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals("line1\nline2\ttab", reader.nextString())
    }

    @Test
    fun readsBackslashAndQuoteEscapes() {
        val reader = readerOf("{\"v\":\"back\\\\slash and \\\"quotes\\\"\"}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals("back\\slash and \"quotes\"", reader.nextString())
    }

    @Test
    fun readsAllRfcEscapes() {
        val reader = readerOf("{\"v\":\"\\b\\f\\n\\r\\t\"}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals("\b\u000C\n\r\t", reader.nextString())
    }

    @Test
    fun readsStringOfOnlyBackslashes() {
        val json = "{\"v\":\"\\\\\\\\\"}"
        val reader = readerOf(json)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals("\\\\", reader.nextString())
    }

    @Test
    fun readsStringWithConsecutiveJsonEscapes() {
        val bs = '\\'
        val q = '"'
        val json = "${q}v${q}:${q}${bs}n${bs}t${bs}r${bs}b${q}"
        val reader = readerOf("{$json}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals("\n\t\r\b", reader.nextString())
    }

    // ══════════════════════════════════════════════════════════════════
    // 6. selectString / selectNameAndConsume
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun selectNameAndConsumeMatchesKnownKey() {
        val options = JsonReaderOptions.of("name")
        val reader = readerOf("""{"name":"Ghost"}""")
        reader.beginObject()
        val index = reader.selectNameAndConsume(options)
        assertEquals(0, index)
        assertEquals("Ghost", reader.nextString())
    }

    @Test
    fun selectNameAndConsumeReturnsNoneForUnknownKey() {
        val options = JsonReaderOptions.of("name")
        val reader = readerOf("""{"other":"value"}""")
        reader.beginObject()
        val index = reader.selectNameAndConsume(options)
        assertEquals(GhostJsonConstants.MATCH_NONE, index)
        reader.skipValue()
    }

    @Test
    fun selectStringWithEmptyOptionsSkipsAllFields() {
        val options = JsonReaderOptions.of()
        val json = """{"a":1,"b":2}"""
        val reader = readerOf(json)
        reader.beginObject()

        assertEquals(GhostJsonConstants.MATCH_NONE, reader.selectString(options))
        reader.consumeKeySeparator()
        reader.skipValue()
        assertEquals(GhostJsonConstants.MATCH_NONE, reader.selectString(options))
        reader.consumeKeySeparator()
        reader.skipValue()
        assertEquals(GhostJsonConstants.MATCH_END, reader.selectString(options))
        reader.endObject()
    }

    @Test
    fun selectStringWithSingleOption() {
        val options = JsonReaderOptions.of("only")
        val reader = readerOf("""{"only":"found"}""")
        reader.beginObject()
        assertEquals(0, reader.selectString(options))
        reader.consumeKeySeparator()
        assertEquals("found", reader.nextString())
        assertEquals(GhostJsonConstants.MATCH_END, reader.selectString(options))
        reader.endObject()
    }

    @Test
    fun readsObjectWithMultipleArrays() {
        val options = JsonReaderOptions.of("a", "b")
        val json = """{"a":[1,2],"b":[3,4]}"""
        val reader = readerOf(json)
        reader.beginObject()

        assertEquals(0, reader.selectString(options))
        reader.consumeKeySeparator()
        val list1 = reader.readList { reader.nextInt() }
        assertEquals(listOf(1, 2), list1)

        assertEquals(1, reader.selectString(options))
        reader.consumeKeySeparator()
        val list2 = reader.readList { reader.nextInt() }
        assertEquals(listOf(3, 4), list2)
        reader.endObject()
    }

    @Test
    fun selectStringWithThreePrefixVariants() {
        val options = JsonReaderOptions.of("user", "userId", "userIds", "userName")
        val json = """{"userIds":[1],"userName":"g","userId":42,"user":"obj"}"""
        val reader = readerOf(json)
        reader.beginObject()

        assertEquals(2, reader.selectString(options))
        reader.consumeKeySeparator()
        reader.readList { reader.nextInt() }
        assertEquals(3, reader.selectString(options))
        reader.consumeKeySeparator()
        assertEquals("g", reader.nextString())
        assertEquals(1, reader.selectString(options))
        reader.consumeKeySeparator()
        assertEquals(42, reader.nextInt())
        assertEquals(0, reader.selectString(options))
        reader.consumeKeySeparator()
        assertEquals("obj", reader.nextString())
    }

    @Test
    fun selectStringWithLongFieldName() {
        val longName = "thisIsAVeryLongFieldNameThatExceedsNormalLengths"
        val options = JsonReaderOptions.of(longName)
        val json = """{"$longName":"found"}"""
        val reader = readerOf(json)
        reader.beginObject()
        assertEquals(0, reader.selectString(options))
        reader.consumeKeySeparator()
        assertEquals("found", reader.nextString())
    }

    @Test
    fun selectStringWithSingleCharFields() {
        val options = JsonReaderOptions.of("a", "b", "c")
        val json = """{"b":2,"c":3,"a":1}"""
        val reader = readerOf(json)
        reader.beginObject()

        assertEquals(1, reader.selectString(options))
        reader.consumeKeySeparator()
        assertEquals(2, reader.nextInt())
        assertEquals(2, reader.selectString(options))
        reader.consumeKeySeparator()
        assertEquals(3, reader.nextInt())
        assertEquals(0, reader.selectString(options))
        reader.consumeKeySeparator()
        assertEquals(1, reader.nextInt())
    }

    @Test
    fun selectStringWithUnderscoreFields() {
        val options = JsonReaderOptions.of("user_id", "user_name", "user_ids")
        val json = """{"user_ids":[1,2],"user_id":42,"user_name":"ghost"}"""
        val reader = readerOf(json)
        reader.beginObject()

        assertEquals(2, reader.selectString(options))
        reader.consumeKeySeparator()
        reader.readList { reader.nextInt() }

        assertEquals(0, reader.selectString(options))
        reader.consumeKeySeparator()
        assertEquals(42, reader.nextInt())

        assertEquals(1, reader.selectString(options))
        reader.consumeKeySeparator()
        assertEquals("ghost", reader.nextString())
    }

    @Test
    fun skipsObjectContainingBracesInStrings() {
        val options = JsonReaderOptions.of("id")
        val json = """{"junk":{"msg":"value with { and } inside"},"id":1}"""
        val reader = readerOf(json)
        reader.beginObject()
        assertEquals(GhostJsonConstants.MATCH_NONE, reader.selectString(options))
        reader.consumeKeySeparator()
        reader.skipValue()
        assertEquals(0, reader.selectString(options))
        reader.consumeKeySeparator()
        assertEquals(1, reader.nextInt())
    }

    @Test
    fun skipsArrayContainingBracketsInStrings() {
        val options = JsonReaderOptions.of("id")
        val json = """{"junk":["contains [ and ]"],"id":2}"""
        val reader = readerOf(json)
        reader.beginObject()
        assertEquals(GhostJsonConstants.MATCH_NONE, reader.selectString(options))
        reader.consumeKeySeparator()
        reader.skipValue()
        assertEquals(0, reader.selectString(options))
        reader.consumeKeySeparator()
        assertEquals(2, reader.nextInt())
    }

    @Test
    fun skipsObjectContainingEscapedQuotesInStrings() {
        val options = JsonReaderOptions.of("id")
        val json = "{\"junk\":{\"msg\":\"escaped \\\"quotes\\\" and {braces}\"},\"id\":3}"
        val reader = readerOf(json)
        reader.beginObject()
        assertEquals(GhostJsonConstants.MATCH_NONE, reader.selectString(options))
        reader.consumeKeySeparator()
        reader.skipValue()
        assertEquals(0, reader.selectString(options))
        reader.consumeKeySeparator()
        assertEquals(3, reader.nextInt())
    }

    // ══════════════════════════════════════════════════════════════════
    // 7. Whitespace-only input
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun whitespaceOnlyInputReportsEndDocument() {
        val reader = readerOf("   \n\t  ")
        assertEquals(GhostJsonConstants.MATCH_END, reader.peekNextToken())
    }

    @Test
    fun emptyStringInputReportsEndDocument() {
        val reader = readerOf("")
        assertEquals(GhostJsonConstants.MATCH_END, reader.peekNextToken())
    }

    // ══════════════════════════════════════════════════════════════════
    // 8. Stream exhaustion mid-parse
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun exhaustedSourceDuringObjectParseFails() {
        val reader = readerOf("{")
        reader.beginObject()
        assertFailsWith<Exception> { reader.nextKey() }
    }

    @Test
    fun exhaustedSourceDuringStringFails() {
        val reader = readerOf("{\"v\":\"unterminated")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<Exception> { reader.nextString() }
    }

    @Test
    fun exhaustedSourceDuringArrayFails() {
        val reader = readerOf("[1,2,")
        assertFailsWith<Exception> {
            reader.readList { reader.nextInt() }
        }
    }

    @Test
    fun unmatchedOpenBraceThrowsOnEnd() {
        val reader = readerOf("""{"v":1""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        reader.nextInt()
        assertFailsWith<GhostJsonException> { reader.endObject() }
    }

    // ══════════════════════════════════════════════════════════════════
    // 9. Numbers at end of stream / edge-case numerics
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun readsNumberAtEndOfArray() {
        val reader = readerOf("[42]")
        val result = reader.readList { reader.nextInt() }
        assertEquals(listOf(42), result)
    }

    @Test
    fun readsDoubleAtEndOfArray() {
        val reader = readerOf("[3.14]")
        val result = reader.readList { reader.nextDouble() }
        assertEquals(1, result.size)
        assertEquals(3.14, result[0], 0.001)
    }

    @Test
    fun readsScientificNotationPositiveExponent() {
        val reader = readerOf("""{"v":1e10}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(1e10, reader.nextDouble(), 0.1)
    }

    @Test
    fun readsScientificNotationNegativeExponent() {
        val reader = readerOf("""{"v":1.23e-4}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(1.23e-4, reader.nextDouble(), 1e-10)
    }

    @Test
    fun readsScientificNotationUppercaseE() {
        val reader = readerOf("""{"v":1.0E+2}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(100.0, reader.nextDouble(), 0.01)
    }

    @Test
    fun readsDoublePrecision() {
        val reader = readerOf("""{"v":1.234567890123456}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(1.234567890123456, reader.nextDouble(), 1e-15)
    }

    @Test
    fun readsArrayOfLongs() {
        val reader = readerOf("[${Long.MAX_VALUE},0,${Long.MIN_VALUE}]")
        val result = reader.readList { reader.nextLong() }
        assertEquals(listOf(Long.MAX_VALUE, 0L, Long.MIN_VALUE), result)
    }

    // ══════════════════════════════════════════════════════════════════
    // 10. skipValue gauntlet
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun skipsUnknownObjectValue() {
        val options = JsonReaderOptions.of("id")
        val json = """{"extra":{"a":1},"id":42}"""
        val reader = readerOf(json)
        reader.beginObject()
        assertEquals(GhostJsonConstants.MATCH_NONE, reader.selectString(options))
        reader.consumeKeySeparator()
        reader.skipValue()
        assertEquals(0, reader.selectString(options))
        reader.consumeKeySeparator()
        assertEquals(42, reader.nextInt())
    }

    @Test
    fun skipsUnknownArrayValue() {
        val options = JsonReaderOptions.of("id")
        val json = """{"arr":[1,"two",null,true,[]],"id":99}"""
        val reader = readerOf(json)
        reader.beginObject()
        assertEquals(GhostJsonConstants.MATCH_NONE, reader.selectString(options))
        reader.consumeKeySeparator()
        reader.skipValue()
        assertEquals(0, reader.selectString(options))
        reader.consumeKeySeparator()
        assertEquals(99, reader.nextInt())
    }

    @Test
    fun skipsUnknownStringValue() {
        val options = JsonReaderOptions.of("id")
        val json = """{"text":"hello world","id":7}"""
        val reader = readerOf(json)
        reader.beginObject()
        assertEquals(GhostJsonConstants.MATCH_NONE, reader.selectString(options))
        reader.consumeKeySeparator()
        reader.skipValue()
        assertEquals(0, reader.selectString(options))
        reader.consumeKeySeparator()
        assertEquals(7, reader.nextInt())
    }

    @Test
    fun skipsUnknownBooleanValue() {
        val options = JsonReaderOptions.of("id")
        val json = """{"flag":true,"id":8}"""
        val reader = readerOf(json)
        reader.beginObject()
        assertEquals(GhostJsonConstants.MATCH_NONE, reader.selectString(options))
        reader.consumeKeySeparator()
        reader.skipValue()
        assertEquals(0, reader.selectString(options))
        reader.consumeKeySeparator()
        assertEquals(8, reader.nextInt())
    }

    @Test
    fun skipsUnknownNullValue() {
        val options = JsonReaderOptions.of("id")
        val json = """{"nothing":null,"id":9}"""
        val reader = readerOf(json)
        reader.beginObject()
        assertEquals(GhostJsonConstants.MATCH_NONE, reader.selectString(options))
        reader.consumeKeySeparator()
        reader.skipValue()
        assertEquals(0, reader.selectString(options))
        reader.consumeKeySeparator()
        assertEquals(9, reader.nextInt())
    }

    @Test
    fun skipsUnknownNumberValue() {
        val options = JsonReaderOptions.of("id")
        val json = """{"count":12345,"id":10}"""
        val reader = readerOf(json)
        reader.beginObject()
        assertEquals(GhostJsonConstants.MATCH_NONE, reader.selectString(options))
        reader.consumeKeySeparator()
        reader.skipValue()
        assertEquals(0, reader.selectString(options))
        reader.consumeKeySeparator()
        assertEquals(10, reader.nextInt())
    }

    @Test
    fun skipsMultipleConsecutiveUnknownFields() {
        val options = JsonReaderOptions.of("id")
        val json = """{"a":"x","b":true,"c":[1],"d":null,"e":{},"id":1}"""
        val reader = readerOf(json)
        reader.beginObject()
        repeat(5) {
            assertEquals(GhostJsonConstants.MATCH_NONE, reader.selectString(options))
            reader.consumeKeySeparator()
            reader.skipValue()
        }
        assertEquals(0, reader.selectString(options))
        reader.consumeKeySeparator()
        assertEquals(1, reader.nextInt())
    }

    @Test
    fun skipsDeeplyNestedUnknownObject() {
        val options = JsonReaderOptions.of("id")
        val json = """{"deep":{"l1":{"l2":{"l3":{"l4":"bottom"}}}},"id":77}"""
        val reader = readerOf(json)
        reader.beginObject()
        assertEquals(GhostJsonConstants.MATCH_NONE, reader.selectString(options))
        reader.consumeKeySeparator()
        reader.skipValue()
        assertEquals(0, reader.selectString(options))
        reader.consumeKeySeparator()
        assertEquals(77, reader.nextInt())
    }

    // ══════════════════════════════════════════════════════════════════
    // 11. Depth tracking
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun depthIncreasesAndDecreasesCorrectly() {
        val reader = readerOf("""{"a":{"b":[1]}}""")
        assertEquals(0, reader.depth)
        reader.beginObject()
        assertEquals(1, reader.depth)
        reader.nextKey()
        reader.consumeKeySeparator()
        reader.beginObject()
        assertEquals(2, reader.depth)
        reader.nextKey()
        reader.consumeKeySeparator()
        reader.beginArray()
        assertEquals(3, reader.depth)
        reader.nextInt()
        reader.endArray()
        assertEquals(2, reader.depth)
        reader.endObject()
        assertEquals(1, reader.depth)
        reader.endObject()
        assertEquals(0, reader.depth)
    }

    @Test
    fun deepNestingRespectsMaxDepthLimit() {
        val deepJson = "[".repeat(300) + "]".repeat(300)
        val reader = readerOf(deepJson)
        assertFailsWith<GhostJsonException> {
            repeat(300) { reader.beginArray() }
        }
    }

    @Test
    fun customMaxDepthIsEnforced() {
        val reader = GhostJsonStringReader("[[[]]]", maxDepth = 2)
        reader.beginArray()
        reader.beginArray()
        assertFailsWith<GhostJsonException> {
            reader.beginArray()
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 12. Strict mode
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun strictModeRejectsObjectWithoutComma() {
        val reader = readerOf("""{"a": 1 "b": 2}""")
        reader.strictMode = true
        reader.beginObject()
        assertEquals("a", reader.nextKey())
        reader.consumeKeySeparator()
        assertEquals(1, reader.nextInt())
        assertFailsWith<GhostJsonException> { reader.nextKey() }
    }

    @Test
    fun strictModeRejectsArrayWithoutComma() {
        val reader = readerOf("[1 2 3]")
        reader.strictMode = true
        reader.beginArray()
        assertEquals(1, reader.nextInt())
        assertFailsWith<GhostJsonException> { reader.consumeArraySeparator() }
    }

    @Test
    fun strictModeRejectsTrailingCommaInObject() {
        val reader = readerOf("""{"a":1,}""")
        reader.strictMode = true
        reader.beginObject()
        assertFailsWith<GhostJsonException> {
            reader.nextKey()
            reader.consumeKeySeparator()
            reader.nextInt()
            reader.nextKey()  // trailing comma → this must throw
        }
    }

    @Test
    fun strictModeThrowsOnUnknownField() {
        val options = JsonReaderOptions.of("id")
        val json = """{"unknown":"val","id":1}"""
        val reader = readerOf(json)
        reader.strictMode = true
        reader.beginObject()
        assertFailsWith<GhostJsonException> {
            reader.selectString(options)
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 13. Float precision
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun nextFloatLosesPrecisionGracefully() {
        val reader = readerOf("""{"v":1.123456789}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        val f = reader.nextFloat()
        assertEquals(1.1234568, f.toDouble(), 0.0000001)
    }

    @Test
    fun readsNegativeFloat() {
        val reader = readerOf("""{"v":-3.14}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(-3.14f, reader.nextFloat(), 0.001f)
    }

    // ══════════════════════════════════════════════════════════════════
    // 14. Token peek
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun peeksObjectToken() {
        val reader = readerOf("{}")
        assertEquals(GhostJsonConstants.OPEN_OBJ_INT, reader.peekNextToken())
    }

    @Test
    fun peeksArrayToken() {
        val reader = readerOf("[]")
        assertEquals(GhostJsonConstants.OPEN_ARR_INT, reader.peekNextToken())
    }

    @Test
    fun peeksStringToken() {
        val reader = readerOf("\"hello\"")
        assertEquals(GhostJsonConstants.QUOTE_INT, reader.peekNextToken())
    }

    @Test
    fun peeksNumberToken() {
        val reader = readerOf("42")
        assertEquals('4'.code, reader.peekNextToken())
    }

    @Test
    fun peeksBooleanTrueToken() {
        val reader = readerOf("true")
        assertEquals(GhostJsonConstants.TRUE_CHAR_INT, reader.peekNextToken())
    }

    @Test
    fun peeksBooleanFalseToken() {
        val reader = readerOf("false")
        assertEquals(GhostJsonConstants.FALSE_CHAR_INT, reader.peekNextToken())
    }

    @Test
    fun peeksNullToken() {
        val reader = readerOf("null")
        assertEquals(GhostJsonConstants.NULL_CHAR_INT, reader.peekNextToken())
    }

    @Test
    fun peeksEndOfDocument() {
        val reader = readerOf("")
        assertEquals(GhostJsonConstants.MATCH_END, reader.peekNextToken())
    }

    // ══════════════════════════════════════════════════════════════════
    // 15. Reset and reuse (pool simulation)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun pooledResetAndReuseWithDifferentSizes() {
        val reader = GhostJsonStringReader("""{"short":1}""")

        reader.beginObject()
        reader.skipWhitespace()
        assertEquals("short", reader.readQuotedString())
        reader.consumeKeySeparator()
        assertEquals(1, reader.nextInt())
        reader.endObject()

        val longerJson = """{"very_long_field_name_indeed":1234567890123}"""
        reader.reset(longerJson)
        reader.beginObject()
        reader.skipWhitespace()
        assertEquals("very_long_field_name_indeed", reader.readQuotedString())
        reader.consumeKeySeparator()
        assertEquals(1234567890123L, reader.nextLong())
        reader.endObject()

        val shortJson = """{"a":true}"""
        reader.reset(shortJson)
        reader.beginObject()
        reader.skipWhitespace()
        assertEquals("a", reader.readQuotedString())
        reader.consumeKeySeparator()
        assertEquals(true, reader.nextBoolean())
        reader.endObject()
    }

    @Test
    fun resetClearsAllState() {
        val reader = GhostJsonStringReader("""{"x":{"nested":1}}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        reader.beginObject()
        assertEquals(2, reader.depth)

        reader.reset("""{"y":2}""")
        assertEquals(0, reader.depth)
        assertEquals(0, reader.position)
        reader.beginObject()
        assertEquals("y", reader.nextKey())
    }

    // ══════════════════════════════════════════════════════════════════
    // 16. Resilient decoder
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun resilientDecodeReturnsNullOnTypeMismatch() {
        val reader = readerOf("""{"v":{"nested": 42}}""")
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        val depthBefore = reader.depth

        val result = reader.decodeResilient {
            reader.beginObject()
            reader.skipWhitespace()
            reader.readQuotedString()
            reader.consumeKeySeparator()
            reader.nextString() // 42 is not a string → GhostJsonException
            reader.endObject()
        }

        assertNull(result)
        assertEquals(depthBefore, reader.depth)
    }

    @Test
    fun resilientDecodeRestoresPositionOnFailure() {
        // Canonical decodeResilient use case: a nested object parse that throws internally.
        // The reader is inside an outer object pointing at a value that is an object.
        // The lambda attempts an invalid read (nextString on an Int) → throws.
        // decodeResilient must restore depth + position, then skipValue so the outer
        // parse can continue with the next field.
        val reader = readerOf("""{"v":{"nested": 42}, "ok":1}""")
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        val depthBefore = reader.depth

        val result = reader.decodeResilient {
            reader.beginObject()
            reader.skipWhitespace()
            reader.readQuotedString()
            reader.consumeKeySeparator()
            reader.nextString()   // 42 is not a string → GhostJsonException
            reader.endObject()
        }

        assertNull(result)
        // Depth must be fully restored after failure
        assertEquals(depthBefore, reader.depth)
        // The outer object must still be parseable after the resilient skip
        reader.skipWhitespace()
        val nextKey = reader.nextKey()
        assertEquals("ok", nextKey)
    }

    @Test
    fun resilientDecodeDepthNotLeaked() {
        val reader = readerOf("""{"v":{"nested": 42}}""")
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        assertEquals(1, reader.depth)

        reader.decodeResilient {
            reader.beginObject()
            reader.skipWhitespace()
            reader.readQuotedString()
            reader.consumeKeySeparator()
            reader.nextString()
            reader.endObject()
        }
        assertEquals(1, reader.depth)
    }

    // ══════════════════════════════════════════════════════════════════
    // 17. List and map reading
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun readsListOfInts() {
        val reader = readerOf("[1,2,3]")
        val list = reader.readList { reader.nextInt() }
        assertEquals(listOf(1, 2, 3), list)
    }

    @Test
    fun readsEmptyList() {
        val reader = readerOf("[]")
        val list = reader.readList { reader.nextInt() }
        assertEquals(emptyList<Int>(), list)
    }

    @Test
    fun readsSingleElementList() {
        val reader = readerOf("[99]")
        val list = reader.readList { reader.nextInt() }
        assertEquals(listOf(99), list)
    }

    @Test
    fun readsListOfStrings() {
        val reader = readerOf("""["a","b","c"]""")
        val list = reader.readList { reader.nextString() }
        assertEquals(listOf("a", "b", "c"), list)
    }

    @Test
    fun readsListOfBooleans() {
        val reader = readerOf("[true,false,true]")
        val result = reader.readList { reader.nextBoolean() }
        assertEquals(listOf(true, false, true), result)
    }

    @Test
    fun readsListOfDoubles() {
        val reader = readerOf("[1.1,2.2,3.3]")
        val result = reader.readList { reader.nextDouble() }
        assertEquals(3, result.size)
        assertEquals(1.1, result[0], 0.01)
        assertEquals(3.3, result[2], 0.01)
    }

    @Test
    fun readsMapOfStringToInt() {
        val reader = readerOf("""{"a":1,"b":2}""")
        val map = reader.readMap(
            keyParser = { reader.readQuotedString() },
            valueParser = { reader.nextInt() }
        )
        assertEquals(mapOf("a" to 1, "b" to 2), map)
    }

    @Test
    fun readsEmptyMap() {
        val reader = readerOf("{}")
        reader.beginObject()
        val key = reader.nextKey()
        assertNull(key)
        reader.endObject()
    }

    @Test
    fun readsMapWithStringValues() {
        val json = """{"k1":"v1","k2":"v2"}"""
        val reader = readerOf(json)
        reader.beginObject()
        val map = buildMap {
            while (true) {
                val key = reader.nextKey() ?: break
                reader.consumeKeySeparator()
                put(key, reader.nextString())
            }
        }
        reader.endObject()
        assertEquals(mapOf("k1" to "v1", "k2" to "v2"), map)
    }

    @Test
    fun readsConsecutiveNullValues() {
        val json = """{"a":null,"b":null,"c":null}"""
        val options = JsonReaderOptions.of("a", "b", "c")
        val reader = readerOf(json)
        reader.beginObject()

        assertEquals(0, reader.selectString(options))
        reader.consumeKeySeparator()
        assertTrue(reader.isNextNullValue())
        reader.consumeNull()

        assertEquals(1, reader.selectString(options))
        reader.consumeKeySeparator()
        assertTrue(reader.isNextNullValue())
        reader.consumeNull()

        assertEquals(2, reader.selectString(options))
        reader.consumeKeySeparator()
        assertTrue(reader.isNextNullValue())
        reader.consumeNull()
    }

    // ══════════════════════════════════════════════════════════════════
    // 18. Structural edge cases
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun emptyObjectParsesSuccessfully() {
        val reader = readerOf("{}")
        reader.beginObject()
        reader.endObject()
    }

    @Test
    fun emptyArrayParsesSuccessfully() {
        val reader = readerOf("[]")
        reader.beginArray()
        reader.endArray()
    }

    @Test
    fun nestedEmptyStructures() {
        val reader = readerOf("""{"a":[]}""")
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        reader.beginArray()
        reader.endArray()
        reader.endObject()
    }

    @Test
    fun readsSingleFieldObject() {
        val reader = readerOf("""{"only":true}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertTrue(reader.nextBoolean())
        reader.endObject()
    }

    @Test
    fun handlesExcessiveWhitespace() {
        val reader = readerOf("  {  \"v\"  :  42  }  ")
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        assertEquals(42, reader.nextInt())
        reader.endObject()
    }

    @Test
    fun handlesNewlinesAndTabs() {
        val json = "{\n\t\"v\"\n\t:\n\t99\n}"
        val reader = readerOf(json)
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        assertEquals(99, reader.nextInt())
        reader.endObject()
    }

    // ══════════════════════════════════════════════════════════════════
    // 19. peekStringField
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun peekStringFieldReturnsValueForKnownKey() {
        // peekStringField reads from current position in the raw string.
        // The position starts at 0, so the raw JSON starts with '{' — we advance past it
        // to position the reader at the first key.
        val json = """{"type":"USER","id":1}"""
        val reader = readerOf(json)
        // Advance past '{' so position points at the first '"'
        reader.beginObject()
        val typeValue = reader.peekStringField("type")
        assertEquals("USER", typeValue)
        // Position must NOT advance — the object is still parseable
        assertNotNull(reader.nextKey())
    }

    @Test
    fun peekStringFieldReturnsNullForMissingKey() {
        val reader = readerOf("""{"id":1}""")
        val typeValue = reader.peekStringField("type")
        assertNull(typeValue)
    }

    @Test
    fun peekStringFieldReturnsNullWhenValueIsNotString() {
        val reader = readerOf("""{"v":42}""")
        val result = reader.peekStringField("v")
        assertNull(result)
    }

    // ══════════════════════════════════════════════════════════════════
    // 20. charToBytePosition / byteToCharPosition
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun charToBytePositionAsciiOnly() {
        val s = "hello"
        // Each ASCII char is 1 byte in UTF-8
        assertEquals(0, charToBytePosition(s, 0))
        assertEquals(1, charToBytePosition(s, 1))
        assertEquals(5, charToBytePosition(s, 5))
    }

    @Test
    fun charToBytePositionWithTwoByteChars() {
        // é = U+00E9 → 2 bytes in UTF-8
        val s = "aéb"
        assertEquals(0, charToBytePosition(s, 0))
        assertEquals(1, charToBytePosition(s, 1)) // after 'a'
        assertEquals(3, charToBytePosition(s, 2)) // after 'é' (2 bytes)
        assertEquals(4, charToBytePosition(s, 3)) // after 'b'
    }

    @Test
    fun charToBytePositionWithThreeByteChars() {
        // 漢 = U+6F22 → 3 bytes in UTF-8
        val s = "a漢b"
        assertEquals(1, charToBytePosition(s, 1)) // after 'a'
        assertEquals(4, charToBytePosition(s, 2)) // after '漢' (3 bytes)
        assertEquals(5, charToBytePosition(s, 3)) // after 'b'
    }

    @Test
    fun charToBytePositionWithSurrogatePair() {
        // 🚀 = U+1F680 → surrogate pair in Kotlin String (2 chars), 4 bytes UTF-8
        val s = "a🚀b"
        assertEquals(1, charToBytePosition(s, 1))  // after 'a'
        assertEquals(5, charToBytePosition(s, 3))  // after '🚀' (2 chars, 4 bytes)
        assertEquals(6, charToBytePosition(s, 4))  // after 'b'
    }

    @Test
    fun byteToCharPositionAsciiOnly() {
        val s = "hello"
        assertEquals(0, byteToCharPosition(s, 0))
        assertEquals(1, byteToCharPosition(s, 1))
        assertEquals(5, byteToCharPosition(s, 5))
    }

    @Test
    fun byteToCharPositionWithTwoByteChars() {
        val s = "aéb"
        assertEquals(1, byteToCharPosition(s, 1))  // after 'a'
        assertEquals(2, byteToCharPosition(s, 3))  // after 'é' (2 bytes)
        assertEquals(3, byteToCharPosition(s, 4))  // after 'b'
    }

    @Test
    fun byteToCharPositionWithSurrogatePair() {
        val s = "a🚀b"
        assertEquals(1, byteToCharPosition(s, 1))  // after 'a'
        assertEquals(3, byteToCharPosition(s, 5))  // after '🚀' (4 bytes → 2 chars)
        assertEquals(4, byteToCharPosition(s, 6))  // after 'b'
    }

    @Test
    fun charToByteAndByteToCharAreInverses() {
        val s = "Hello \u6F22\u5B57 \uD83D\uDE80 world"
        // Iterate only up to s.length (inclusive) but skip the trailing surrogate
        // index since iterating charPos = s.length is safe (returns full byte count)
        var charPos = 0
        while (charPos <= s.length) {
            val bytePos = charToBytePosition(s, charPos)
            val recoveredCharPos = byteToCharPosition(s, bytePos)
            assertEquals(charPos, recoveredCharPos,
                "Round-trip failed at charPos=$charPos")
            // Skip the low surrogate index — it doesn't have a standalone byte boundary
            if (charPos < s.length && s[charPos].isHighSurrogate()) {
                charPos += 2
            } else {
                charPos++
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 21. Boolean coercion
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun coercesIntOneToBooleanTrue() {
        val reader = GhostJsonStringReader("""{"v":1}""", coerceBooleans = true)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(true, reader.nextBoolean())
    }

    @Test
    fun coercesIntZeroToBooleanFalse() {
        val reader = GhostJsonStringReader("""{"v":0}""", coerceBooleans = true)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(false, reader.nextBoolean())
    }

    @Test
    fun coercesStringTrueToBoolean() {
        val reader = GhostJsonStringReader("""{"v":"true"}""", coerceBooleans = true)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(true, reader.nextBoolean())
    }

    @Test
    fun coercesStringFalseToBoolean() {
        val reader = GhostJsonStringReader("""{"v":"false"}""", coerceBooleans = true)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(false, reader.nextBoolean())
    }

    @Test
    fun booleanWithoutCoercionRejectsInt() {
        val reader = readerOf("""{"v":1}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> { reader.nextBoolean() }
    }

    // ══════════════════════════════════════════════════════════════════
    // 22. String coercion to numbers
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun coercesQuotedIntToInt() {
        val reader = GhostJsonStringReader("""{"v":"42"}""", coerceStringsToNumbers = true)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(42, reader.nextInt())
    }

    @Test
    fun coercesQuotedLongToLong() {
        val reader = GhostJsonStringReader(
            """{"v":"${Long.MAX_VALUE}"}""",
            coerceStringsToNumbers = true
        )
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(Long.MAX_VALUE, reader.nextLong())
    }

    @Test
    fun coercesQuotedDoubleToDouble() {
        val reader = GhostJsonStringReader("""{"v":"3.14"}""", coerceStringsToNumbers = true)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(3.14, reader.nextDouble(), 0.001)
    }

    @Test
    fun numberCoercionWithoutFlagThrows() {
        val reader = readerOf("""{"v":"42"}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> { reader.nextInt() }
    }

    // ══════════════════════════════════════════════════════════════════
    // 23. hasNext / consumeArraySeparator in lenient mode
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun hasNextReturnsFalseForEmptyArray() {
        val reader = readerOf("[]")
        reader.beginArray()
        assertFalse(reader.hasNext())
    }

    @Test
    fun hasNextReturnsTrueForNonEmptyArray() {
        val reader = readerOf("[1]")
        reader.beginArray()
        assertTrue(reader.hasNext())
    }

    @Test
    fun hasNextConsumesSeparatorInLenientMode() {
        val reader = readerOf("[1,2,3]")
        reader.beginArray()
        assertTrue(reader.hasNext())
        reader.nextInt()
        assertTrue(reader.hasNext())
        reader.nextInt()
        assertTrue(reader.hasNext())
        reader.nextInt()
        assertFalse(reader.hasNext())
    }

    // ══════════════════════════════════════════════════════════════════
    // 24. Truncated JSON / malformations
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun truncatedStringValueThrowsOnRead() {
        val reader = readerOf("""{"id": 1, "name": "Ju""")
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        assertEquals(1, reader.nextInt())
        reader.consumeArraySeparator()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        assertFailsWith<Exception> { reader.nextString() }
    }

    @Test
    fun leadingZeroInIntegerThrows() {
        val reader = readerOf("""{"v":007}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> { reader.nextInt() }
    }

    @Test
    fun isolatedMinusThrows() {
        val reader = readerOf("""{"v":-}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<Exception> { reader.nextInt() }
    }
}

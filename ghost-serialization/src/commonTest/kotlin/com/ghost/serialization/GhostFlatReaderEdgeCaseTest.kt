package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.GhostJsonConstants
import com.ghost.serialization.parser.GhostJsonFlatReader
import com.ghost.serialization.parser.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertNull

@OptIn(InternalGhostApi::class)
class GhostFlatReaderEdgeCaseTest {

    private fun readerOf(json: String): GhostJsonFlatReader {
        return GhostJsonFlatReader(json.encodeToByteArray())
    }

    // ── A. NUMERIC HELL ──────────────────────────────────────────────

    @Test
    fun readsLongMaxValue() {
        val reader = readerOf("{\"v\":${Long.MAX_VALUE}}")
        reader.beginObject()
        reader.skipWhitespace() // or nextNonWhitespace
        reader.readQuotedString()
        reader.consumeKeySeparator()
        assertEquals(Long.MAX_VALUE, reader.nextLong())
    }

    @Test
    fun readsLongMinValue() {
        val reader = readerOf("{\"v\":${Long.MIN_VALUE}}")
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        assertEquals(Long.MIN_VALUE, reader.nextLong())
    }

    @Test
    fun readsZeroInt() {
        val reader = readerOf("{\"v\":0}")
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        assertEquals(0, reader.nextInt())
    }

    @Test
    fun readsNegativeInt() {
        val reader = readerOf("{\"v\":-42}")
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        assertEquals(-42, reader.nextInt())
    }

    @Test
    fun readsScientificNotationPositiveExponent() {
        val reader = readerOf("{\"v\":1e10}")
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        assertEquals(1e10, reader.nextDouble(), 0.1)
    }

    @Test
    fun readsScientificNotationNegativeExponent() {
        val reader = readerOf("{\"v\":1.23e-4}")
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        assertEquals(1.23e-4, reader.nextDouble(), 1e-10)
    }

    @Test
    fun readsScientificNotationUppercaseE() {
        val reader = readerOf("{\"v\":1.0E+2}")
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        assertEquals(100.0, reader.nextDouble(), 0.01)
    }

    @Test
    fun readsDoublePrecision() {
        val reader = readerOf("{\"v\":1.234567890123456}")
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        assertEquals(1.234567890123456, reader.nextDouble(), 1e-15)
    }

    @Test
    fun intOverflowThrowsException() {
        val reader = readerOf("{\"v\":${Long.MAX_VALUE}}")
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> { reader.nextInt() }
    }

    @Test
    fun longOverflowNegativeThrowsException() {
        val reader = readerOf("{\"v\":-92233720368547758089}")
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> { reader.nextLong() }
    }

    // ── B. MALFORMATIONS & DoS ───────────────────────────────────────

    @Test
    fun deepNestingRespectsMaxDepthLimit() {
        val deepJson = "[".repeat(300) + "]".repeat(300)
        val reader = readerOf(deepJson)
        assertFailsWith<GhostJsonException> {
            repeat(300) { reader.beginArray() }
        }
    }

    @Test
    fun truncatedJsonThrowsOnRead() {
        val reader = readerOf("{\"id\": 1, \"name\": \"Ju")
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
        val reader = readerOf("{\"a\":[]}")
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        reader.beginArray()
        reader.endArray()
        reader.endObject()
    }

    // ── C. STRINGS & UNICODE ─────────────────────────────────────────

    @Test
    fun readsSimpleEscapes() {
        val reader = readerOf("{\"v\":\"line1\\nline2\\ttab\"}")
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        assertEquals("line1\nline2\ttab", reader.nextString())
    }

    @Test
    fun readsBackslashAndQuoteEscapes() {
        val reader = readerOf("{\"v\":\"back\\\\slash and \\\"quotes\\\"\"}")
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        assertEquals("back\\slash and \"quotes\"", reader.nextString())
    }

    @Test
    fun readsAllRfcEscapes() {
        val reader = readerOf("{\"v\":\"\\b\\f\\n\\r\\t\"}")
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        assertEquals("\b\u000C\n\r\t", reader.nextString())
    }

    @Test
    fun readsUnicodeEscape() {
        val reader = readerOf("{\"v\":\"\\u0041\"}")
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        assertEquals("A", reader.nextString())
    }

    @Test
    fun readsUnicodeCharactersDirectly() {
        val reader = readerOf("{\"v\":\"漢字テスト\"}")
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        assertEquals("漢字テスト", reader.nextString())
    }

    @Test
    fun readsEmptyString() {
        val reader = readerOf("{\"v\":\"\"}")
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        assertEquals("", reader.nextString())
    }

    @Test
    fun readsEmojiString() {
        val emoji = "🚀🔥"
        val reader = readerOf("{\"v\":\"$emoji\"}")
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        assertEquals(emoji, reader.nextString())
    }

    // ── D. BOOLEANS & NULL ───────────────────────────────────────────

    @Test
    fun readsTrueBoolean() {
        val reader = readerOf("{\"v\":true}")
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        assertEquals(true, reader.nextBoolean())
    }

    @Test
    fun readsFalseBoolean() {
        val reader = readerOf("{\"v\":false}")
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        assertEquals(false, reader.nextBoolean())
    }

    @Test
    fun detectsNullToken() {
        val reader = readerOf("{\"v\":null}")
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        assertTrue(reader.isNextNullValue())
        reader.consumeNull()
    }

    // ── E. WHITESPACE RESILIENCE ─────────────────────────────────────

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

    // ── F. TOKEN PEEK ────────────────────────────────────────────────

    @Test
    fun peeksObjectToken() {
        val reader = readerOf("{}")
        assertEquals(GhostJsonConstants.OPEN_OBJ.toInt(), reader.peekNextToken())
    }

    @Test
    fun peeksArrayToken() {
        val reader = readerOf("[]")
        assertEquals(GhostJsonConstants.OPEN_ARR.toInt(), reader.peekNextToken())
    }

    @Test
    fun peeksStringToken() {
        val reader = readerOf("\"hello\"")
        assertEquals(GhostJsonConstants.QUOTE.toInt(), reader.peekNextToken())
    }

    @Test
    fun peeksNumberToken() {
        val reader = readerOf("42")
        assertEquals('4'.code, reader.peekNextToken())
    }

    @Test
    fun peeksBooleanToken() {
        val reader = readerOf("true")
        assertEquals(GhostJsonConstants.TRUE_CHAR.toInt(), reader.peekNextToken())
    }

    @Test
    fun peeksNullToken() {
        val reader = readerOf("null")
        assertEquals(GhostJsonConstants.NULL_CHAR.toInt(), reader.peekNextToken())
    }

    @Test
    fun peeksEndDocument() {
        val reader = readerOf("")
        assertEquals(-1, reader.peekNextToken())
    }

    // ── G. POOL RESET AND REUSE ──────────────────────────────────────

    @Test
    fun testPooledResetAndReuseWithDifferentSizes() {
        val initialJson = "{\"short\":1}"
        val reader = GhostJsonFlatReader(initialJson.encodeToByteArray())

        // 1. Verify first parse works
        reader.beginObject()
        reader.skipWhitespace()
        assertEquals("short", reader.readQuotedString())
        reader.consumeKeySeparator()
        assertEquals(1, reader.nextInt())
        reader.endObject()

        // 2. Reset with a much longer JSON string to verify limit and rawData updates
        val longerJson = "{\"very_long_field_name_indeed\":1234567890123}"
        reader.reset(longerJson.encodeToByteArray())

        reader.beginObject()
        reader.skipWhitespace()
        assertEquals("very_long_field_name_indeed", reader.readQuotedString())
        reader.consumeKeySeparator()
        assertEquals(1234567890123L, reader.nextLong())
        reader.endObject()

        // 3. Reset with a very short JSON string to verify bounds
        val shortJson = "{\"a\":true}"
        reader.reset(shortJson.encodeToByteArray())

        reader.beginObject()
        reader.skipWhitespace()
        assertEquals("a", reader.readQuotedString())
        reader.consumeKeySeparator()
        assertEquals(true, reader.nextBoolean())
        reader.endObject()
    }

    @Test
    fun testResilientDecoderDepthLeak() {
        val json = "{\"v\":{\"nested\": 42}}"
        val reader = readerOf(json)
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        assertEquals(1, reader.depth)
        
        // Try parsing nested object's int value as a string (throws non-structural exception)
        val result = reader.decodeResilient {
            reader.beginObject()
            reader.skipWhitespace()
            reader.readQuotedString()
            reader.consumeKeySeparator()
            reader.nextString()
            reader.endObject()
        }
        assertNull(result)
        assertEquals(1, reader.depth)
    }

    @Test
    fun testSurrogateBoundaryCheck() {
        val json = "{\"v\":\"abc\\uD83D\"}"
        val reader = readerOf(json)
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> { reader.nextString() }
    }

    @Test
    fun testPoolTierCollision() {
        val scratch = acquireScratchBuffer(48)
        val small = acquireScratchBuffer(1024)
        assertEquals(48, scratch.size)
        assertEquals(1024, small.size)
        
        releaseScratchBuffer(scratch)
        releaseScratchBuffer(small)
        
        val scratch2 = acquireScratchBuffer(48)
        val small2 = acquireScratchBuffer(1024)
        assertEquals(48, scratch2.size)
        assertEquals(1024, small2.size)
    }

    @Test
    fun testStrictCommaEnforcement() {
        val reader1 = readerOf("{\"a\": 1 \"b\": 2}")
        reader1.strictMode = true
        reader1.beginObject()
        assertEquals("a", reader1.nextKey())
        reader1.consumeKeySeparator()
        assertEquals(1, reader1.nextInt())
        assertFailsWith<GhostJsonException> { reader1.nextKey() }

        val reader2 = readerOf("[1 2 3]")
        reader2.strictMode = true
        reader2.beginArray()
        assertEquals(1, reader2.nextInt())
        assertFailsWith<GhostJsonException> { reader2.consumeArraySeparator() }
    }

    @Test
    fun testLeadingZeroValidationCorrectness() {
        val reader = readerOf("0p")
        assertEquals(0, reader.nextInt())
    }
}

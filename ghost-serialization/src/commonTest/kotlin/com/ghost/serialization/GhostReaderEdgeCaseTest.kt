package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginArray
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.consumeArraySeparator
import com.ghost.serialization.parser.streaming.consumeKeySeparator
import com.ghost.serialization.parser.streaming.consumeNull
import com.ghost.serialization.parser.streaming.decodeResilient
import com.ghost.serialization.parser.streaming.endArray
import com.ghost.serialization.parser.streaming.endObject
import com.ghost.serialization.parser.streaming.isNextNullValue
import com.ghost.serialization.parser.streaming.nextBoolean
import com.ghost.serialization.parser.streaming.nextDouble
import com.ghost.serialization.parser.streaming.nextInt
import com.ghost.serialization.parser.streaming.nextKey
import com.ghost.serialization.parser.streaming.nextLong
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.parser.streaming.readList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

@OptIn(InternalGhostApi::class)
class GhostReaderEdgeCaseTest {

    private fun readerOf(json: String): GhostJsonReader {
        return GhostJsonReader(json.encodeToByteArray())
    }

    // ── A. NUMERIC HELL ──────────────────────────────────────────────

    @Test
    fun readsLongMaxValue() {
        val reader = readerOf("{\"v\":${Long.MAX_VALUE}}")
        reader.beginObject()
        reader.nextKey().unused()
        reader.consumeKeySeparator()
        assertEquals(
            expected = Long.MAX_VALUE,
            actual = reader.nextLong()
        )
    }

    @Test
    fun readsLongMinValue() {
        val reader = readerOf("{\"v\":${Long.MIN_VALUE}}")
        reader.beginObject()
        reader.nextKey().unused()
        reader.consumeKeySeparator()
        assertEquals(
            expected = Long.MIN_VALUE,
            actual = reader.nextLong()
        )
    }

    @Test
    fun readsZeroInt() {
        val reader = readerOf("{\"v\":0}")
        reader.beginObject()
        reader.nextKey().unused()
        reader.consumeKeySeparator()
        assertEquals(
            expected = 0,
            actual = reader.nextInt()
        )
    }

    @Test
    fun readsNegativeInt() {
        val reader = readerOf("{\"v\":-42}")
        reader.beginObject()
        reader.nextKey().unused()
        reader.consumeKeySeparator()
        assertEquals(
            expected = -42,
            actual = reader.nextInt()
        )
    }

    @Test
    fun readsScientificNotationPositiveExponent() {
        val reader = readerOf("{\"v\":1e10}")
        reader.beginObject()
        reader.nextKey().unused()
        reader.consumeKeySeparator()
        assertEquals(
            expected = 1e10,
            actual = reader.nextDouble(),
            absoluteTolerance = 0.1
        )
    }

    @Test
    fun readsScientificNotationNegativeExponent() {
        val reader = readerOf("{\"v\":1.23e-4}")
        reader.beginObject()
        reader.nextKey().unused()
        reader.consumeKeySeparator()
        assertEquals(
            expected = 1.23e-4,
            actual = reader.nextDouble(),
            absoluteTolerance = 1e-10
        )
    }

    @Test
    fun readsScientificNotationUppercaseE() {
        val reader = readerOf("{\"v\":1.0E+2}")
        reader.beginObject()
        reader.nextKey().unused()
        reader.consumeKeySeparator()
        assertEquals(
            expected = 100.0,
            actual = reader.nextDouble(),
            absoluteTolerance = 0.01
        )
    }

    @Test
    fun readsDoublePrecision() {
        val reader = readerOf("{\"v\":1.234567890123456}")
        reader.beginObject()
        reader.nextKey().unused()
        reader.consumeKeySeparator()
        assertEquals(
            expected = 1.234567890123456,
            actual = reader.nextDouble(),
            absoluteTolerance = 1e-15
        )
    }

    @Test
    fun intOverflowThrowsException() {
        val reader = readerOf("{\"v\":${Long.MAX_VALUE}}")
        reader.beginObject()
        reader.nextKey().unused()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> { reader.nextInt() }
    }

    @Test
    fun longOverflowNegativeThrowsException() {
        val reader = readerOf("{\"v\":-92233720368547758089}")
        reader.beginObject()
        reader.nextKey().unused()
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
        reader.nextKey().unused()
        reader.consumeKeySeparator()
        assertEquals(
            expected = 1,
            actual = reader.nextInt()
        )
        reader.consumeArraySeparator()
        reader.nextKey().unused()
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
        reader.nextKey().unused()
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
        reader.nextKey().unused()
        reader.consumeKeySeparator()
        assertEquals(
            expected = "line1\nline2\ttab",
            actual = reader.nextString()
        )
    }

    @Test
    fun readsBackslashAndQuoteEscapes() {
        val reader = readerOf("{\"v\":\"back\\\\slash and \\\"quotes\\\"\"}")
        reader.beginObject()
        reader.nextKey().unused()
        reader.consumeKeySeparator()
        assertEquals(
            expected = "back\\slash and \"quotes\"",
            actual = reader.nextString()
        )
    }

    @Test
    fun readsAllRfcEscapes() {
        val reader = readerOf("{\"v\":\"\\b\\f\\n\\r\\t\"}")
        reader.beginObject()
        reader.nextKey().unused()
        reader.consumeKeySeparator()
        assertEquals(
            expected = "\b\u000C\n\r\t",
            actual = reader.nextString()
        )
    }

    @Test
    fun readsUnicodeEscape() {
        val reader = readerOf("{\"v\":\"\\u0041\"}")
        reader.beginObject()
        reader.nextKey().unused()
        reader.consumeKeySeparator()
        assertEquals(
            expected = "A",
            actual = reader.nextString()
        )
    }

    @Test
    fun readsUnicodeCharactersDirectly() {
        val reader = readerOf("{\"v\":\"漢字テスト\"}")
        reader.beginObject()
        reader.nextKey().unused()
        reader.consumeKeySeparator()
        assertEquals(
            expected = "漢字テスト",
            actual = reader.nextString()
        )
    }

    @Test
    fun readsEmptyString() {
        val reader = readerOf("{\"v\":\"\"}")
        reader.beginObject()
        reader.nextKey().unused()
        reader.consumeKeySeparator()
        assertEquals(
            expected = "",
            actual = reader.nextString()
        )
    }

    @Test
    fun readsEmojiString() {
        val emoji = "🚀🔥"
        val reader = readerOf("{\"v\":\"$emoji\"}")
        reader.beginObject()
        reader.nextKey().unused()
        reader.consumeKeySeparator()
        assertEquals(
            expected = emoji,
            actual = reader.nextString()
        )
    }

    // ── D. BOOLEANS & NULL ───────────────────────────────────────────

    @Test
    fun readsTrueBoolean() {
        val reader = readerOf("{\"v\":true}")
        reader.beginObject()
        reader.nextKey().unused()
        reader.consumeKeySeparator()
        assertEquals(
            expected = true,
            actual = reader.nextBoolean()
        )
    }

    @Test
    fun readsFalseBoolean() {
        val reader = readerOf("{\"v\":false}")
        reader.beginObject()
        reader.nextKey().unused()
        reader.consumeKeySeparator()
        assertEquals(
            expected = false,
            actual = reader.nextBoolean()
        )
    }

    @Test
    fun detectsNullToken() {
        val reader = readerOf("{\"v\":null}")
        reader.beginObject()
        reader.nextKey().unused()
        reader.consumeKeySeparator()
        assertTrue(actual = reader.isNextNullValue())
        reader.consumeNull()
    }

    // ── E. ARRAYS ────────────────────────────────────────────────────

    @Test
    fun readsArrayOfInts() {
        val reader = readerOf("[1,2,3]")
        val result = reader.readList { reader.nextInt() }
        assertEquals(
            expected = listOf(1, 2, 3),
            actual = result
        )
    }

    @Test
    fun readsArrayOfStrings() {
        val reader = readerOf("[\"a\",\"b\",\"c\"]")
        val result = reader.readList { reader.nextString() }
        assertEquals(
            expected = listOf("a", "b", "c"),
            actual = result
        )
    }

    @Test
    fun readsEmptyArray() {
        val reader = readerOf("[]")
        val result = reader.readList { reader.nextInt() }
        assertEquals(
            expected = emptyList(),
            actual = result
        )
    }

    // ── F. WHITESPACE RESILIENCE ─────────────────────────────────────

    @Test
    fun handlesExcessiveWhitespace() {
        val reader = readerOf("  {  \"v\"  :  42  }  ")
        reader.beginObject()
        reader.nextKey().unused()
        reader.consumeKeySeparator()
        assertEquals(
            expected = 42,
            actual = reader.nextInt()
        )
        reader.endObject()
    }

    @Test
    fun handlesNewlinesAndTabs() {
        val json = "{\n\t\"v\"\n\t:\n\t99\n}"
        val reader = readerOf(json)
        reader.beginObject()
        reader.nextKey().unused()
        reader.consumeKeySeparator()
        assertEquals(
            expected = 99,
            actual = reader.nextInt()
        )
        reader.endObject()
    }

    // ── G. TOKEN PEEK ────────────────────────────────────────────────

    @Test
    fun peeksObjectToken() {
        val reader = readerOf("{}")
        assertEquals(
            expected = TOK.OPEN_OBJ_INT,
            actual = reader.peekNextToken()
        )
    }

    @Test
    fun peeksArrayToken() {
        val reader = readerOf("[]")
        assertEquals(
            expected = TOK.OPEN_ARR_INT,
            actual = reader.peekNextToken()
        )
    }

    @Test
    fun peeksStringToken() {
        val reader = readerOf("\"hello\"")
        assertEquals(
            expected = TOK.QUOTE_INT,
            actual = reader.peekNextToken()
        )
    }

    @Test
    fun peeksNumberToken() {
        val reader = readerOf("42")
        assertEquals(
            expected = '4'.code,
            actual = reader.peekNextToken()
        )
    }

    @Test
    fun peeksBooleanToken() {
        val reader = readerOf("true")
        assertEquals(
            expected = TOK.TRUE_CHAR_INT,
            actual = reader.peekNextToken()
        )
    }

    @Test
    fun peeksNullToken() {
        val reader = readerOf("null")
        assertEquals(
            expected = TOK.NULL_CHAR_INT,
            actual = reader.peekNextToken()
        )
    }

    @Test
    fun peeksEndDocument() {
        val reader = readerOf("")
        assertEquals(
            expected = -1,
            actual = reader.peekNextToken()
        )
    }

    @Test
    fun testResilientDecoderDepthLeak() {
        val json = "{\"v\":{\"nested\": 42}}"
        val reader = readerOf(json)
        reader.beginObject()
        reader.nextKey().unused()
        reader.consumeKeySeparator()
        assertEquals(
            expected = 1,
            actual = reader.depth
        )

        // Try parsing nested object's int value as a string (throws non-structural exception)
        val result = reader.decodeResilient {
            reader.beginObject()
            reader.nextKey().unused()
            reader.consumeKeySeparator()
            reader.nextString()
            reader.endObject()
        }
        assertNull(actual = result)
        // Depth should be restored to 1 even after exception
        assertEquals(
            expected = 1,
            actual = reader.depth
        )
    }

    @Test
    fun testSurrogateBoundaryCheck() {
        // High surrogate \uD83D without low surrogate at the end of string
        val json = "{\"v\":\"abc\\uD83D\"}"
        val reader = readerOf(json)
        reader.beginObject()
        reader.nextKey().unused()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> { reader.nextString() }
    }

    @Test
    fun testPoolTierCollision() {
        val scratch = acquireScratchBuffer(minSize = 48)
        val small = acquireScratchBuffer(minSize = 1024)
        assertEquals(
            expected = 48,
            actual = scratch.size
        )
        assertEquals(
            expected = 1024,
            actual = small.size
        )

        releaseScratchBuffer(buffer = scratch)
        releaseScratchBuffer(buffer = small)

        val scratch2 = acquireScratchBuffer(minSize = 48)
        val small2 = acquireScratchBuffer(minSize = 1024)
        assertEquals(
            expected = 48,
            actual = scratch2.size
        )
        assertEquals(
            expected = 1024,
            actual = small2.size
        )
    }

    @Test
    fun testStrictCommaEnforcement() {
        val reader1 = readerOf("{\"a\": 1 \"b\": 2}")
        reader1.strictMode = true
        reader1.beginObject()
        assertEquals(
            expected = "a",
            actual = reader1.nextKey()
        )
        reader1.consumeKeySeparator()
        assertEquals(
            expected = 1,
            actual = reader1.nextInt()
        )
        assertFailsWith<GhostJsonException> { reader1.nextKey() }

        val reader2 = readerOf("[1 2 3]")
        reader2.strictMode = true
        reader2.beginArray()
        assertEquals(
            expected = 1,
            actual = reader2.nextInt()
        )
        assertFailsWith<GhostJsonException> { reader2.consumeArraySeparator() }
    }

    @Test
    fun testLeadingZeroValidationCorrectness() {
        val reader = readerOf("0p")
        assertEquals(
            expected = 0,
            actual = reader.nextInt()
        )
    }
}

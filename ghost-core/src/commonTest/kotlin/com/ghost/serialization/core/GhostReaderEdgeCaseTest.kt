package com.ghost.serialization.core
import com.ghost.serialization.core.parser.Options

import com.ghost.serialization.core.parser.skipCommaIfPresent
import com.ghost.serialization.core.parser.nextNonWhitespace
import com.ghost.serialization.core.parser.skipAnyValue
import com.ghost.serialization.serializers.IntArraySerializer
import com.ghost.serialization.serializers.LongArraySerializer
import com.ghost.serialization.core.contract.GhostRegistry
import com.ghost.serialization.core.contract.GhostSerializer

import com.ghost.serialization.core.parser.GhostJsonReader
import com.ghost.serialization.core.writer.GhostJsonWriter
import com.ghost.serialization.core.exception.GhostJsonException
import com.ghost.serialization.core.parser.nextKey
import com.ghost.serialization.core.parser.consumeKeySeparator
import com.ghost.serialization.core.parser.isNextNullValue
import com.ghost.serialization.core.parser.skipValue
import com.ghost.serialization.core.parser.JsonToken
import com.ghost.serialization.core.parser.peekJsonToken
import com.ghost.serialization.core.parser.readList
import com.ghost.serialization.core.parser.nextInt
import com.ghost.serialization.core.parser.nextDouble
import com.ghost.serialization.core.parser.consumeArraySeparator
import com.ghost.serialization.core.parser.nextLong
import com.ghost.serialization.core.parser.nextFloat
import com.ghost.serialization.core.parser.consumeNull

import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GhostReaderEdgeCaseTest {

    private fun readerOf(json: String): GhostJsonReader {
        return GhostJsonReader(Buffer().writeUtf8(json))
    }

    // ── A. NUMERIC HELL ──────────────────────────────────────────────

    @Test
    fun readsLongMaxValue() {
        val reader = readerOf("{\"v\":${Long.MAX_VALUE}}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(Long.MAX_VALUE, reader.nextLong())
    }

    @Test
    fun readsLongMinValue() {
        val reader = readerOf("{\"v\":${Long.MIN_VALUE}}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(Long.MIN_VALUE, reader.nextLong())
    }

    @Test
    fun readsZeroInt() {
        val reader = readerOf("{\"v\":0}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(0, reader.nextInt())
    }

    @Test
    fun readsNegativeInt() {
        val reader = readerOf("{\"v\":-42}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(-42, reader.nextInt())
    }

    @Test
    fun readsScientificNotationPositiveExponent() {
        val reader = readerOf("{\"v\":1e10}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(1e10, reader.nextDouble(), 0.1)
    }

    @Test
    fun readsScientificNotationNegativeExponent() {
        val reader = readerOf("{\"v\":1.23e-4}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(1.23e-4, reader.nextDouble(), 1e-10)
    }

    @Test
    fun readsScientificNotationUppercaseE() {
        val reader = readerOf("{\"v\":1.0E+2}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(100.0, reader.nextDouble(), 0.01)
    }

    @Test
    fun readsDoublePrecision() {
        val reader = readerOf("{\"v\":1.234567890123456}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(1.234567890123456, reader.nextDouble(), 1e-15)
    }

    @Test
    fun intOverflowThrowsException() {
        val reader = readerOf("{\"v\":${Long.MAX_VALUE}}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> { reader.nextInt() }
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
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(1, reader.nextInt())
        reader.skipCommaIfPresent()
        reader.nextKey()
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
        reader.nextKey()
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
    fun readsUnicodeEscape() {
        val reader = readerOf("{\"v\":\"\\u0041\"}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals("A", reader.nextString())
    }

    @Test
    fun readsUnicodeCharactersDirectly() {
        val reader = readerOf("{\"v\":\"漢字テスト\"}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals("漢字テスト", reader.nextString())
    }

    @Test
    fun readsEmptyString() {
        val reader = readerOf("{\"v\":\"\"}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals("", reader.nextString())
    }

    @Test
    fun readsEmojiString() {
        val emoji = "🚀🔥"
        val reader = readerOf("{\"v\":\"$emoji\"}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(emoji, reader.nextString())
    }

    // ── D. BOOLEANS & NULL ───────────────────────────────────────────

    @Test
    fun readsTrueBoolean() {
        val reader = readerOf("{\"v\":true}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(true, reader.nextBoolean())
    }

    @Test
    fun readsFalseBoolean() {
        val reader = readerOf("{\"v\":false}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(false, reader.nextBoolean())
    }

    @Test
    fun detectsNullToken() {
        val reader = readerOf("{\"v\":null}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertTrue(reader.isNextNullValue())
        reader.consumeNull()
    }

    // ── E. ARRAYS ────────────────────────────────────────────────────

    @Test
    fun readsArrayOfInts() {
        val reader = readerOf("[1,2,3]")
        val result = reader.readList { reader.nextInt() }
        assertEquals(listOf(1, 2, 3), result)
    }

    @Test
    fun readsArrayOfStrings() {
        val reader = readerOf("[\"a\",\"b\",\"c\"]")
        val result = reader.readList { reader.nextString() }
        assertEquals(listOf("a", "b", "c"), result)
    }

    @Test
    fun readsEmptyArray() {
        val reader = readerOf("[]")
        val result = reader.readList { reader.nextInt() }
        assertEquals(emptyList(), result)
    }

    // ── F. WHITESPACE RESILIENCE ─────────────────────────────────────

    @Test
    fun handlesExcessiveWhitespace() {
        val reader = readerOf("  {  \"v\"  :  42  }  ")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(42, reader.nextInt())
        reader.endObject()
    }

    @Test
    fun handlesNewlinesAndTabs() {
        val json = "{\n\t\"v\"\n\t:\n\t99\n}"
        val reader = readerOf(json)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(99, reader.nextInt())
        reader.endObject()
    }

    // ── G. TOKEN PEEK ────────────────────────────────────────────────

    @Test
    fun peeksObjectToken() {
        val reader = readerOf("{}")
        assertEquals(JsonToken.BEGIN_OBJECT, reader.peekJsonToken())
    }

    @Test
    fun peeksArrayToken() {
        val reader = readerOf("[]")
        assertEquals(JsonToken.BEGIN_ARRAY, reader.peekJsonToken())
    }

    @Test
    fun peeksStringToken() {
        val reader = readerOf("\"hello\"")
        assertEquals(JsonToken.STRING, reader.peekJsonToken())
    }

    @Test
    fun peeksNumberToken() {
        val reader = readerOf("42")
        assertEquals(JsonToken.NUMBER, reader.peekJsonToken())
    }

    @Test
    fun peeksBooleanToken() {
        val reader = readerOf("true")
        assertEquals(JsonToken.BOOLEAN, reader.peekJsonToken())
    }

    @Test
    fun peeksNullToken() {
        val reader = readerOf("null")
        assertEquals(JsonToken.NULL, reader.peekJsonToken())
    }

    @Test
    fun peeksEndDocument() {
        val reader = readerOf("")
        assertEquals(JsonToken.END_DOCUMENT, reader.peekJsonToken())
    }
}

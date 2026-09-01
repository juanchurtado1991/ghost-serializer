@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.common.GhostJsonConstants
import com.ghost.serialization.parser.common.JsonReaderOptions
import com.ghost.serialization.parser.bytes.readQuotedString
import com.ghost.serialization.parser.proto.GhostProtoJsonFlatReader
import com.ghost.serialization.parser.streaming.beginArray
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.consumeArraySeparator
import com.ghost.serialization.parser.streaming.consumeKeySeparator
import com.ghost.serialization.parser.streaming.endArray
import com.ghost.serialization.parser.streaming.endObject
import com.ghost.serialization.parser.streaming.nextBoolean
import com.ghost.serialization.parser.streaming.nextKey
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.parser.streaming.skipValue
import com.ghost.serialization.proto.protoReaderOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue


class GhostProtoFlatReaderEdgeCaseTest {

    // ── A. NUMERIC EDGE CASES ────────────────────────────────────────

    @Test
    fun quotedInt32Accepted() {
        val reader = protoReaderOf("""{"retries":"42"}""")
        reader.beginObject()
        assertEquals("retries", reader.nextKey())
        reader.consumeKeySeparator()
        assertEquals(42, reader.nextInt())
        reader.endObject()
    }

    @Test
    fun bareInt32Accepted() {
        val reader = protoReaderOf("""{"retries":42}""")
        reader.beginObject()
        assertEquals("retries", reader.nextKey())
        reader.consumeKeySeparator()
        assertEquals(42, reader.nextInt())
        reader.endObject()
    }

    @Test
    fun quotedInt32WithWholeFractionAccepted() {
        val reader = protoReaderOf("""{"retries":"1.0"}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(1, reader.nextInt())
        reader.endObject()
    }

    @Test
    fun quotedInt32WithFractionalPartRejected() {
        val reader = protoReaderOf("""{"retries":"1.5"}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> { reader.nextInt() }
    }

    @Test
    fun bareInt32WithFractionalPartRejected() {
        val reader = protoReaderOf("""{"retries":1.5}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> { reader.nextInt() }
    }

    @Test
    fun quotedInt64Accepted() {
        val reader = protoReaderOf("""{"deviceId":"9223372036854775807"}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(Long.MAX_VALUE, reader.nextLong())
        reader.endObject()
    }

    @Test
    fun bareInt64Accepted() {
        val reader = protoReaderOf("""{"deviceId":9223372036854775807}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(Long.MAX_VALUE, reader.nextLong())
        reader.endObject()
    }

    @Test
    fun truncatedInt64OverflowThrows() {
        val reader = protoReaderOf("""{"deviceId":"92233720368547758089"}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> { reader.nextLong() }
    }

    // ── B. BASE64 BYTES ──────────────────────────────────────────────

    @Test
    fun validBase64Decodes() {
        val reader = protoReaderOf("\"YWJjMTIzIT8kKiYoKSctPUB+\"")
        assertEquals("abc123!?$*&()'-=@~", reader.nextProtoBytes().decodeToString())
    }

    @Test
    fun invalidBase64CharacterThrows() {
        val reader = protoReaderOf("\"!!!not-base64!!!\"")
        assertFailsWith<GhostJsonException> { reader.nextProtoBytes() }
    }

    @Test
    fun emptyBase64StringDecodesToEmptyBytes() {
        val reader = protoReaderOf("\"\"")
        assertEquals(0, reader.nextProtoBytes().size)
    }

    // ── C. MALFORMATIONS & DoS ───────────────────────────────────────

    @Test
    fun deepNestingRespectsMaxDepthLimit() {
        val deepJson = "[".repeat(300) + "]".repeat(300)
        val reader = protoReaderOf(deepJson)
        assertFailsWith<GhostJsonException> {
            repeat(300) { reader.beginArray() }
        }
    }

    @Test
    fun truncatedJsonThrowsOnRead() {
        val reader = protoReaderOf("""{"id": 1, "name": "Ju""")
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
    fun malformedObjectMissingValueThrows() {
        val reader = protoReaderOf("""{ "k": }""")
        assertFailsWith<GhostJsonException> {
            reader.beginObject()
            reader.nextKey()
            reader.consumeKeySeparator()
            reader.nextInt()
        }
    }

    @Test
    fun malformedArrayTrailingCommaThrows() {
        val reader = protoReaderOf("[1, 2, ]")
        reader.beginArray()
        reader.nextInt()
        reader.consumeArraySeparator()
        reader.nextInt()
        assertFailsWith<GhostJsonException> { reader.endArray() }
    }

    @Test
    fun emptyObjectParsesSuccessfully() {
        val reader = protoReaderOf("{}")
        reader.beginObject()
        reader.endObject()
    }

    @Test
    fun emptyArrayParsesSuccessfully() {
        val reader = protoReaderOf("[]")
        reader.beginArray()
        reader.endArray()
    }

    // ── D. UNKNOWN FIELD SKIP ──────────────────────────────────────────

    @Test
    fun skipValueIgnoresUnknownNestedObject() {
        val reader = protoReaderOf("""{"known":"x","unknown":{"deep":1},"after":2}""")
        reader.beginObject()
        assertEquals("known", reader.nextKey())
        reader.consumeKeySeparator()
        assertEquals("x", reader.nextString())
        assertEquals("unknown", reader.nextKey())
        reader.consumeKeySeparator()
        reader.skipValue()
        assertEquals("after", reader.nextKey())
        reader.consumeKeySeparator()
        assertEquals(2, reader.nextInt())
        reader.endObject()
    }

    @Test
    fun skipValueIgnoresUnknownArray() {
        val reader = protoReaderOf("""{"known":1,"noise":[1,{"a":2},3]}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(1, reader.nextInt())
        reader.nextKey()
        reader.consumeKeySeparator()
        reader.skipValue()
        reader.endObject()
    }

    // ── E. ENUM & WHITESPACE ─────────────────────────────────────────

    @Test
    fun enumAcceptsQuotedNameAndBareNumber() {
        val options = JsonReaderOptions.of("UNKNOWN", "FOO", "BAR")
        val readerStr = protoReaderOf("\"BAR\"")
        assertEquals(2, readerStr.nextProtoEnum(options))

        val readerInt = protoReaderOf("1")
        assertEquals(1, readerInt.nextProtoEnum(options))
    }

    @Test
    fun handlesExcessiveWhitespace() {
        val reader = protoReaderOf("  {  \"v\"  :  42  }  ")
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        assertEquals(42, reader.nextInt())
        reader.endObject()
    }

    // ── F. POOL RESET AND REUSE ───────────────────────────────────────

    @Test
    fun resetReusesReaderWithDifferentPayloadSizes() {
        val reader = GhostProtoJsonFlatReader("""{"short":1}""".encodeToByteArray())
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(1, reader.nextInt())
        reader.endObject()

        val longerJson = """{"very_long_field_name_indeed":"9223372036854775807"}"""
        reader.reset(longerJson.encodeToByteArray())
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(Long.MAX_VALUE, reader.nextLong())
        reader.endObject()

        reader.reset("""{"flag":true}""".encodeToByteArray())
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertTrue(reader.nextBoolean())
        reader.endObject()
    }

    @Test
    fun peekNextTokenReportsStructure() {
        assertEquals(GhostJsonConstants.OPEN_OBJ_INT, protoReaderOf("{}").peekNextToken())
        assertEquals(GhostJsonConstants.OPEN_ARR_INT, protoReaderOf("[]").peekNextToken())
        assertEquals(GhostJsonConstants.QUOTE_INT, protoReaderOf("\"hello\"").peekNextToken())
    }
}

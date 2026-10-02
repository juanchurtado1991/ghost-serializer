@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.bytes.extensions.readQuotedString
import com.ghost.serialization.parser.common.json.JsonReaderOptions
import com.ghost.serialization.parser.proto.GhostProtoJsonFlatReader
import com.ghost.serialization.proto.protoReaderOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

class GhostProtoFlatReaderEdgeCaseTest {

    // ── A. NUMERIC EDGE CASES ────────────────────────────────────────

    @Test
    fun quotedInt32Accepted() {
        val reader = protoReaderOf(json = """{"retries":"42"}""")
        reader.beginObject()
        assertEquals(
            expected = "retries",
            actual = reader.nextKey()
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = 42,
            actual = reader.nextInt()
        )
        reader.endObject()
    }

    @Test
    fun bareInt32Accepted() {
        val reader = protoReaderOf(json = """{"retries":42}""")
        reader.beginObject()
        assertEquals(
            expected = "retries",
            actual = reader.nextKey()
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = 42,
            actual = reader.nextInt()
        )
        reader.endObject()
    }

    @Test
    fun quotedInt32WithWholeFractionAccepted() {
        val reader = protoReaderOf(json = """{"retries":"1.0"}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = 1,
            actual = reader.nextInt()
        )
        reader.endObject()
    }

    @Test
    fun quotedInt32WithFractionalPartRejected() {
        val reader = protoReaderOf(json = """{"retries":"1.5"}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> { reader.nextInt() }
    }

    @Test
    fun bareInt32WithFractionalPartRejected() {
        val reader = protoReaderOf(json = """{"retries":1.5}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> { reader.nextInt() }
    }

    @Test
    fun quotedInt64Accepted() {
        val reader = protoReaderOf(json = """{"deviceId":"9223372036854775807"}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = Long.MAX_VALUE,
            actual = reader.nextLong()
        )
        reader.endObject()
    }

    @Test
    fun bareInt64Accepted() {
        val reader = protoReaderOf(json = """{"deviceId":9223372036854775807}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = Long.MAX_VALUE,
            actual = reader.nextLong()
        )
        reader.endObject()
    }

    @Test
    fun truncatedInt64OverflowThrows() {
        val reader = protoReaderOf(json = """{"deviceId":"92233720368547758089"}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> { reader.nextLong() }
    }

    // ── B. BASE64 BYTES ──────────────────────────────────────────────

    @Test
    fun validBase64Decodes() {
        val reader = protoReaderOf(json = "\"YWJjMTIzIT8kKiYoKSctPUB+\"")
        assertEquals(
            expected = "abc123!?$*&()'-=@~",
            actual = reader.nextProtoBytes().decodeToString()
        )
    }

    @Test
    fun invalidBase64CharacterThrows() {
        val reader = protoReaderOf(json = "\"!!!not-base64!!!\"")
        assertFailsWith<GhostJsonException> { reader.nextProtoBytes() }
    }

    @Test
    fun emptyBase64StringDecodesToEmptyBytes() {
        val reader = protoReaderOf(json = "\"\"")
        assertEquals(
            expected = 0,
            actual = reader.nextProtoBytes().size
        )
    }

    // ── C. MALFORMATIONS & DoS ───────────────────────────────────────

    @Test
    fun deepNestingRespectsMaxDepthLimit() {
        val deepJson = "[".repeat(300) + "]".repeat(300)
        val reader = protoReaderOf(json = deepJson)
        assertFailsWith<GhostJsonException> {
            repeat(300) { reader.beginArray() }
        }
    }

    @Test
    fun truncatedJsonThrowsOnRead() {
        val reader = protoReaderOf(json = """{"id": 1, "name": "Ju""")
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        assertEquals(
            expected = 1,
            actual = reader.nextInt()
        )
        reader.consumeArraySeparator()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        assertFailsWith<Exception> { reader.nextString() }
    }

    @Test
    fun malformedObjectMissingValueThrows() {
        val reader = protoReaderOf(json = """{ "k": }""")
        assertFailsWith<GhostJsonException> {
            reader.beginObject()
            reader.nextKey()
            reader.consumeKeySeparator()
            reader.nextInt()
        }
    }

    @Test
    fun malformedArrayTrailingCommaThrows() {
        val reader = protoReaderOf(json = "[1, 2, ]")
        reader.beginArray()
        reader.nextInt()
        reader.consumeArraySeparator()
        reader.nextInt()
        assertFailsWith<GhostJsonException> { reader.endArray() }
    }

    @Test
    fun emptyObjectParsesSuccessfully() {
        val reader = protoReaderOf(json = "{}")
        reader.beginObject()
        reader.endObject()
    }

    @Test
    fun emptyArrayParsesSuccessfully() {
        val reader = protoReaderOf(json = "[]")
        reader.beginArray()
        reader.endArray()
    }

    // ── D. UNKNOWN FIELD SKIP ──────────────────────────────────────────

    @Test
    fun skipValueIgnoresUnknownNestedObject() {
        val reader = protoReaderOf(json = """{"known":"x","unknown":{"deep":1},"after":2}""")
        reader.beginObject()
        assertEquals(
            expected = "known",
            actual = reader.nextKey()
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = "x",
            actual = reader.nextString()
        )
        assertEquals(
            expected = "unknown",
            actual = reader.nextKey()
        )
        reader.consumeKeySeparator()
        reader.skipValue()
        assertEquals(
            expected = "after",
            actual = reader.nextKey()
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = 2,
            actual = reader.nextInt()
        )
        reader.endObject()
    }

    @Test
    fun skipValueIgnoresUnknownArray() {
        val reader = protoReaderOf(json = """{"known":1,"noise":[1,{"a":2},3]}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = 1,
            actual = reader.nextInt()
        )
        reader.nextKey()
        reader.consumeKeySeparator()
        reader.skipValue()
        reader.endObject()
    }

    // ── E. ENUM & WHITESPACE ─────────────────────────────────────────

    @Test
    fun enumAcceptsQuotedNameAndBareNumber() {
        val options = JsonReaderOptions.of("UNKNOWN", "FOO", "BAR")
        val readerStr = protoReaderOf(json = "\"BAR\"")
        assertEquals(
            expected = 2,
            actual = readerStr.nextProtoEnum(options = options)
        )

        val readerInt = protoReaderOf(json = "1")
        assertEquals(
            expected = 1,
            actual = readerInt.nextProtoEnum(options = options)
        )
    }

    @Test
    fun handlesExcessiveWhitespace() {
        val reader = protoReaderOf(json = "  {  \"v\"  :  42  }  ")
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        assertEquals(
            expected = 42,
            actual = reader.nextInt()
        )
        reader.endObject()
    }

    // ── F. POOL RESET AND REUSE ───────────────────────────────────────

    @Test
    fun resetReusesReaderWithDifferentPayloadSizes() {
        val reader = GhostProtoJsonFlatReader(rawData = """{"short":1}""".encodeToByteArray())
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = 1,
            actual = reader.nextInt()
        )
        reader.endObject()

        val longerJson = """{"very_long_field_name_indeed":"9223372036854775807"}"""
        reader.reset(longerJson.encodeToByteArray())
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = Long.MAX_VALUE,
            actual = reader.nextLong()
        )
        reader.endObject()

        reader.reset("""{"flag":true}""".encodeToByteArray())
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertTrue(actual = reader.nextBoolean())
        reader.endObject()
    }

    @Test
    fun peekNextTokenReportsStructure() {
        assertEquals(
            expected = TOK.OPEN_OBJ_INT,
            actual = protoReaderOf(json = "{}").peekNextToken()
        )
        assertEquals(
            expected = TOK.OPEN_ARR_INT,
            actual = protoReaderOf(json = "[]").peekNextToken()
        )
        assertEquals(
            expected = TOK.QUOTE_INT,
            actual = protoReaderOf(json = "\"hello\"").peekNextToken()
        )
    }
}

@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.common.json.JsonReaderOptions
import com.ghost.serialization.parser.common.byteToCharPosition
import com.ghost.serialization.parser.common.charToBytePosition
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.beginArray
import com.ghost.serialization.parser.strings.beginObject
import com.ghost.serialization.parser.strings.consumeArraySeparator
import com.ghost.serialization.parser.strings.consumeKeySeparator
import com.ghost.serialization.parser.strings.endArray
import com.ghost.serialization.parser.strings.endObject
import com.ghost.serialization.parser.strings.hasNext
import com.ghost.serialization.parser.strings.nextBoolean
import com.ghost.serialization.parser.strings.nextInt
import com.ghost.serialization.parser.strings.nextKey
import com.ghost.serialization.parser.strings.nextString
import com.ghost.serialization.parser.strings.readList
import com.ghost.serialization.parser.strings.readQuotedString
import com.ghost.serialization.parser.strings.selectString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants as SCN
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

/** [GhostJsonStringReader] contract: structure, depth, stream exhaustion, and malformed input. */
@OptIn(InternalGhostApi::class)
class GhostStringReaderStructureTest {

    // ══════════════════════════════════════════════════════════════════
    // Whitespace-only input
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun whitespaceOnlyInputReportsEndDocument() {
        val reader = stringReaderOf(json = "   \n\t  ")
        assertEquals(
            expected = SCN.MATCH_END,
            actual = reader.peekNextToken()
        )
    }

    @Test
    fun emptyStringInputReportsEndDocument() {
        val reader = stringReaderOf(json = "")
        assertEquals(
            expected = SCN.MATCH_END,
            actual = reader.peekNextToken()
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // Stream exhaustion mid-parse
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun exhaustedSourceDuringObjectParseFails() {
        val reader = stringReaderOf(json = "{")
        reader.beginObject()
        assertFailsWith<Exception> { reader.nextKey() }
    }

    @Test
    fun exhaustedSourceDuringStringFails() {
        val reader = stringReaderOf(json = "{\"v\":\"unterminated")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<Exception> { reader.nextString() }
    }

    @Test
    fun exhaustedSourceDuringArrayFails() {
        val reader = stringReaderOf(json = "[1,2,")
        assertFailsWith<Exception> {
            reader.readList { reader.nextInt() }
        }
    }

    @Test
    fun unmatchedOpenBraceThrowsOnEnd() {
        val reader = stringReaderOf(json = """{"v":1""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        reader.nextInt()
        assertFailsWith<GhostJsonException> { reader.endObject() }
    }

    // ══════════════════════════════════════════════════════════════════
    // Depth tracking
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun depthIncreasesAndDecreasesCorrectly() {
        val reader = stringReaderOf(json = """{"a":{"b":[1]}}""")
        assertEquals(
            expected = 0,
            actual = reader.depth
        )
        reader.beginObject()
        assertEquals(
            expected = 1,
            actual = reader.depth
        )
        reader.nextKey()
        reader.consumeKeySeparator()
        reader.beginObject()
        assertEquals(
            expected = 2,
            actual = reader.depth
        )
        reader.nextKey()
        reader.consumeKeySeparator()
        reader.beginArray()
        assertEquals(
            expected = 3,
            actual = reader.depth
        )
        reader.nextInt()
        reader.endArray()
        assertEquals(
            expected = 2,
            actual = reader.depth
        )
        reader.endObject()
        assertEquals(
            expected = 1,
            actual = reader.depth
        )
        reader.endObject()
        assertEquals(
            expected = 0,
            actual = reader.depth
        )
    }

    @Test
    fun deepNestingRespectsMaxDepthLimit() {
        val deepJson = "[".repeat(300) + "]".repeat(300)
        val reader = stringReaderOf(json = deepJson)
        assertFailsWith<GhostJsonException> {
            repeat(300) { reader.beginArray() }
        }
    }

    @Test
    fun customMaxDepthIsEnforced() {
        val reader = GhostJsonStringReader(rawData = "[[[]]]", maxDepth = 2)
        reader.beginArray()
        reader.beginArray()
        assertFailsWith<GhostJsonException> {
            reader.beginArray()
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Token peek
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun peeksObjectToken() {
        val reader = stringReaderOf(json = "{}")
        assertEquals(
            expected = TOK.OPEN_OBJ_INT,
            actual = reader.peekNextToken()
        )
    }

    @Test
    fun peeksArrayToken() {
        val reader = stringReaderOf(json = "[]")
        assertEquals(
            expected = TOK.OPEN_ARR_INT,
            actual = reader.peekNextToken()
        )
    }

    @Test
    fun peeksStringToken() {
        val reader = stringReaderOf(json = "\"hello\"")
        assertEquals(
            expected = TOK.QUOTE_INT,
            actual = reader.peekNextToken()
        )
    }

    @Test
    fun peeksNumberToken() {
        val reader = stringReaderOf(json = "42")
        assertEquals(
            expected = '4'.code,
            actual = reader.peekNextToken()
        )
    }

    @Test
    fun peeksBooleanTrueToken() {
        val reader = stringReaderOf(json = "true")
        assertEquals(
            expected = TOK.TRUE_CHAR_INT,
            actual = reader.peekNextToken()
        )
    }

    @Test
    fun peeksBooleanFalseToken() {
        val reader = stringReaderOf(json = "false")
        assertEquals(
            expected = TOK.FALSE_CHAR_INT,
            actual = reader.peekNextToken()
        )
    }

    @Test
    fun peeksNullToken() {
        val reader = stringReaderOf(json = "null")
        assertEquals(
            expected = TOK.NULL_CHAR_INT,
            actual = reader.peekNextToken()
        )
    }

    @Test
    fun peeksEndOfDocument() {
        val reader = stringReaderOf(json = "")
        assertEquals(
            expected = SCN.MATCH_END,
            actual = reader.peekNextToken()
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // Structural edge cases
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun emptyObjectParsesSuccessfully() {
        val reader = stringReaderOf(json = "{}")
        reader.beginObject()
        reader.endObject()
    }

    @Test
    fun emptyArrayParsesSuccessfully() {
        val reader = stringReaderOf(json = "[]")
        reader.beginArray()
        reader.endArray()
    }

    @Test
    fun nestedEmptyStructures() {
        val reader = stringReaderOf(json = """{"a":[]}""")
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
        val reader = stringReaderOf(json = """{"only":true}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertTrue(actual = reader.nextBoolean())
        reader.endObject()
    }

    @Test
    fun handlesExcessiveWhitespace() {
        val reader = stringReaderOf(json = "  {  \"v\"  :  42  }  ")
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

    @Test
    fun handlesNewlinesAndTabs() {
        val json = "{\n\t\"v\"\n\t:\n\t99\n}"
        val reader = stringReaderOf(json = json)
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        assertEquals(
            expected = 99,
            actual = reader.nextInt()
        )
        reader.endObject()
    }

    // ══════════════════════════════════════════════════════════════════
    // hasNext / consumeArraySeparator in lenient mode
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun hasNextReturnsFalseForEmptyArray() {
        val reader = stringReaderOf(json = "[]")
        reader.beginArray()
        assertFalse(actual = reader.hasNext())
    }

    @Test
    fun hasNextReturnsTrueForNonEmptyArray() {
        val reader = stringReaderOf(json = "[1]")
        reader.beginArray()
        assertTrue(actual = reader.hasNext())
    }

    @Test
    fun hasNextConsumesSeparatorInLenientMode() {
        val reader = stringReaderOf(json = "[1,2,3]")
        reader.beginArray()
        assertTrue(actual = reader.hasNext())
        reader.nextInt()
        assertTrue(actual = reader.hasNext())
        reader.nextInt()
        assertTrue(actual = reader.hasNext())
        reader.nextInt()
        assertFalse(actual = reader.hasNext())
    }

    // ══════════════════════════════════════════════════════════════════
    // Truncated JSON / malformations
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun truncatedStringValueThrowsOnRead() {
        val reader = stringReaderOf(json = """{"id": 1, "name": "Ju""")
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
    fun leadingZeroInIntegerThrows() {
        val reader = stringReaderOf(json = """{"v":007}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> { reader.nextInt() }
    }

    @Test
    fun isolatedMinusThrows() {
        val reader = stringReaderOf(json = """{"v":-}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<Exception> { reader.nextInt() }
    }

    @Test
    fun lazyBuildsStringDispatchWhenDisabledInitially() {
        val options = JsonReaderOptions(
            rawBytes = arrayOf("id".encodeToByteArray()),
            shift = 0,
            multiplier = 31,
            tableSize = 1024,
            rawStrings = arrayOf("id"),
            enableStringDispatch = false
        )
        val reader = GhostJsonStringReader(rawData = """{"id":1}""")
        reader.beginObject()
        assertEquals(
            expected = 0,
            actual = reader.selectString(options = options)
        )
    }

    @Test
    fun ensureUtf8BytesCachesSingleEncodingPerReset() {
        val reader = GhostJsonStringReader(rawData = """{"a":1,"b":2}""")
        val first = reader.ensureUtf8Bytes()
        val second = reader.ensureUtf8Bytes()
        assertTrue(
            actual = first === second,
            message = "UTF-8 bytes should be cached across calls"
        )
        reader.reset("""{"c":3}""")
        val afterReset = reader.ensureUtf8Bytes()
        assertTrue(
            actual = afterReset !== first,
            message = "Reset should invalidate UTF-8 cache"
        )
    }

    @Test
    fun readerCharBytePositionMatchesParserUtils() {
        val json = """{"emoji":"a🚀b"}"""
        val reader = GhostJsonStringReader(rawData = json)
        for (charPos in 0..json.length) {
            assertEquals(
                expected = charToBytePosition(s = json, charPos = charPos),
                actual = reader.charPositionToBytePosition(charPos = charPos),
                message = "char→byte mismatch at $charPos"
            )
        }
        val bytes = reader.ensureUtf8Bytes()
        for (bytePos in 0..bytes.size) {
            assertEquals(
                expected = byteToCharPosition(s = json, targetBytePos = bytePos),
                actual = reader.bytePositionToCharPosition(targetBytePos = bytePos),
                message = "byte→char mismatch at $bytePos"
            )
        }
    }
}

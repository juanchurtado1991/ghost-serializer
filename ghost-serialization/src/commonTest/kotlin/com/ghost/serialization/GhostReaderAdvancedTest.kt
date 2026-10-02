@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.common.json.JsonReaderOptions
import com.ghost.serialization.parser.common.createByteArraySource
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginArray
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.consumeKeySeparator
import com.ghost.serialization.parser.streaming.consumeNull
import com.ghost.serialization.parser.streaming.endArray
import com.ghost.serialization.parser.streaming.endObject
import com.ghost.serialization.parser.streaming.isNextNullValue
import com.ghost.serialization.parser.streaming.nextBoolean
import com.ghost.serialization.parser.streaming.nextDouble
import com.ghost.serialization.parser.streaming.nextFloat
import com.ghost.serialization.parser.streaming.nextInt
import com.ghost.serialization.parser.streaming.nextKey
import com.ghost.serialization.parser.streaming.nextLong
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.parser.streaming.selectString
import com.ghost.serialization.parser.streaming.skipValue
import com.ghost.serialization.parser.streaming.readList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue


class GhostReaderAdvancedTest {

    private fun readerOf(json: String): GhostJsonReader {
        return GhostJsonReader(json.encodeToByteArray())
    }

    // ── A. SKIP VALUE GAUNTLET ───────────────────────────────────────

    @Test
    fun skipsUnknownObjectValue() {
        val options = JsonReaderOptions.of("id")
        val json = """{"extra":{"a":1},"id":42}"""
        val reader = readerOf(json)
        reader.beginObject()
        assertEquals(
            expected = -2,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        reader.skipValue()
        assertEquals(
            expected = 0,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = 42,
            actual = reader.nextInt()
        )
    }

    @Test
    fun skipsUnknownArrayValue() {
        val options = JsonReaderOptions.of("id")
        val json = """{"arr":[1,"two",null,true,[]],"id":99}"""
        val reader = readerOf(json)
        reader.beginObject()
        assertEquals(
            expected = -2,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        reader.skipValue()
        assertEquals(
            expected = 0,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = 99,
            actual = reader.nextInt()
        )
    }

    @Test
    fun skipsUnknownStringValue() {
        val options = JsonReaderOptions.of("id")
        val json = """{"text":"hello world","id":7}"""
        val reader = readerOf(json)
        reader.beginObject()
        assertEquals(
            expected = -2,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        reader.skipValue()
        assertEquals(
            expected = 0,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = 7,
            actual = reader.nextInt()
        )
    }

    @Test
    fun skipsUnknownBooleanValue() {
        val options = JsonReaderOptions.of("id")
        val json = """{"flag":true,"id":8}"""
        val reader = readerOf(json)
        reader.beginObject()
        assertEquals(
            expected = -2,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        reader.skipValue()
        assertEquals(
            expected = 0,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = 8,
            actual = reader.nextInt()
        )
    }

    @Test
    fun skipsUnknownNullValue() {
        val options = JsonReaderOptions.of("id")
        val json = """{"nothing":null,"id":9}"""
        val reader = readerOf(json)
        reader.beginObject()
        assertEquals(
            expected = -2,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        reader.skipValue()
        assertEquals(
            expected = 0,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = 9,
            actual = reader.nextInt()
        )
    }

    @Test
    fun skipsUnknownNumberValue() {
        val options = JsonReaderOptions.of("id")
        val json = """{"count":12345,"id":10}"""
        val reader = readerOf(json)
        reader.beginObject()
        assertEquals(
            expected = -2,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        reader.skipValue()
        assertEquals(
            expected = 0,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = 10,
            actual = reader.nextInt()
        )
    }

    @Test
    fun skipsMultipleConsecutiveUnknownFields() {
        val options = JsonReaderOptions.of("id")
        val json = """{"a":"x","b":true,"c":[1],"d":null,"e":{},"id":1}"""
        val reader = readerOf(json)
        reader.beginObject()
        repeat(5) {
            assertEquals(
                expected = -2,
                actual = reader.selectString(options = options)
            )
            reader.consumeKeySeparator()
            reader.skipValue()
        }
        assertEquals(
            expected = 0,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = 1,
            actual = reader.nextInt()
        )
    }

    @Test
    fun skipsDeeplyNestedUnknownObject() {
        val options = JsonReaderOptions.of("id")
        val json = """{"deep":{"l1":{"l2":{"l3":{"l4":"bottom"}}}},"id":77}"""
        val reader = readerOf(json)
        reader.beginObject()
        assertEquals(
            expected = -2,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        reader.skipValue()
        assertEquals(
            expected = 0,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = 77,
            actual = reader.nextInt()
        )
    }

    // ── B. SINGLE ELEMENT STRUCTURES ─────────────────────────────────

    @Test
    fun readsSingleElementArray() {
        val reader = readerOf("[42]")
        val result = reader.readList { reader.nextInt() }
        assertEquals(
            expected = listOf(42),
            actual = result
        )
    }

    @Test
    fun readsSingleFieldObject() {
        val reader = readerOf("""{"only":true}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertTrue(actual = reader.nextBoolean())
        reader.endObject()
    }

    // ── C. ADJACENT NULL VALUES ──────────────────────────────────────

    @Test
    fun readsConsecutiveNullValues() {
        val json = """{"a":null,"b":null,"c":null}"""
        val options = JsonReaderOptions.of("a", "b", "c")
        val reader = readerOf(json)
        reader.beginObject()

        assertEquals(
            expected = 0,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        assertTrue(actual = reader.isNextNullValue())
        reader.consumeNull()

        assertEquals(
            expected = 1,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        assertTrue(actual = reader.isNextNullValue())
        reader.consumeNull()

        assertEquals(
            expected = 2,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        assertTrue(actual = reader.isNextNullValue())
        reader.consumeNull()
    }

    // ── D. MIXED TYPE ARRAYS ─────────────────────────────────────────

    @Test
    fun readsArrayOfBooleans() {
        val reader = readerOf("[true,false,true]")
        val result = reader.readList { reader.nextBoolean() }
        assertEquals(
            expected = listOf(true, false, true),
            actual = result
        )
    }

    @Test
    fun readsArrayOfDoubles() {
        val reader = readerOf("[1.1,2.2,3.3]")
        val result = reader.readList { reader.nextDouble() }
        assertEquals(
            expected = 3,
            actual = result.size
        )
        assertEquals(
            expected = 1.1,
            actual = result[0],
            absoluteTolerance = 0.01
        )
        assertEquals(
            expected = 3.3,
            actual = result[2],
            absoluteTolerance = 0.01
        )
    }

    @Test
    fun readsArrayOfLongs() {
        val reader = readerOf("[${Long.MAX_VALUE},0,${Long.MIN_VALUE}]")
        val result = reader.readList { reader.nextLong() }
        assertEquals(
            expected = listOf(Long.MAX_VALUE, 0L, Long.MIN_VALUE),
            actual = result
        )
    }

    // ── E. STRICT MODE ───────────────────────────────────────────────

    @Test
    fun strictModeThrowsOnUnknownField() {
        val options = JsonReaderOptions.of("id")
        val json = """{"unknown":"val","id":1}"""
        val reader = GhostJsonReader(
            createByteArraySource(data = json.encodeToByteArray()),
            strictMode = true
        )
        reader.beginObject()
        assertFailsWith<GhostJsonException> {
            reader.selectString(options = options)
        }
    }

    // ── F. DEPTH TRACKING ────────────────────────────────────────────

    @Test
    fun depthIncreasesAndDecreasesCorrectly() {
        val reader = readerOf("""{"a":{"b":[1]}}""")
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
    fun customMaxDepthIsEnforced() {
        val reader = GhostJsonReader(
            createByteArraySource(data = "[[[]]]".encodeToByteArray()),
            maxDepth = 2
        )
        reader.beginArray()
        reader.beginArray()
        assertFailsWith<GhostJsonException> {
            reader.beginArray()
        }
    }

    // ── G. LINE AND COLUMN TRACKING ──────────────────────────────────

    @Test
    fun tracksLineNumberOnNewlines() {
        val json = "{\n\"v\"\n:\n1\n X" // 'X' is invalid
        val reader = readerOf(json)
        reader.beginObject()
        val ex = assertFailsWith<GhostJsonException> {
            reader.selectString(options = JsonReaderOptions.of("v"))
            reader.consumeKeySeparator()
            reader.nextInt()
            reader.endObject()
        }
        assertTrue(
            actual = ex.line > 1,
            message = "Line should be > 1. Found: ${ex.line}"
        )
    }

    // ── H. FLOAT PRECISION ───────────────────────────────────────────

    @Test
    fun nextFloatLosesPrecisionGracefully() {
        val reader = readerOf("""{"v":1.123456789}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        val f = reader.nextFloat()
        assertEquals(
            expected = 1.1234568,
            actual = f.toDouble(),
            absoluteTolerance = 0.0000001
        )
    }

    // ── I. SPECIAL FIELD NAME PATTERNS ───────────────────────────────

    @Test
    fun selectStringWithSingleCharFields() {
        val options = JsonReaderOptions.of("a", "b", "c")
        val json = """{"b":2,"c":3,"a":1}"""
        val reader = readerOf(json)
        reader.beginObject()

        assertEquals(
            expected = 1,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = 2,
            actual = reader.nextInt()
        )
        assertEquals(
            expected = 2,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = 3,
            actual = reader.nextInt()
        )
        assertEquals(
            expected = 0,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = 1,
            actual = reader.nextInt()
        )
    }

    @Test
    fun selectStringWithLongFieldNames() {
        val longName = "thisIsAVeryLongFieldNameThatExceedsNormalLengths"
        val options = JsonReaderOptions.of(longName)
        val json = """{"$longName":"found"}"""
        val reader = readerOf(json)
        reader.beginObject()

        assertEquals(
            expected = 0,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = "found",
            actual = reader.nextString()
        )
    }

    @Test
    fun selectStringWithUnderscoreFields() {
        val options = JsonReaderOptions.of("user_id", "user_name", "user_ids")
        val json = """{"user_ids":[1,2],"user_id":42,"user_name":"ghost"}"""
        val reader = readerOf(json)
        reader.beginObject()

        assertEquals(
            expected = 2,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        reader.readList { reader.nextInt() }

        assertEquals(
            expected = 0,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = 42,
            actual = reader.nextInt()
        )

        assertEquals(
            expected = 1,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = "ghost",
            actual = reader.nextString()
        )
    }

    // ── J. MAP READING ───────────────────────────────────────────────

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
        assertEquals(
            expected = mapOf("k1" to "v1", "k2" to "v2"),
            actual = map
        )
    }

    @Test
    fun readsEmptyMap() {
        val reader = readerOf("{}")
        reader.beginObject()
        val key = reader.nextKey()
        assertEquals(
            expected = null,
            actual = key
        )
        reader.endObject()
    }
}

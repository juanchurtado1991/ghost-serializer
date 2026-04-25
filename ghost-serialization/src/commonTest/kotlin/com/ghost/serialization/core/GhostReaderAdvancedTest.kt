package com.ghost.serialization.core

import com.ghost.serialization.core.exception.GhostJsonException
import com.ghost.serialization.core.parser.GhostJsonReader
import com.ghost.serialization.core.parser.JsonReaderOptions
import com.ghost.serialization.core.parser.consumeKeySeparator
import com.ghost.serialization.core.parser.consumeNull
import com.ghost.serialization.core.parser.isNextNullValue
import com.ghost.serialization.core.parser.nextDouble
import com.ghost.serialization.core.parser.nextFloat
import com.ghost.serialization.core.parser.nextInt
import com.ghost.serialization.core.parser.nextKey
import com.ghost.serialization.core.parser.nextLong
import com.ghost.serialization.core.parser.readList
import com.ghost.serialization.core.parser.skipValue
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GhostReaderAdvancedTest {

    private fun readerOf(json: String): GhostJsonReader {
        return GhostJsonReader(Buffer().writeUtf8(json))
    }

    // ── A. SKIP VALUE GAUNTLET ───────────────────────────────────────

    @Test
    fun skipsUnknownObjectValue() {
        val options = JsonReaderOptions.of("id")
        val json = """{"extra":{"a":1},"id":42}"""
        val reader = readerOf(json)
        reader.beginObject()
        assertEquals(-2, reader.selectString(options))
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
        assertEquals(-2, reader.selectString(options))
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
        assertEquals(-2, reader.selectString(options))
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
        assertEquals(-2, reader.selectString(options))
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
        assertEquals(-2, reader.selectString(options))
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
        assertEquals(-2, reader.selectString(options))
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
            assertEquals(-2, reader.selectString(options))
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
        assertEquals(-2, reader.selectString(options))
        reader.consumeKeySeparator()
        reader.skipValue()
        assertEquals(0, reader.selectString(options))
        reader.consumeKeySeparator()
        assertEquals(77, reader.nextInt())
    }

    // ── B. SINGLE ELEMENT STRUCTURES ─────────────────────────────────

    @Test
    fun readsSingleElementArray() {
        val reader = readerOf("[42]")
        val result = reader.readList { reader.nextInt() }
        assertEquals(listOf(42), result)
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

    // ── C. ADJACENT NULL VALUES ──────────────────────────────────────

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

    // ── D. MIXED TYPE ARRAYS ─────────────────────────────────────────

    @Test
    fun readsArrayOfBooleans() {
        val reader = readerOf("[true,false,true]")
        val result = reader.readList { reader.nextBoolean() }
        assertEquals(listOf(true, false, true), result)
    }

    @Test
    fun readsArrayOfDoubles() {
        val reader = readerOf("[1.1,2.2,3.3]")
        val result = reader.readList { reader.nextDouble() }
        assertEquals(3, result.size)
        assertEquals(1.1, result[0], 0.01)
        assertEquals(3.3, result[2], 0.01)
    }

    @Test
    fun readsArrayOfLongs() {
        val reader = readerOf("[${Long.MAX_VALUE},0,${Long.MIN_VALUE}]")
        val result = reader.readList { reader.nextLong() }
        assertEquals(listOf(Long.MAX_VALUE, 0L, Long.MIN_VALUE), result)
    }

    // ── E. STRICT MODE ───────────────────────────────────────────────

    @Test
    fun strictModeThrowsOnUnknownField() {
        val options = JsonReaderOptions.of("id")
        val json = """{"unknown":"val","id":1}"""
        val reader = GhostJsonReader(
            Buffer().writeUtf8(json),
            strictMode = true
        )
        reader.beginObject()
        assertFailsWith<GhostJsonException> {
            reader.selectString(options)
        }
    }

    // ── F. DEPTH TRACKING ────────────────────────────────────────────

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
    fun customMaxDepthIsEnforced() {
        val reader = GhostJsonReader(
            Buffer().writeUtf8("[[[]]]"),
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
            reader.selectString(JsonReaderOptions.of("v"))
            reader.consumeKeySeparator()
            reader.nextInt()
            reader.endObject() // This MUST fail
        }
        assertTrue(ex.line > 1, "Line should be > 1. Found: ${ex.line}")
    }

    // ── H. FLOAT PRECISION ───────────────────────────────────────────

    @Test
    fun nextFloatLosesPrecisionGracefully() {
        val reader = readerOf("""{"v":1.123456789}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        val f = reader.nextFloat()
        assertEquals(1.1234568f, f, 0.0000001f)
    }

    // ── I. SPECIAL FIELD NAME PATTERNS ───────────────────────────────

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
    fun selectStringWithLongFieldNames() {
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
        assertEquals(mapOf("k1" to "v1", "k2" to "v2"), map)
    }

    @Test
    fun readsEmptyMap() {
        val reader = readerOf("{}")
        reader.beginObject()
        val key = reader.nextKey()
        assertEquals(null, key)
        reader.endObject()
    }
}

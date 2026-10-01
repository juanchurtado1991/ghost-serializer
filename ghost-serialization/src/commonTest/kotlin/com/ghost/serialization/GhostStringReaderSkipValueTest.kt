@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.parser.common.json.JsonReaderOptions
import com.ghost.serialization.parser.strings.beginObject
import com.ghost.serialization.parser.strings.consumeKeySeparator
import com.ghost.serialization.parser.strings.nextInt
import com.ghost.serialization.parser.strings.selectString
import com.ghost.serialization.parser.strings.skipValue
import kotlin.test.Test
import kotlin.test.assertEquals
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants as SCN

/** [com.ghost.serialization.parser.strings.GhostJsonStringReader] contract: skipValue stress cases. */
@OptIn(InternalGhostApi::class)
class GhostStringReaderSkipValueTest {

    // ══════════════════════════════════════════════════════════════════
    // skipValue stress cases
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun skipsUnknownObjectValue() {
        val options = JsonReaderOptions.of("id")
        val json = """{"extra":{"a":1},"id":42}"""
        val reader = stringReaderOf(json = json)
        reader.beginObject()
        assertEquals(
            expected = SCN.MATCH_NONE,
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
        val reader = stringReaderOf(json = json)
        reader.beginObject()
        assertEquals(
            expected = SCN.MATCH_NONE,
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
        val reader = stringReaderOf(json = json)
        reader.beginObject()
        assertEquals(
            expected = SCN.MATCH_NONE,
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
        val reader = stringReaderOf(json = json)
        reader.beginObject()
        assertEquals(
            expected = SCN.MATCH_NONE,
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
        val reader = stringReaderOf(json = json)
        reader.beginObject()
        assertEquals(
            expected = SCN.MATCH_NONE,
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
        val reader = stringReaderOf(json = json)
        reader.beginObject()
        assertEquals(
            expected = SCN.MATCH_NONE,
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
        val reader = stringReaderOf(json = json)
        reader.beginObject()
        repeat(5) {
            assertEquals(
                expected = SCN.MATCH_NONE,
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
        val reader = stringReaderOf(json = json)
        reader.beginObject()
        assertEquals(
            expected = SCN.MATCH_NONE,
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
}

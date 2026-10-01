@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.parser.common.json.JsonReaderOptions
import com.ghost.serialization.parser.strings.beginObject
import com.ghost.serialization.parser.strings.consumeKeySeparator
import com.ghost.serialization.parser.strings.consumeNull
import com.ghost.serialization.parser.strings.endObject
import com.ghost.serialization.parser.strings.isNextNullValue
import com.ghost.serialization.parser.strings.nextBoolean
import com.ghost.serialization.parser.strings.nextDouble
import com.ghost.serialization.parser.strings.nextInt
import com.ghost.serialization.parser.strings.nextKey
import com.ghost.serialization.parser.strings.nextString
import com.ghost.serialization.parser.strings.readList
import com.ghost.serialization.parser.strings.readMap
import com.ghost.serialization.parser.strings.readQuotedString
import com.ghost.serialization.parser.strings.selectString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [GhostJsonStringReader] contract: list and map reading. */
@OptIn(InternalGhostApi::class)
class GhostStringReaderCollectionTest {

    // ══════════════════════════════════════════════════════════════════
    // List and map reading
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun readsListOfInts() {
        val reader = stringReaderOf(json = "[1,2,3]")
        val list = reader.readList { reader.nextInt() }
        assertEquals(
            expected = listOf(1, 2, 3),
            actual = list
        )
    }

    @Test
    fun readsEmptyList() {
        val reader = stringReaderOf(json = "[]")
        val list = reader.readList { reader.nextInt() }
        assertEquals(
            expected = emptyList<Int>(),
            actual = list
        )
    }

    @Test
    fun readsSingleElementList() {
        val reader = stringReaderOf(json = "[99]")
        val list = reader.readList { reader.nextInt() }
        assertEquals(
            expected = listOf(99),
            actual = list
        )
    }

    @Test
    fun readsListOfStrings() {
        val reader = stringReaderOf(json = """["a","b","c"]""")
        val list = reader.readList { reader.nextString() }
        assertEquals(
            expected = listOf("a", "b", "c"),
            actual = list
        )
    }

    @Test
    fun readsListOfBooleans() {
        val reader = stringReaderOf(json = "[true,false,true]")
        val result = reader.readList { reader.nextBoolean() }
        assertEquals(
            expected = listOf(true, false, true),
            actual = result
        )
    }

    @Test
    fun readsListOfDoubles() {
        val reader = stringReaderOf(json = "[1.1,2.2,3.3]")
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
    fun readsMapOfStringToInt() {
        val reader = stringReaderOf(json = """{"a":1,"b":2}""")
        val map = reader.readMap(
            keyParser = { reader.readQuotedString() },
            valueParser = { reader.nextInt() }
        )
        assertEquals(
            expected = mapOf("a" to 1, "b" to 2),
            actual = map
        )
    }

    @Test
    fun readsEmptyMap() {
        val reader = stringReaderOf(json = "{}")
        reader.beginObject()
        val key = reader.nextKey()
        assertNull(actual = key)
        reader.endObject()
    }

    @Test
    fun readsMapWithStringValues() {
        val json = """{"k1":"v1","k2":"v2"}"""
        val reader = stringReaderOf(json = json)
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
    fun readsConsecutiveNullValues() {
        val json = """{"a":null,"b":null,"c":null}"""
        val options = JsonReaderOptions.of("a", "b", "c")
        val reader = stringReaderOf(json = json)
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
}

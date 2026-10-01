@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.beginObject
import com.ghost.serialization.parser.strings.consumeKeySeparator
import com.ghost.serialization.parser.strings.consumeNull
import com.ghost.serialization.parser.strings.isNextNullValue
import com.ghost.serialization.parser.strings.nextBoolean
import com.ghost.serialization.parser.strings.nextKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [GhostJsonStringReader] contract: null/boolean literal detection and coercion. */
@OptIn(InternalGhostApi::class)
class GhostStringReaderLiteralTest {

    // ══════════════════════════════════════════════════════════════════
    // isNextNullValue correctness
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun isNextNullDetectsActualNull() {
        val reader = stringReaderOf(json = """{"v":null}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertTrue(actual = reader.isNextNullValue())
    }

    @Test
    fun isNextNullReturnsFalseForString() {
        val reader = stringReaderOf(json = """{"v":"hello"}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFalse(actual = reader.isNextNullValue())
    }

    @Test
    fun isNextNullReturnsFalseForNumber() {
        val reader = stringReaderOf(json = """{"v":42}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFalse(actual = reader.isNextNullValue())
    }

    @Test
    fun isNextNullReturnsFalseForObject() {
        val reader = stringReaderOf(json = """{"v":{}}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFalse(actual = reader.isNextNullValue())
    }

    @Test
    fun isNextNullReturnsFalseForArray() {
        val reader = stringReaderOf(json = """{"v":[]}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFalse(actual = reader.isNextNullValue())
    }

    @Test
    fun isNextNullReturnsFalseForTrue() {
        val reader = stringReaderOf(json = """{"v":true}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFalse(actual = reader.isNextNullValue())
    }

    @Test
    fun isNextNullReturnsFalseForFalse() {
        val reader = stringReaderOf(json = """{"v":false}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFalse(actual = reader.isNextNullValue())
    }

    // ══════════════════════════════════════════════════════════════════
    // Truncated literals
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun truncatedNullFailsOnConsume() {
        val reader = stringReaderOf(json = """{"v":nul}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertTrue(actual = reader.isNextNullValue())
        assertFailsWith<Exception> { reader.consumeNull() }
    }

    @Test
    fun truncatedTrueThrowsException() {
        val reader = stringReaderOf(json = "tru")
        assertFailsWith<Exception> { reader.nextBoolean() }
    }

    @Test
    fun truncatedFalseThrowsException() {
        val reader = stringReaderOf(json = "fals")
        assertFailsWith<Exception> { reader.nextBoolean() }
    }

    // ══════════════════════════════════════════════════════════════════
    // Boolean coercion
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun coercesIntOneToBooleanTrue() {
        val reader = GhostJsonStringReader(rawData = """{"v":1}""", coerceBooleans = true)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = true,
            actual = reader.nextBoolean()
        )
    }

    @Test
    fun coercesIntZeroToBooleanFalse() {
        val reader = GhostJsonStringReader(rawData = """{"v":0}""", coerceBooleans = true)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = false,
            actual = reader.nextBoolean()
        )
    }

    @Test
    fun coercesStringTrueToBoolean() {
        val reader = GhostJsonStringReader(rawData = """{"v":"true"}""", coerceBooleans = true)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = true,
            actual = reader.nextBoolean()
        )
    }

    @Test
    fun coercesStringFalseToBoolean() {
        val reader = GhostJsonStringReader(rawData = """{"v":"false"}""", coerceBooleans = true)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = false,
            actual = reader.nextBoolean()
        )
    }

    @Test
    fun booleanWithoutCoercionRejectsInt() {
        val reader = stringReaderOf(json = """{"v":1}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> { reader.nextBoolean() }
    }
}

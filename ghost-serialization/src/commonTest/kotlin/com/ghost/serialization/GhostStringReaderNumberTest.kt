@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.beginObject
import com.ghost.serialization.parser.strings.consumeKeySeparator
import com.ghost.serialization.parser.strings.nextDouble
import com.ghost.serialization.parser.strings.nextFloat
import com.ghost.serialization.parser.strings.nextInt
import com.ghost.serialization.parser.strings.nextKey
import com.ghost.serialization.parser.strings.nextLong
import com.ghost.serialization.parser.strings.readList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** [GhostJsonStringReader] contract: number parsing, overflow, precision, and string coercion. */
@OptIn(InternalGhostApi::class)
class GhostStringReaderNumberTest {

    // ══════════════════════════════════════════════════════════════════
    // Long / Int overflow detection
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun readsZeroInt() {
        val reader = stringReaderOf(json = """{"v":0}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = 0,
            actual = reader.nextInt()
        )
    }

    @Test
    fun readsPositiveInt() {
        val reader = stringReaderOf(json = """{"v":42}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = 42,
            actual = reader.nextInt()
        )
    }

    @Test
    fun readsNegativeInt() {
        val reader = stringReaderOf(json = """{"v":-99}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = -99,
            actual = reader.nextInt()
        )
    }

    @Test
    fun readsExactLongMaxValue() {
        val reader = stringReaderOf(json = """{"v":${Long.MAX_VALUE}}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = Long.MAX_VALUE,
            actual = reader.nextLong()
        )
    }

    @Test
    fun readsExactLongMinValue() {
        val reader = stringReaderOf(json = """{"v":${Long.MIN_VALUE}}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = Long.MIN_VALUE,
            actual = reader.nextLong()
        )
    }

    @Test
    fun veryLargeNumberThrowsLongOverflow() {
        val reader = stringReaderOf(json = """{"v":99999999999999999999}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> { reader.nextLong() }
    }

    @Test
    fun intOverflowFromLongThrows() {
        val reader = stringReaderOf(json = """{"v":${Long.MAX_VALUE}}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> { reader.nextInt() }
    }

    @Test
    fun longNegativeOverflowThrows() {
        val reader = stringReaderOf(json = """{"v":-92233720368547758089}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> { reader.nextLong() }
    }

    // ══════════════════════════════════════════════════════════════════
    // Numbers at end of stream / edge-case numerics
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun readsNumberAtEndOfArray() {
        val reader = stringReaderOf(json = "[42]")
        val result = reader.readList { reader.nextInt() }
        assertEquals(
            expected = listOf(42),
            actual = result
        )
    }

    @Test
    fun readsDoubleAtEndOfArray() {
        val reader = stringReaderOf(json = "[3.14]")
        val result = reader.readList { reader.nextDouble() }
        assertEquals(
            expected = 1,
            actual = result.size
        )
        assertEquals(
            expected = 3.14,
            actual = result[0],
            absoluteTolerance = 0.001
        )
    }

    @Test
    fun readsScientificNotationPositiveExponent() {
        val reader = stringReaderOf(json = """{"v":1e10}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = 1e10,
            actual = reader.nextDouble(),
            absoluteTolerance = 0.1
        )
    }

    @Test
    fun readsScientificNotationNegativeExponent() {
        val reader = stringReaderOf(json = """{"v":1.23e-4}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = 1.23e-4,
            actual = reader.nextDouble(),
            absoluteTolerance = 1e-10
        )
    }

    @Test
    fun readsScientificNotationUppercaseE() {
        val reader = stringReaderOf(json = """{"v":1.0E+2}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = 100.0,
            actual = reader.nextDouble(),
            absoluteTolerance = 0.01
        )
    }

    @Test
    fun readsDoublePrecision() {
        val reader = stringReaderOf(json = """{"v":1.234567890123456}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = 1.234567890123456,
            actual = reader.nextDouble(),
            absoluteTolerance = 1e-15
        )
    }

    @Test
    fun readsArrayOfLongs() {
        val reader = stringReaderOf(json = "[${Long.MAX_VALUE},0,${Long.MIN_VALUE}]")
        val result = reader.readList { reader.nextLong() }
        assertEquals(
            expected = listOf(Long.MAX_VALUE, 0L, Long.MIN_VALUE),
            actual = result
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // Float precision
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun nextFloatLosesPrecisionGracefully() {
        val reader = stringReaderOf(json = """{"v":1.123456789}""")
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

    @Test
    fun readsNegativeFloat() {
        val reader = stringReaderOf(json = """{"v":-3.14}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = -3.14f,
            actual = reader.nextFloat(),
            absoluteTolerance = 0.001f
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // String coercion to numbers
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun coercesQuotedIntToInt() {
        val reader = GhostJsonStringReader(rawData = """{"v":"42"}""", coerceStringsToNumbers = true)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = 42,
            actual = reader.nextInt()
        )
    }

    @Test
    fun coercesQuotedLongToLong() {
        val reader = GhostJsonStringReader(
            rawData = """{"v":"${Long.MAX_VALUE}"}""",
            coerceStringsToNumbers = true
        )
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = Long.MAX_VALUE,
            actual = reader.nextLong()
        )
    }

    @Test
    fun coercesQuotedDoubleToDouble() {
        val reader = GhostJsonStringReader(rawData = """{"v":"3.14"}""", coerceStringsToNumbers = true)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = 3.14,
            actual = reader.nextDouble(),
            absoluteTolerance = 0.001
        )
    }

    @Test
    fun numberCoercionWithoutFlagThrows() {
        val reader = stringReaderOf(json = """{"v":"42"}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> { reader.nextInt() }
    }
}

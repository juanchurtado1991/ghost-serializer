package com.ghost.serialization.types

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [RawJsonValueScanner] is internal, so these edge cases can only be tested from within
 * ghost-api — elsewhere it's only reached indirectly through KSP-generated round-trips, which
 * don't guarantee grammar/overflow boundary coverage.
 */
class RawJsonNumberScanningTest {

    private fun raw(
        json: String
    ): RawJson = RawJson.fromString(json = json)

    @Test
    fun kindRejectsLeadingZeroFollowedByDigits() {
        assertEquals(
            expected = RawJsonKind.INVALID,
            actual = raw(json = "01").kind()
        )
        assertEquals(
            expected = RawJsonKind.INVALID,
            actual = raw(json = "-01").kind()
        )
        assertEquals(
            expected = RawJsonKind.NUMBER,
            actual = raw(json = "0").kind()
        )
        assertEquals(
            expected = RawJsonKind.NUMBER,
            actual = raw(json = "-0").kind()
        )
    }

    @Test
    fun kindRejectsIncompleteFractionOrExponent() {
        assertEquals(
            expected = RawJsonKind.INVALID,
            actual = raw(json = "1.").kind()
        )
        assertEquals(
            expected = RawJsonKind.INVALID,
            actual = raw(json = ".5").kind()
        )
        assertEquals(
            expected = RawJsonKind.INVALID,
            actual = raw(json = "1e").kind()
        )
        assertEquals(
            expected = RawJsonKind.INVALID,
            actual = raw(json = "1e+").kind()
        )
        assertEquals(
            expected = RawJsonKind.INVALID,
            actual = raw(json = "-").kind()
        )
        assertEquals(
            expected = RawJsonKind.NUMBER,
            actual = raw(json = "1e3").kind()
        )
        assertEquals(
            expected = RawJsonKind.NUMBER,
            actual = raw(json = "1.5e-3").kind()
        )
    }

    @Test
    fun kindRejectsTrailingGarbageAfterNumber() {
        assertEquals(
            expected = RawJsonKind.INVALID,
            actual = raw(json = "1x").kind()
        )
        assertEquals(
            expected = RawJsonKind.INVALID,
            actual = raw(json = "1.0.0").kind()
        )
    }

    @Test
    fun asLongOrNull_roundTripsLongMaxAndMinExactly() {
        assertEquals(
            expected = Long.MAX_VALUE,
            actual = raw(json = Long.MAX_VALUE.toString()).asLongOrNull()
        )
        assertEquals(
            expected = Long.MIN_VALUE,
            actual = raw(json = Long.MIN_VALUE.toString()).asLongOrNull()
        )
    }

    @Test
    fun asLongOrNull_rejectsOverflowPastLongBounds() {
        assertNull(actual = raw(json = "9223372036854775808").asLongOrNull()) // Long.MAX_VALUE + 1
        assertNull(actual = raw(json = "-9223372036854775809").asLongOrNull()) // Long.MIN_VALUE - 1
        assertNull(actual = raw(json = "99999999999999999999999").asLongOrNull())
    }

    @Test
    fun asIntOrNull_rejectsValuesOutsideIntRangeButWithinLongRange() {
        assertEquals(
            expected = Int.MAX_VALUE,
            actual = raw(json = Int.MAX_VALUE.toString()).asIntOrNull()
        )
        assertEquals(
            expected = Int.MIN_VALUE,
            actual = raw(json = Int.MIN_VALUE.toString()).asIntOrNull()
        )
        assertNull(actual = raw(json = (Int.MAX_VALUE.toLong() + 1).toString()).asIntOrNull())
        assertNull(actual = raw(json = (Int.MIN_VALUE.toLong() - 1).toString()).asIntOrNull())
    }

    @Test
    fun asDoubleOrNull_fallsBackToDecodeForFractionAndExponent() {
        assertEquals(
            expected = 3.14,
            actual = raw(json = "3.14").asDoubleOrNull()
        )
        assertEquals(
            expected = 0.0,
            actual = raw(json = "0").asDoubleOrNull()
        )
        assertEquals(
            expected = 1.5e300,
            actual = raw(json = "1.5e300").asDoubleOrNull()
        )
        assertNull(actual = raw(json = "NaN").asDoubleOrNull())
        assertNull(actual = raw(json = "Infinity").asDoubleOrNull())
    }

    @Test
    fun asIntAndLongOrNull_nullForNonIntegerTokens() {
        assertNull(actual = raw(json = "true").asIntOrNull())
        assertNull(actual = raw(json = "\"42\"").asIntOrNull())
        assertNull(actual = raw(json = "").asLongOrNull())
    }
}

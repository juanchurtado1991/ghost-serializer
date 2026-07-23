package com.ghost.serialization.types

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [RawJsonValueScanner] is internal, so these edge cases can only be exercised from within
 * ghost-api. Elsewhere in the repo it is only reached indirectly through KSP-generated
 * round-trips, which don't guarantee coverage of grammar/overflow boundaries.
 */
class RawJsonNumberScanningTest {

    private fun raw(json: String): RawJson = RawJson.fromString(json)

    @Test
    fun kindRejectsLeadingZeroFollowedByDigits() {
        assertEquals(RawJsonKind.INVALID, raw("01").kind())
        assertEquals(RawJsonKind.INVALID, raw("-01").kind())
        assertEquals(RawJsonKind.NUMBER, raw("0").kind())
        assertEquals(RawJsonKind.NUMBER, raw("-0").kind())
    }

    @Test
    fun kindRejectsIncompleteFractionOrExponent() {
        assertEquals(RawJsonKind.INVALID, raw("1.").kind())
        assertEquals(RawJsonKind.INVALID, raw(".5").kind())
        assertEquals(RawJsonKind.INVALID, raw("1e").kind())
        assertEquals(RawJsonKind.INVALID, raw("1e+").kind())
        assertEquals(RawJsonKind.INVALID, raw("-").kind())
        assertEquals(RawJsonKind.NUMBER, raw("1e3").kind())
        assertEquals(RawJsonKind.NUMBER, raw("1.5e-3").kind())
    }

    @Test
    fun kindRejectsTrailingGarbageAfterNumber() {
        assertEquals(RawJsonKind.INVALID, raw("1x").kind())
        assertEquals(RawJsonKind.INVALID, raw("1.0.0").kind())
    }

    @Test
    fun asLongOrNull_roundTripsLongMaxAndMinExactly() {
        assertEquals(Long.MAX_VALUE, raw(Long.MAX_VALUE.toString()).asLongOrNull())
        assertEquals(Long.MIN_VALUE, raw(Long.MIN_VALUE.toString()).asLongOrNull())
    }

    @Test
    fun asLongOrNull_rejectsOverflowPastLongBounds() {
        assertNull(raw("9223372036854775808").asLongOrNull()) // Long.MAX_VALUE + 1
        assertNull(raw("-9223372036854775809").asLongOrNull()) // Long.MIN_VALUE - 1
        assertNull(raw("99999999999999999999999").asLongOrNull())
    }

    @Test
    fun asIntOrNull_rejectsValuesOutsideIntRangeButWithinLongRange() {
        assertEquals(Int.MAX_VALUE, raw(Int.MAX_VALUE.toString()).asIntOrNull())
        assertEquals(Int.MIN_VALUE, raw(Int.MIN_VALUE.toString()).asIntOrNull())
        assertNull(raw((Int.MAX_VALUE.toLong() + 1).toString()).asIntOrNull())
        assertNull(raw((Int.MIN_VALUE.toLong() - 1).toString()).asIntOrNull())
    }

    @Test
    fun asDoubleOrNull_fallsBackToDecodeForFractionAndExponent() {
        assertEquals(3.14, raw("3.14").asDoubleOrNull())
        assertEquals(0.0, raw("0").asDoubleOrNull())
        assertEquals(1.5e300, raw("1.5e300").asDoubleOrNull())
        assertNull(raw("NaN").asDoubleOrNull())
        assertNull(raw("Infinity").asDoubleOrNull())
    }

    @Test
    fun asIntAndLongOrNull_nullForNonIntegerTokens() {
        assertNull(raw("true").asIntOrNull())
        assertNull(raw("\"42\"").asIntOrNull())
        assertNull(raw("").asLongOrNull())
    }
}

@file:Suppress("NOTHING_TO_INLINE")

package com.ghost.serialization.parser.common

import kotlin.math.pow
import com.ghost.serialization.parser.common.constants.GhostJsonNumericLimits as NUM
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants as WR

/**
 * Numeric-parse predicates and digit-accumulation kernels shared by the JSON and YAML readers.
 */

/** Accumulates one decimal digit into an Int, throwing via [onOverflow] past [Int] bounds. */
internal inline fun accumulateIntWithOverflowCheck(
    current: Int,
    digitValue: Int,
    isNegative: Boolean,
    onOverflow: () -> Nothing,
): Int {
    val maxLastDigit = if (isNegative) NUM.INT_MIN_LAST_DIGIT else NUM.INT_MAX_LAST_DIGIT
    val wouldOverflow = current > NUM.INT_OVERFLOW_LIMIT ||
        (current == NUM.INT_OVERFLOW_LIMIT && digitValue > maxLastDigit)

    if (wouldOverflow) onOverflow()
    return current * WR.BASE_TEN + digitValue
}

/**
 * Accumulates one decimal digit into a Long, throwing via [onOverflow] past JVM Long bounds.
 * Preserves the Long.MIN_VALUE edge case for negative overflow-limit + last digit.
 */
internal inline fun accumulateLongWithOverflowCheck(
    current: Long,
    digitValue: Int,
    isNegative: Boolean,
    onOverflow: () -> Nothing,
): Long {
    val wouldOverflow = current == Long.MIN_VALUE ||
        current > NUM.LONG_OVERFLOW_LIMIT ||
        (current == NUM.LONG_OVERFLOW_LIMIT && digitValue > NUM.LONG_MAX_LAST_DIGIT)
    if (wouldOverflow) {
        val isExactNegativeOverflow = isNegative &&
            current == NUM.LONG_OVERFLOW_LIMIT &&
            digitValue == NUM.LONG_MIN_LAST_DIGIT
        if (isExactNegativeOverflow) {
            return Long.MIN_VALUE
        }
        onOverflow()
    }
    return current * WR.BASE_TEN + digitValue
}

internal inline fun getFloatPowerOfTen(
    exponent: Int
): Float {
    return if (exponent > 0) {
        if (exponent < NUM.POWERS_OF_TEN_FLOAT.size) {
            NUM.POWERS_OF_TEN_FLOAT[exponent]
        } else {
            10.0f.pow(x = exponent.toFloat())
        }
    } else {
        val absExp = -exponent
        if (absExp < NUM.INVERSE_POWERS_OF_TEN_FLOAT.size) {
            NUM.INVERSE_POWERS_OF_TEN_FLOAT[absExp]
        } else {
            10.0f.pow(x = exponent.toFloat())
        }
    }
}

internal inline fun isDigit(
    byteCode: Int
): Boolean {
    return (byteCode xor TOK.ZERO_INT) < WR.BASE_TEN
}

internal inline fun isExponentMarker(
    markerByte: Int
): Boolean {
    return (markerByte or TOK.CASE_INSENSITIVE_MASK) == TOK.EXP_LOWER_INT
}

internal inline fun isNumericSeparator(
    byteCode: Int
): Boolean {
    return byteCode == TOK.DOT_INT || byteCode == TOK.EXP_LOWER_INT || byteCode == TOK.EXP_UPPER_INT
}

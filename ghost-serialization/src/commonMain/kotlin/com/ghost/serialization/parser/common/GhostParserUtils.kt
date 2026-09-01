@file:Suppress("NOTHING_TO_INLINE")

package com.ghost.serialization.parser.common

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import kotlin.math.pow
import com.ghost.serialization.parser.common.GhostJsonConstants as C


/**
 * Checks if the byte code matches dot, lower 'e', or upper 'E'.
 */
internal inline fun isNumericSeparator(byteCode: Int): Boolean {
    return byteCode == C.DOT_INT || byteCode == C.EXP_LOWER_INT || byteCode == C.EXP_UPPER_INT
}

/**
 * Checks if the byte code matches lowercase or uppercase 'e'.
 */
internal inline fun isExponentMarker(markerByte: Int): Boolean {
    return (markerByte or C.CASE_INSENSITIVE_MASK) == C.EXP_LOWER_INT
}

/**
 * Helper to verify if the given byte is a valid JSON numeric digit.
 */
internal inline fun isDigit(byteCode: Int): Boolean {
    return (byteCode xor C.ZERO_INT) < C.BASE_TEN
}

/**
 * Accumulates one decimal digit into an Int, throwing via [onOverflow] past JVM Int bounds.
 */
internal inline fun accumulateIntWithOverflowCheck(
    current: Int,
    digitValue: Int,
    isNegative: Boolean,
    onOverflow: () -> Nothing,
): Int {
    if (current > C.INT_OVERFLOW_LIMIT ||
        (current == C.INT_OVERFLOW_LIMIT &&
                digitValue > (if (isNegative) C.INT_MIN_LAST_DIGIT else C.INT_MAX_LAST_DIGIT))
    ) {
        onOverflow()
    }
    return current * C.BASE_TEN + digitValue
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
    if (current == Long.MIN_VALUE ||
        current > C.LONG_OVERFLOW_LIMIT ||
        (current == C.LONG_OVERFLOW_LIMIT && digitValue > C.LONG_MAX_LAST_DIGIT)
    ) {
        if (isNegative && current == C.LONG_OVERFLOW_LIMIT &&
            digitValue == C.LONG_MIN_LAST_DIGIT
        ) {
            return Long.MIN_VALUE
        }
        onOverflow()
    }
    return current * C.BASE_TEN + digitValue
}

/**
 * Returns 10.0f raised to the power of exponent using a lookup table or pow fallback.
 */
internal inline fun getFloatPowerOfTen(exponent: Int): Float {
    return if (exponent > 0) {
        if (exponent < C.POWERS_OF_TEN_FLOAT.size) {
            C.POWERS_OF_TEN_FLOAT[exponent]
        } else {
            10.0f.pow(exponent.toFloat())
        }
    } else {
        val absExp = -exponent
        if (absExp < C.INVERSE_POWERS_OF_TEN_FLOAT.size) {
            C.INVERSE_POWERS_OF_TEN_FLOAT[absExp]
        } else {
            10.0f.pow(exponent.toFloat())
        }
    }
}

/**
 * Returns 10.0 raised to the power of exponent using a lookup table or pow fallback.
 */
internal inline fun getDoublePowerOfTen(exponent: Int): Double {
    return if (exponent > 0) {
        if (exponent < C.POWERS_OF_TEN.size) {
            C.POWERS_OF_TEN[exponent]
        } else {
            10.0.pow(exponent.toDouble())
        }
    } else {
        val absExp = -exponent
        if (absExp < C.INVERSE_POWERS_OF_TEN.size) {
            C.INVERSE_POWERS_OF_TEN[absExp]
        } else {
            10.0.pow(exponent.toDouble())
        }
    }
}

/**
 * Pure, reader-agnostic boolean coercion matcher: compares raw bytes at [start] against known
 * truthy ("true","yes","on","y","1") / falsy ("false","no","off","n","0") strings, case-insensitive
 * via `or CASE_INSENSITIVE_MASK`, without allocating a String.
 *
 * @param onError Called on no match; must throw ([Nothing] keeps every branch exhaustive).
 */
internal inline fun matchCoerceBooleanBytes(
    start: Int,
    length: Int,
    onError: () -> Nothing,
    getByte: (Int) -> Int,
): Boolean = when (length) {
    C.BOOL_STR_LEN_1 -> {
        val b0 = getByte(start)
        when (b0 or C.CASE_INSENSITIVE_MASK) {
            C.FOLD_Y -> true  // "y" / "Y"
            C.FOLD_N -> false // "n" / "N"
            else -> when (b0) {
                C.ONE_INT -> true
                // "1"
                C.ZERO_INT -> false
                // "0"
                else -> onError()
            }
        }
    }

    C.BOOL_STR_LEN_2 -> {
        val b0 = getByte(start)
        val b1 = getByte(start + 1)
        when {
            // "on" / "ON"
            (b0 or C.CASE_INSENSITIVE_MASK) == C.FOLD_O &&
                    (b1 or C.CASE_INSENSITIVE_MASK) == C.FOLD_N -> true
            // "no" / "NO"
            (b0 or C.CASE_INSENSITIVE_MASK) == C.FOLD_N &&
                    (b1 or C.CASE_INSENSITIVE_MASK) == C.FOLD_O -> false

            else -> onError()
        }
    }

    C.BOOL_STR_LEN_3 -> {
        val b0 = getByte(start)
        val b1 = getByte(start + 1)
        val b2 = getByte(start + 2)
        when {
            // "yes" / "YES"
            (b0 or C.CASE_INSENSITIVE_MASK) == C.FOLD_Y &&
                    (b1 or C.CASE_INSENSITIVE_MASK) == C.FOLD_E &&
                    (b2 or C.CASE_INSENSITIVE_MASK) == C.FOLD_S -> true
            // "off" / "OFF"
            (b0 or C.CASE_INSENSITIVE_MASK) == C.FOLD_O &&
                    (b1 or C.CASE_INSENSITIVE_MASK) == C.FOLD_F &&
                    (b2 or C.CASE_INSENSITIVE_MASK) == C.FOLD_F -> false

            else -> onError()
        }
    }

    C.BOOL_STR_LEN_4 -> {
        val b0 = getByte(start)
        val b1 = getByte(start + 1)
        val b2 = getByte(start + 2)
        val b3 = getByte(start + 3)
        // "true" / "TRUE"
        if ((b0 or C.CASE_INSENSITIVE_MASK) == C.FOLD_T &&
            (b1 or C.CASE_INSENSITIVE_MASK) == C.FOLD_R &&
            (b2 or C.CASE_INSENSITIVE_MASK) == C.FOLD_U &&
            (b3 or C.CASE_INSENSITIVE_MASK) == C.FOLD_E
        ) true
        else onError()
    }

    C.BOOL_STR_LEN_5 -> {
        val b0 = getByte(start)
        val b1 = getByte(start + 1)
        val b2 = getByte(start + 2)
        val b3 = getByte(start + 3)
        val b4 = getByte(start + 4)
        // "false" / "FALSE"
        if ((b0 or C.CASE_INSENSITIVE_MASK) == C.FOLD_F &&
            (b1 or C.CASE_INSENSITIVE_MASK) == C.FOLD_A &&
            (b2 or C.CASE_INSENSITIVE_MASK) == C.FOLD_L &&
            (b3 or C.CASE_INSENSITIVE_MASK) == C.FOLD_S &&
            (b4 or C.CASE_INSENSITIVE_MASK) == C.FOLD_E
        ) false
        else onError()
    }

    else -> onError()
}

/**
 * Maps a char-based position in [s] to the corresponding UTF-8 byte offset, without allocating
 * a [ByteArray]. Surrogate pairs count as two `Char`s but four UTF-8 bytes. Used by
 * [GhostJsonStringReader] to bridge char-indexed positions to the byte-indexed positions
 * expected by KSP-generated custom decoders.
 */
@InternalGhostApi
fun charToBytePosition(s: String, charPos: Int): Int {
    var bytePos = 0
    var i = 0
    while (i < charPos && i < s.length) {
        val code = s[i].code
        when {
            code <= C.UTF8_1BYTE_MAX -> {
                bytePos += C.UTF8_1BYTE_SIZE
                i++
            }

            code <= C.UTF8_2BYTE_MAX -> {
                bytePos += C.UTF8_2BYTE_SIZE
                i++
            }

            code in C.HIGH_SURROGATE_START..C.HIGH_SURROGATE_END &&
                    i + 1 < s.length &&
                    s[i + 1].code in C.LOW_SURROGATE_START..C.LOW_SURROGATE_END -> {
                bytePos += C.UTF8_4BYTE_SIZE
                i += 2
            }

            else -> {
                bytePos += C.UTF8_3BYTE_SIZE
                i++
            }
        }
    }
    return bytePos
}

/**
 * Inverse of [charToBytePosition]: maps a UTF-8 byte offset back to the char-indexed position
 * in [s]. Used by the KSP compiler bridge to translate decoder byte offsets back to char
 * positions for the String reader.
 */
@InternalGhostApi
fun byteToCharPosition(s: String, targetBytePos: Int): Int {
    var bytePos = 0
    var i = 0
    while (bytePos < targetBytePos && i < s.length) {
        val code = s[i].code
        when {
            code <= C.UTF8_1BYTE_MAX -> {
                bytePos += C.UTF8_1BYTE_SIZE
                i++
            }

            code <= C.UTF8_2BYTE_MAX -> {
                bytePos += C.UTF8_2BYTE_SIZE
                i++
            }

            code in C.HIGH_SURROGATE_START..C.HIGH_SURROGATE_END &&
                    i + 1 < s.length &&
                    s[i + 1].code in C.LOW_SURROGATE_START..C.LOW_SURROGATE_END -> {
                bytePos += C.UTF8_4BYTE_SIZE
                i += 2
            }

            else -> {
                bytePos += C.UTF8_3BYTE_SIZE
                i++
            }
        }
    }
    return i
}

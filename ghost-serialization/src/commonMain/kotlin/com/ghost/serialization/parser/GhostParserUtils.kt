@file:Suppress("NOTHING_TO_INLINE", "UNNECESSARY_NOT_NULL_ASSERTION")

package com.ghost.serialization.parser

import kotlin.math.pow
import com.ghost.serialization.parser.GhostJsonConstants as C

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
 * Pure, reader-agnostic boolean coercion byte matcher.
 *
 * Compares raw bytes at [start]..[start+length] against the known truthy/falsy
 * coercion strings. No String is allocated. Case-insensitive via bitwise
 * `or CASE_INSENSITIVE_MASK` (= or 32), which collapses ASCII upper/lower pairs.
 *
 * Truthy  (case-insensitive): "true", "yes", "on", "y", "1".
 * Falsy   (case-insensitive): "false", "no", "off", "n", "0".
 *
 * @param start     Index of the first content byte (after the opening `"`).
 * @param length    Number of content bytes (excluding quotes).
 * @param getByte   Reads byte at absolute index as an unsigned int (0-255).
 * @param onError   Called when no coercion match — must throw. The [Nothing] return
 *                  type keeps every branch exhaustive without a dead-code stub.
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
            (b3 or C.CASE_INSENSITIVE_MASK) == C.FOLD_E) true
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
            (b4 or C.CASE_INSENSITIVE_MASK) == C.FOLD_E) false
        else onError()
    }
    else -> onError()
}

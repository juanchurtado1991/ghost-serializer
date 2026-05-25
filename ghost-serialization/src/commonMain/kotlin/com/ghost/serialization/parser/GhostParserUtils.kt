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

@file:Suppress("NOTHING_TO_INLINE")

package com.ghost.serialization.parser.common.json

import com.ghost.serialization.parser.common.json.GhostEiselLemirePowers as POW

/**
 * Eisel–Lemire decimal-to-binary conversion (Lemire, "Number Parsing at a Gigabyte per Second", 2021; the
 * algorithm behind `fast_float`, Go's `strconv` and Rust's `core`). Turns `mantissa × 10^exponent` into a
 * correctly rounded [Double] with one 64×64→128 multiply against [GhostEiselLemirePowers] (two in the rare
 * wide case), without allocating. Returns [NO_RESULT] for the few inputs it cannot decide (exact halfway
 * ties, subnormals, overflow, exponents outside the table); the caller then takes the platform parse.
 *
 * Port of Go's `strconv.eiselLemire64`; the bit tricks follow it line by line so it can be checked against it.
 * The result is the raw bits of a non-negative Double, so [NO_RESULT] (`-1`, sign bit set) can never collide
 * with a valid one. [unsignedMultiplyHigh] is Hacker's Delight `mulhu`, portable across every KMP target.
 */

internal const val NO_RESULT = -1L

private const val DOUBLE_EXPONENT_BIAS = 1023
private const val DOUBLE_EXPONENT_INFINITY = 0x7FF
private const val DOUBLE_MANTISSA_BITS = 52
private const val DOUBLE_MANTISSA_MASK = 0x000F_FFFF_FFFF_FFFFL
private const val HALF_WAY_LOW_BITS = 0x1FFL
private const val LOG2_OF_TEN_Q16 = 217_706
private const val LONG_BITS = 64
private const val LOW_32_MASK = 0xFFFF_FFFFL
private const val MANTISSA_OVERFLOW_SHIFT = 53
private const val Q16_SHIFT = 16
private const val SHIFT_32 = 32
private const val SHIFT_63 = 63
private const val SHIFT_TO_54_BITS = 9

internal inline fun eiselLemireToDoubleBits(
    mantissa: Long,
    exponent: Int
): Long {
    if (mantissa == 0L) return 0L
    val isOutOfTable = exponent < POW.MIN_EXPONENT || exponent > POW.MAX_EXPONENT
    if (isOutOfTable) return NO_RESULT

    val leadingZeros = mantissa.countLeadingZeroBits()
    val normalized = mantissa shl leadingZeros
    val approximateBinaryExponent = (LOG2_OF_TEN_Q16 * exponent) shr Q16_SHIFT
    var binaryExponent = approximateBinaryExponent + LONG_BITS + DOUBLE_EXPONENT_BIAS - leadingZeros
    val tableIndex = exponent - POW.MIN_EXPONENT

    var productHigh = unsignedMultiplyHigh(
        left = normalized,
        right = POW.HIGH[tableIndex]
    )
    var productLow = normalized * POW.HIGH[tableIndex]

    val needsWiderProduct = (productHigh and HALF_WAY_LOW_BITS) == HALF_WAY_LOW_BITS &&
        isUnsignedLess(
            left = productLow + normalized,
            right = normalized
        )
    if (needsWiderProduct) {
        val lowHigh = unsignedMultiplyHigh(
            left = normalized,
            right = POW.LOW[tableIndex]
        )
        val lowLow = normalized * POW.LOW[tableIndex]
        var mergedHigh = productHigh
        val mergedLow = productLow + lowHigh
        val lowWordCarried = isUnsignedLess(
            left = mergedLow,
            right = productLow
        )
        if (lowWordCarried) mergedHigh++
        val isStillAmbiguous = (mergedHigh and HALF_WAY_LOW_BITS) == HALF_WAY_LOW_BITS &&
            mergedLow + 1 == 0L &&
            isUnsignedLess(
                left = lowLow + normalized,
                right = normalized
            )
        if (isStillAmbiguous) return NO_RESULT
        productHigh = mergedHigh
        productLow = mergedLow
    }

    val topBit = (productHigh ushr SHIFT_63).toInt()
    var resultMantissa = productHigh ushr (topBit + SHIFT_TO_54_BITS)
    binaryExponent -= 1 xor topBit

    val isHalfWayTie = productLow == 0L &&
        (productHigh and HALF_WAY_LOW_BITS) == 0L &&
        (resultMantissa and 3L) == 1L
    if (isHalfWayTie) return NO_RESULT

    resultMantissa += resultMantissa and 1L
    resultMantissa = resultMantissa ushr 1
    if ((resultMantissa ushr MANTISSA_OVERFLOW_SHIFT) > 0L) {
        resultMantissa = resultMantissa ushr 1
        binaryExponent++
    }
    val isSubnormalOrInfinite = binaryExponent <= 0 || binaryExponent >= DOUBLE_EXPONENT_INFINITY
    if (isSubnormalOrInfinite) return NO_RESULT

    return (binaryExponent.toLong() shl DOUBLE_MANTISSA_BITS) or (resultMantissa and DOUBLE_MANTISSA_MASK)
}

internal inline fun isUnsignedLess(
    left: Long,
    right: Long
): Boolean = (left xor Long.MIN_VALUE) < (right xor Long.MIN_VALUE)

internal inline fun unsignedMultiplyHigh(
    left: Long,
    right: Long
): Long {
    val leftLow = left and LOW_32_MASK
    val leftHigh = left ushr SHIFT_32
    val rightLow = right and LOW_32_MASK
    val rightHigh = right ushr SHIFT_32
    val partial = leftHigh * rightLow + ((leftLow * rightLow) ushr SHIFT_32)
    val middle = leftLow * rightHigh + (partial and LOW_32_MASK)
    return leftHigh * rightHigh + (partial ushr SHIFT_32) + (middle ushr SHIFT_32)
}

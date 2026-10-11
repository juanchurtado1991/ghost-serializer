package com.ghost.serialization.parser.common

import com.ghost.serialization.parser.common.json.isAmbiguousFloatNarrowing
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

private const val BITS_PER_LIMB = 32
private const val CHUNK_POWER_OF_TEN = 1_000_000_000L
private const val CHUNK_DIGITS = 9
private const val DECIMAL_BASE = 10L
private const val DOUBLE_EXPONENT_BIAS_PLUS_MANTISSA = 1075
private const val DOUBLE_EXPONENT_MASK = 0x7FFL
private const val DOUBLE_IMPLICIT_BIT = 1L shl 52
private const val DOUBLE_MANTISSA_BITS = 52
private const val DOUBLE_MANTISSA_MASK = DOUBLE_IMPLICIT_BIT - 1
private const val FLOAT_OVERFLOW_THRESHOLD = 3.4028235677973366E38
private const val LIMB_MASK = 0xFFFF_FFFFL
private const val MAX_REFINEMENT_STEPS = 4
private const val NEGLIGIBLE_DECIMAL_MAGNITUDE = 400

/**
 * Correctly rounded decimal-to-binary conversion for platforms whose own `String.toDouble()` /
 * `toFloat()` is not (Kotlin/Native and Kotlin/Wasm get hard literals such as
 * `2.2250738585072012e-308` one ulp off). [refineToNearestDouble] starts from the platform's
 * approximation and moves it by single ulps until the literal lies between the midpoints to its
 * neighbours (ties to even), comparing exactly in arbitrary-precision integer arithmetic;
 * [narrowToNearestFloat] narrows that Double and resolves the one case where narrowing rounds
 * twice (the Double sits exactly halfway between two Floats, or on the Float overflow threshold).
 * Literals are non-negative magnitudes in JSON number syntax; literals whose magnitude is beyond
 * ±[NEGLIGIBLE_DECIMAL_MAGNITUDE] decimal orders already round to zero or infinity and are not refined.
 */
internal inline fun correctlyRoundedDouble(
    literal: String,
    nearestDoubleOf: (magnitudeLiteral: String) -> Double
): Double {
    val isNegative = literal.firstOrNull()?.code == TOK.MINUS_INT
    val magnitude = nearestDoubleOf(if (isNegative) literal.substring(startIndex = 1) else literal)
    return if (isNegative) -magnitude else magnitude
}

internal inline fun correctlyRoundedFloat(
    literal: String,
    nearestDoubleOf: (magnitudeLiteral: String) -> Double
): Float {
    val isNegative = literal.firstOrNull()?.code == TOK.MINUS_INT
    val magnitudeLiteral = if (isNegative) literal.substring(startIndex = 1) else literal
    val magnitude = narrowToNearestFloat(
        magnitudeLiteral = magnitudeLiteral,
        nearestDouble = nearestDoubleOf(magnitudeLiteral)
    )
    return if (isNegative) -magnitude else magnitude
}

internal fun narrowToNearestFloat(
    magnitudeLiteral: String,
    nearestDouble: Double
): Float {
    val narrowed = nearestDouble.toFloat()
    if (narrowed.isInfinite()) {
        val isBelowOverflowThreshold = nearestDouble == FLOAT_OVERFLOW_THRESHOLD &&
            compareLiteralToBinary(
                magnitudeLiteral = magnitudeLiteral,
                value = nearestDouble
            ) < 0
        return if (isBelowOverflowThreshold) Float.MAX_VALUE else narrowed
    }
    val isHalfwayBetweenFloats = isAmbiguousFloatNarrowing(
        exact = nearestDouble,
        narrowed = narrowed
    )
    if (!isHalfwayBetweenFloats) {
        return narrowed
    }
    val comparison = compareLiteralToBinary(
        magnitudeLiteral = magnitudeLiteral,
        value = nearestDouble
    )
    val narrowedIsAbove = narrowed.toDouble() > nearestDouble
    return when {
        comparison > 0 && !narrowedIsAbove -> Float.fromBits(bits = narrowed.toRawBits() + 1)
        comparison < 0 && narrowedIsAbove -> Float.fromBits(bits = narrowed.toRawBits() - 1)
        else -> narrowed
    }
}

internal fun refineToNearestDouble(
    magnitudeLiteral: String,
    approximation: Double
): Double {
    val decimal = DecimalLiteral.parse(literal = magnitudeLiteral) ?: return approximation
    val isBeyondDoubleRange = decimal.order > NEGLIGIBLE_DECIMAL_MAGNITUDE ||
        decimal.order < -NEGLIGIBLE_DECIMAL_MAGNITUDE
    if (decimal.isZero || isBeyondDoubleRange) {
        return approximation
    }
    var candidate = approximation
    repeat(MAX_REFINEMENT_STEPS) {
        val step = refinementStep(
            decimal = decimal,
            candidate = candidate
        )
        if (step == 0) return candidate
        candidate = Double.fromBits(bits = candidate.toRawBits() + step)
    }
    return candidate
}

private fun compareLiteralToBinary(
    magnitudeLiteral: String,
    value: Double
): Int {
    val decimal = DecimalLiteral.parse(literal = magnitudeLiteral) ?: return 0
    val (significand, binaryExponent) = decompose(value = value)
    return decimal.compareTo(
        significand = significand,
        binaryExponent = binaryExponent
    )
}

private fun decompose(
    value: Double
): Pair<Long, Int> {
    val bits = value.toRawBits()
    val biasedExponent = ((bits ushr DOUBLE_MANTISSA_BITS) and DOUBLE_EXPONENT_MASK).toInt()
    val fraction = bits and DOUBLE_MANTISSA_MASK
    return if (biasedExponent == 0) {
        fraction to 1 - DOUBLE_EXPONENT_BIAS_PLUS_MANTISSA
    } else {
        (fraction or DOUBLE_IMPLICIT_BIT) to biasedExponent - DOUBLE_EXPONENT_BIAS_PLUS_MANTISSA
    }
}

private fun refinementStep(
    decimal: DecimalLiteral,
    candidate: Double
): Int {
    if (candidate.isInfinite()) {
        val maxDouble = decompose(value = Double.MAX_VALUE)
        val overflowMidpoint = decimal.compareTo(
            significand = 2 * maxDouble.first + 1,
            binaryExponent = maxDouble.second - 1
        )
        return if (overflowMidpoint < 0) -1 else 0
    }
    val (significand, exponent) = decompose(value = candidate)
    val isEven = significand % 2 == 0L
    val upper = decimal.compareTo(
        significand = 2 * significand + 1,
        binaryExponent = exponent - 1
    )
    if (upper > 0 || (upper == 0 && !isEven)) return 1
    if (candidate == 0.0) return 0
    val (previousSignificand, previousExponent) = decompose(
        value = Double.fromBits(bits = candidate.toRawBits() - 1)
    )
    val commonExponent = minOf(
        a = previousExponent,
        b = exponent
    )
    val doubledMidpoint = (previousSignificand shl (previousExponent - commonExponent)) +
        (significand shl (exponent - commonExponent))
    val lower = decimal.compareTo(
        significand = doubledMidpoint,
        binaryExponent = commonExponent - 1
    )
    if (lower < 0 || (lower == 0 && !isEven)) return -1
    return 0
}

/**
 * A decimal literal as `digits * 10^exponent` with exact comparison against a binary value; the
 * private helper of [refineToNearestDouble], kept here because nothing else uses it.
 */
private class DecimalLiteral(
    private val digits: String,
    private val exponent: Int
) {
    val isZero: Boolean = digits.all { it == TOK.CHAR_ZERO }

    val order: Int = exponent + digits.trimStart(TOK.CHAR_ZERO).length

    fun compareTo(
        significand: Long,
        binaryExponent: Int
    ): Int {
        var decimalSide = BigNat.fromDecimal(digits = digits)
        var binarySide = BigNat.fromLong(value = significand)
        if (exponent >= 0) decimalSide = decimalSide.timesPowerOfTen(power = exponent)
        else binarySide = binarySide.timesPowerOfTen(power = -exponent)
        if (binaryExponent >= 0) binarySide = binarySide.shiftLeft(bits = binaryExponent)
        else decimalSide = decimalSide.shiftLeft(bits = -binaryExponent)
        return decimalSide.compareTo(other = binarySide)
    }

    companion object {
        fun parse(
            literal: String
        ): DecimalLiteral? {
            val exponentIndex = literal.indexOfFirst { isExponentMarker(markerByte = it.code) }
            val mantissa = if (exponentIndex < 0) {
                literal
            } else {
                literal.substring(
                    startIndex = 0,
                    endIndex = exponentIndex
                )
            }
            val explicitExponent = if (exponentIndex < 0) {
                0
            } else {
                literal.substring(startIndex = exponentIndex + 1).toIntOrNull() ?: return null
            }
            val dotIndex = mantissa.indexOf(TOK.CHAR_DOT)
            val integerPart = if (dotIndex < 0) {
                mantissa
            } else {
                mantissa.substring(
                    startIndex = 0,
                    endIndex = dotIndex
                )
            }
            val fractionPart = if (dotIndex < 0) "" else mantissa.substring(startIndex = dotIndex + 1)
            val digits = (integerPart + fractionPart).ifEmpty { return null }
            if (!digits.all { isDigit(byteCode = it.code) }) return null
            return DecimalLiteral(
                digits = digits,
                exponent = explicitExponent - fractionPart.length
            )
        }
    }
}

/** Minimal arbitrary-precision natural number (little-endian 32-bit limbs) for [DecimalLiteral]. */
private class BigNat(private val limbs: IntArray) {

    fun compareTo(
        other: BigNat
    ): Int {
        val size = significantSize()
        val otherSize = other.significantSize()
        if (size != otherSize) return size.compareTo(other = otherSize)
        for (index in size - 1 downTo 0) {
            val a = limbs[index].toLong() and LIMB_MASK
            val b = other.limbs[index].toLong() and LIMB_MASK
            if (a != b) return a.compareTo(other = b)
        }
        return 0
    }

    fun shiftLeft(
        bits: Int
    ): BigNat {
        val limbShift = bits / BITS_PER_LIMB
        val bitShift = bits % BITS_PER_LIMB
        val result = IntArray(size = limbs.size + limbShift + 1)
        for (index in limbs.indices) {
            val shifted = (limbs[index].toLong() and LIMB_MASK) shl bitShift
            result[index + limbShift] = result[index + limbShift] or shifted.toInt()
            result[index + limbShift + 1] = (shifted ushr BITS_PER_LIMB).toInt()
        }
        return BigNat(limbs = result)
    }

    fun timesPowerOfTen(
        power: Int
    ): BigNat {
        var result = this
        var remaining = power
        while (remaining >= CHUNK_DIGITS) {
            result = result.timesSmall(factor = CHUNK_POWER_OF_TEN)
            remaining -= CHUNK_DIGITS
        }
        var tail = 1L
        repeat(remaining) { tail *= DECIMAL_BASE }
        return if (tail == 1L) result else result.timesSmall(factor = tail)
    }

    private fun plusSmall(
        value: Long
    ): BigNat {
        val result = limbs.copyOf(newSize = limbs.size + 1)
        var carry = value
        var index = 0
        while (carry != 0L) {
            val sum = (result[index].toLong() and LIMB_MASK) + (carry and LIMB_MASK)
            result[index] = sum.toInt()
            carry = (carry ushr BITS_PER_LIMB) + (sum ushr BITS_PER_LIMB)
            index++
        }
        return BigNat(limbs = result)
    }

    private fun significantSize(): Int {
        var size = limbs.size
        while (size > 0 && limbs[size - 1] == 0) size--
        return size
    }

    private fun timesSmall(
        factor: Long
    ): BigNat {
        val result = IntArray(size = limbs.size + 2)
        var carry = 0L
        for (index in limbs.indices) {
            val product = (limbs[index].toLong() and LIMB_MASK) * factor + carry
            result[index] = product.toInt()
            carry = product ushr BITS_PER_LIMB
        }
        result[limbs.size] = carry.toInt()
        result[limbs.size + 1] = (carry ushr BITS_PER_LIMB).toInt()
        return BigNat(limbs = result)
    }

    companion object {
        fun fromDecimal(
            digits: String
        ): BigNat {
            var result = BigNat(limbs = IntArray(size = 1))
            var index = 0
            while (index < digits.length) {
                val end = minOf(
                    a = digits.length,
                    b = index + CHUNK_DIGITS
                )
                val chunk = digits.substring(
                    startIndex = index,
                    endIndex = end
                ).toLong()
                var scale = 1L
                repeat(end - index) { scale *= DECIMAL_BASE }
                result = result.timesSmall(factor = scale).plusSmall(value = chunk)
                index = end
            }
            return result
        }

        fun fromLong(
            value: Long
        ): BigNat = BigNat(limbs = intArrayOf(value.toInt(), (value ushr BITS_PER_LIMB).toInt()))
    }
}

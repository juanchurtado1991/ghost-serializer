package com.ghost.serialization.parser.common.json

import com.ghost.serialization.parser.common.getFloatPowerOfTen
import com.ghost.serialization.parser.common.isDigit
import com.ghost.serialization.parser.common.isExponentMarker
import com.ghost.serialization.parser.common.constants.GhostJsonErrorMessages as EM
import com.ghost.serialization.parser.common.constants.GhostJsonNumericLimits as NUM
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants as SCN
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants as WR

/**
 * Floating-point value-parsing kernels shared by flat, streaming, and string JSON readers.
 *
 * Reader-specific state (position, getByte, errors) is supplied via inlined adapters so each
 * call site stays monomorphic after inlining.
 */

/**
 * Scales a parsed mantissa/exponent into a finite [Double].
 *
 * Exact for `|exponent| <= 22` (the only range where `10^n` is an exactly-representable Double)
 * or when extra exponent can be folded into the mantissa without exceeding 2^53. Otherwise falls
 * back to string parsing — correctly rounded but allocates; rare in practice, no measurable
 * throughput cost in `benchmarkTwitter`.
 */
internal inline fun finalizeParsedDouble(
    mantissa: Long,
    exponent: Int,
    isNegative: Boolean,
    throwError: (String) -> Nothing,
): Double {
    var result = Double.NaN
    if (mantissa <= NUM.MAX_EXACT_DOUBLE_MANTISSA) {
        if (exponent in 0..NUM.MAX_EXACT_DOUBLE_POWER_OF_TEN) {
            result = mantissa.toDouble() * NUM.POWERS_OF_TEN[exponent]
        } else if (exponent in -NUM.MAX_EXACT_DOUBLE_POWER_OF_TEN..-1) {
            result = mantissa.toDouble() / NUM.POWERS_OF_TEN[-exponent]
        } else if (exponent > NUM.MAX_EXACT_DOUBLE_POWER_OF_TEN) {
            var shift = exponent - NUM.MAX_EXACT_DOUBLE_POWER_OF_TEN
            var shiftedMantissa = mantissa
            while (shift > 0) {
                val next = shiftedMantissa * 10
                if (next / 10 != shiftedMantissa || next > NUM.MAX_EXACT_DOUBLE_MANTISSA) {
                    shiftedMantissa = -1L
                    break
                }
                shiftedMantissa = next
                shift--
            }
            if (shiftedMantissa >= 0) {
                result = shiftedMantissa.toDouble() * NUM.POWERS_OF_TEN[NUM.MAX_EXACT_DOUBLE_POWER_OF_TEN]
            }
        }
    }
    if (result.isNaN()) {
        result = "${mantissa}e$exponent".toDouble()
    }
    if (isNegative) {
        result = -result
    }
    if (result.isInfinite() || result.isNaN()) {
        throwError(EM.ERR_NUMERIC_OVERFLOW)
    }
    return result
}

/**
 * Scales a parsed mantissa/exponent into a finite [Float].
 */
internal inline fun finalizeParsedFloat(
    mantissa: Long,
    exponent: Int,
    isNegative: Boolean,
    throwError: (String) -> Nothing,
): Float {
    var result = mantissa.toFloat()
    if (exponent > 0) {
        result *= getFloatPowerOfTen(exponent = exponent)
    } else if (exponent < 0) {
        // Divide by the exact positive power instead of multiplying by a precomputed
        // reciprocal (1/10^n is itself not exactly representable for n >= 1) — division
        // by an exact operand is correctly rounded, multiplication by the inexact
        // reciprocal compounds rounding error (e.g. 98.6 -> 98.60000000000001).
        result /= getFloatPowerOfTen(exponent = -exponent)
    }
    if (isNegative) {
        result = -result
    }
    if (result.isInfinite() || result.isNaN()) {
        throwError(EM.ERR_NUMERIC_OVERFLOW)
    }
    return result
}

/**
 * Parses the exponent after an `e`/`E` marker at [startPosition].
 *
 * @param startPosition Index of the `e`/`E` marker.
 * @return Signed exponent value; [setPosition] receives the index after the last digit.
 */
internal inline fun parseExponentValueCore(
    startPosition: Int,
    limit: Int,
    getByte: (Int) -> Int,
    setPosition: (Int) -> Unit,
    throwError: (String) -> Nothing,
): Int {
    var position = startPosition + 1
    var isExpNegative = false
    if (position < limit) {
        val marker = getByte(position)
        if (marker == TOK.MINUS_INT) {
            isExpNegative = true
            position++
        } else if (marker == TOK.PLUS_INT) {
            position++
        }
    }

    var expValue = 0
    var hasExpDigits = false
    while (position < limit) {
        val currentByteInt = getByte(position)
        if (isDigit(byteCode = currentByteInt)) {
            if (expValue < NUM.EXPONENT_CLAMP_THRESHOLD) {
                expValue = expValue * WR.BASE_TEN + (currentByteInt - TOK.ZERO_INT)
            }
            hasExpDigits = true
            position++
        } else {
            break
        }
    }

    if (!hasExpDigits) {
        throwError(EM.ERR_EXPECTED_EXPONENT_DIGITS)
    }
    setPosition(position)
    return if (isExpNegative) -expValue else expValue
}

/**
 * Parses a JSON float body (int digits, optional fraction, optional exponent) after the
 * header/leading-zero check. Both digit runs try [tryParseEightDigitsAt] first when
 * `precisionLimit` has room for a full chunk and [allowBulkDigitRead] is set — the streaming
 * channel passes `false` because its Okio-backed source releases consumed segments, so an
 * 8-byte-ahead speculative read can run past the retained window near the end of the document.
 */
internal inline fun <R> parseJsonFloatingBodyCore(
    precisionLimit: Int,
    allowBulkDigitRead: Boolean,
    getPosition: () -> Int,
    setPosition: (Int) -> Unit,
    limit: Int,
    getByte: (Int) -> Int,
    parseExponentValue: () -> Int,
    throwError: (String) -> Nothing,
    finish: (mantissa: Long, exponent: Int) -> R,
): R {
    var mantissa = 0L
    var exponent = 0
    var digitCount = 0
    var sawIntDigit = false
    var position = getPosition()

    while (position < limit) {
        // Only past the leading-zero case below (digitCount > 0), and only when a full 8-digit
        // chunk fits the precision budget — the byte-by-byte loop already handles the tail.
        val canReadBulkDigits = allowBulkDigitRead && digitCount > 0 && digitCount + 8 <= precisionLimit
        if (canReadBulkDigits) {
            val eight = tryParseEightDigitsAt(
                position = position,
                limit = limit,
                getByte = getByte
            )
            if (eight >= 0) {
                mantissa = mantissa * SCN.HUNDRED_MILLION + eight
                digitCount += 8
                position += 8
                continue
            }
        }
        val byte = getByte(position)
        if (!isDigit(byteCode = byte)) break
        val digit = byte - TOK.ZERO_INT
        sawIntDigit = true
        // A sole leading "0" (validateLeadingZero rejects "0" + another digit) carries no
        // precision — don't let it consume a `precisionLimit` slot, or max-precision fractions
        // lose their last digit (e.g. "0.30000000000000004" needs all 17 fraction digits).
        if (digitCount == 0 && digit == 0) {
            position++
            continue
        }
        if (digitCount < precisionLimit) {
            mantissa = mantissa * WR.BASE_TEN + digit
            digitCount++
        } else {
            exponent++
        }
        position++
    }

    if (!sawIntDigit) {
        setPosition(position)
        throwError(EM.ERR_EXPECTED_INT_PART)
    }

    if (position < limit && getByte(position) == TOK.DOT_INT) {
        position++
        val fractionStart = position
        while (position < limit) {
            if (allowBulkDigitRead && digitCount + 8 <= precisionLimit) {
                val eight = tryParseEightDigitsAt(
                    position = position,
                    limit = limit,
                    getByte = getByte
                )
                if (eight >= 0) {
                    mantissa = mantissa * SCN.HUNDRED_MILLION + eight
                    digitCount += 8
                    exponent -= 8
                    position += 8
                    continue
                }
            }
            val byte = getByte(position)
            if (!isDigit(byteCode = byte)) break
            val digit = byte - TOK.ZERO_INT
            if (digitCount < precisionLimit) {
                mantissa = mantissa * WR.BASE_TEN + digit
                digitCount++
                exponent--
            }
            position++
        }
        if (position == fractionStart) {
            setPosition(position)
            throwError(EM.ERR_EXPECTED_DECIMAL_DIGITS)
        }
    }

    setPosition(position)
    if (position < limit && isExponentMarker(markerByte = getByte(position))) {
        exponent += parseExponentValue()
    }

    return finish(mantissa, exponent)
}

/**
 * Reads 8 consecutive ASCII digit bytes at [position] into a 0..99999999 [Int] in one pass
 * (fast_float/simdjson's `parse_eight_digits_unrolled`). Returns -1 if fewer than 8 bytes remain
 * or any isn't a digit — caller falls back to byte-at-a-time. Only used by
 * [parseJsonFloatingBodyCore], so kept private to this file.
 */
private inline fun tryParseEightDigitsAt(
    position: Int,
    limit: Int,
    getByte: (Int) -> Int
): Int {
    if (position + 8 > limit) return -1
    var packed = 0L
    for (i in 0 until 8) {
        packed = packed or ((getByte(position + i).toLong() and 0xFF) shl (8 * i))
    }
    val hasNonDigitByte = (((packed + SCN.EIGHT_DIGITS_CHECK_ADD) or (packed - SCN.EIGHT_DIGITS_ASCII_ZERO)) and
        SCN.EIGHT_DIGITS_HIGH_BIT) != 0L
    if (hasNonDigitByte) {
        return -1
    }
    var value = packed - SCN.EIGHT_DIGITS_ASCII_ZERO
    value = (value * 10) + (value ushr 8)
    value = (((value and SCN.EIGHT_DIGITS_MASK) * SCN.EIGHT_DIGITS_MUL1) +
        (((value ushr 16) and SCN.EIGHT_DIGITS_MASK) * SCN.EIGHT_DIGITS_MUL2)) ushr 32
    return value.toInt()
}

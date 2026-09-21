@file:Suppress("NOTHING_TO_INLINE")

package com.ghost.serialization.parser.common

import com.ghost.serialization.parser.common.GhostJsonConstants as C

/**
 * Shared numeric-parse kernels for flat, streaming, and string JSON readers.
 *
 * Reader-specific state (position, nextTokenByte, getByte, skipWhitespace, errors) is
 * supplied via inlined adapters so each call site stays monomorphic after inlining.
 */

/**
 * Prepares the numeric header: optional coercion quote and leading minus.
 *
 * @return Bitmask of [C.NUMERIC_HEADER_QUOTED] and/or [C.NUMERIC_HEADER_NEGATIVE].
 */
internal inline fun prepareNumericHeaderCore(
    getNextTokenByte: () -> Int,
    setNextTokenByte: (Int) -> Unit,
    getPosition: () -> Int,
    setPosition: (Int) -> Unit,
    limit: Int,
    coerceStringsToNumbers: Boolean,
    skipWhitespace: () -> Unit,
    throwError: (String) -> Nothing,
): Int {
    if (getNextTokenByte() == C.RESET_TOKEN_BYTE) {
        skipWhitespace()
    }
    if (getPosition() >= limit) {
        throwError(C.ERR_EXPECTED_NUMBER)
    }

    var header = 0
    var token = getNextTokenByte()

    if (token == C.QUOTE_INT) {
        if (!coerceStringsToNumbers) {
            throwError(C.ERR_COERCION_DISABLED)
        }
        setPosition(getPosition() + 1)
        setNextTokenByte(C.RESET_TOKEN_BYTE)
        skipWhitespace()
        if (getPosition() >= limit) {
            throwError(C.ERR_EXPECTED_NUMBER)
        }
        token = getNextTokenByte()
        header = header or C.NUMERIC_HEADER_QUOTED
    }

    if (token == C.MINUS_INT) {
        if (getPosition() + 1 >= limit) {
            throwError(C.ERR_ISOLATED_MINUS)
        }
        setPosition(getPosition() + 1)
        setNextTokenByte(C.RESET_TOKEN_BYTE)
        header = header or C.NUMERIC_HEADER_NEGATIVE
    }

    return header
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
        if (marker == C.MINUS_INT) {
            isExpNegative = true
            position++
        } else if (marker == C.PLUS_INT) {
            position++
        }
    }

    var expValue = 0
    var hasExpDigits = false
    while (position < limit) {
        val currentByteInt = getByte(position)
        if (isDigit(currentByteInt)) {
            if (expValue < C.EXPONENT_CLAMP_THRESHOLD) {
                expValue = expValue * C.BASE_TEN + (currentByteInt - C.ZERO_INT)
            }
            hasExpDigits = true
            position++
        } else {
            break
        }
    }

    if (!hasExpDigits) {
        throwError(C.ERR_EXPECTED_EXPONENT_DIGITS)
    }
    setPosition(position)
    return if (isExpNegative) -expValue else expValue
}

/**
 * Consumes the closing `"` after a coerced numeric string value.
 */
internal inline fun consumeNumericCoercionFooterCore(
    position: Int,
    limit: Int,
    getByte: (Int) -> Int,
    throwError: (String) -> Nothing,
    afterQuote: () -> Unit,
) {
    if (position >= limit || getByte(position) != C.QUOTE_INT) {
        throwError(C.ERR_EXPECTED_COERCION_QUOTE)
    }
    afterQuote()
}

/**
 * Asserts that a leading `0` is not followed by another digit.
 */
internal inline fun validateLeadingZeroCore(
    position: Int,
    limit: Int,
    getByte: (Int) -> Int,
    throwError: (String) -> Nothing,
) {
    if (position < limit && getByte(position) == C.ZERO_INT && position + 1 < limit) {
        val nextDigitByte = getByte(position + 1)
        if (nextDigitByte in C.ZERO_INT..C.NINE_INT) {
            throwError(C.ERR_LEADING_ZEROS)
        }
    }
}

/**
 * Consumes a lone leading `0` (used by int/long fast paths) and rejects `0` + digit.
 */
internal inline fun handleLeadingZeroCore(
    position: Int,
    limit: Int,
    getByte: (Int) -> Int,
    throwError: (String) -> Nothing,
    consumeOne: () -> Unit,
) {
    val nextCursor = position + 1
    if (nextCursor < limit) {
        val nextDigitByte = getByte(nextCursor)
        if (nextDigitByte in C.ZERO_INT..C.NINE_INT) {
            throwError(C.ERR_LEADING_ZEROS)
        }
    }
    consumeOne()
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
        result *= getFloatPowerOfTen(exponent)
    } else if (exponent < 0) {
        // Divide by the exact positive power instead of multiplying by a precomputed
        // reciprocal (1/10^n is itself not exactly representable for n >= 1) — division
        // by an exact operand is correctly rounded, multiplication by the inexact
        // reciprocal compounds rounding error (e.g. 98.6 -> 98.60000000000001).
        result /= getFloatPowerOfTen(-exponent)
    }
    if (isNegative) {
        result = -result
    }
    if (result.isInfinite() || result.isNaN()) {
        throwError(C.ERR_NUMERIC_OVERFLOW)
    }
    return result
}

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
    if (mantissa <= C.MAX_EXACT_DOUBLE_MANTISSA) {
        if (exponent in 0..C.MAX_EXACT_DOUBLE_POWER_OF_TEN) {
            result = mantissa.toDouble() * C.POWERS_OF_TEN[exponent]
        } else if (exponent in -C.MAX_EXACT_DOUBLE_POWER_OF_TEN..-1) {
            result = mantissa.toDouble() / C.POWERS_OF_TEN[-exponent]
        } else if (exponent > C.MAX_EXACT_DOUBLE_POWER_OF_TEN) {
            var shift = exponent - C.MAX_EXACT_DOUBLE_POWER_OF_TEN
            var shiftedMantissa = mantissa
            while (shift > 0) {
                val next = shiftedMantissa * 10
                if (next / 10 != shiftedMantissa || next > C.MAX_EXACT_DOUBLE_MANTISSA) {
                    shiftedMantissa = -1L
                    break
                }
                shiftedMantissa = next
                shift--
            }
            if (shiftedMantissa >= 0) {
                result = shiftedMantissa.toDouble() * C.POWERS_OF_TEN[C.MAX_EXACT_DOUBLE_POWER_OF_TEN]
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
        throwError(C.ERR_NUMERIC_OVERFLOW)
    }
    return result
}

// Quoted verbatim from fast_float's ascii_number.h (parse_eight_digits_unrolled /
// is_made_of_eight_digits_fast) rather than hand-derived. Exhaustively verified against all
// 100,000,000 possible 8-digit inputs before use.
private const val EIGHT_DIGITS_MASK = 0x000000FF000000FFL
private const val EIGHT_DIGITS_MUL1 = 0x000F424000000064L
private const val EIGHT_DIGITS_MUL2 = 0x0000271000000001L
private const val EIGHT_DIGITS_ASCII_ZERO = 0x3030303030303030L
private const val EIGHT_DIGITS_CHECK_ADD = 0x4646464646464646L
private val EIGHT_DIGITS_HIGH_BIT = 0x8080808080808080UL.toLong()

/** `10^8`, the multiplier to fold an 8-digit SWAR chunk into an existing mantissa. */
private const val HUNDRED_MILLION = 100_000_000L

/**
 * Reads 8 consecutive ASCII digit bytes at [position] into a 0..99999999 [Int] in one pass
 * (fast_float/simdjson's `parse_eight_digits_unrolled`). Returns -1 if fewer than 8 bytes remain
 * or any isn't a digit — caller falls back to byte-at-a-time.
 */
internal inline fun tryParseEightDigitsAt(position: Int, limit: Int, getByte: (Int) -> Int): Int {
    if (position + 8 > limit) return -1
    var packed = 0L
    for (i in 0 until 8) {
        packed = packed or ((getByte(position + i).toLong() and 0xFF) shl (8 * i))
    }
    if ((((packed + EIGHT_DIGITS_CHECK_ADD) or (packed - EIGHT_DIGITS_ASCII_ZERO)) and EIGHT_DIGITS_HIGH_BIT) != 0L) {
        return -1
    }
    var value = packed - EIGHT_DIGITS_ASCII_ZERO
    value = (value * 10) + (value ushr 8)
    value = (((value and EIGHT_DIGITS_MASK) * EIGHT_DIGITS_MUL1) +
        (((value ushr 16) and EIGHT_DIGITS_MASK) * EIGHT_DIGITS_MUL2)) ushr 32
    return value.toInt()
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
        if (allowBulkDigitRead && digitCount > 0 && digitCount + 8 <= precisionLimit) {
            val eight = tryParseEightDigitsAt(position, limit, getByte)
            if (eight >= 0) {
                mantissa = mantissa * HUNDRED_MILLION + eight
                digitCount += 8
                position += 8
                continue
            }
        }
        val byte = getByte(position)
        if (!isDigit(byte)) break
        val digit = byte - C.ZERO_INT
        sawIntDigit = true
        // A sole leading "0" (validateLeadingZero rejects "0" + another digit) carries no
        // precision — don't let it consume a `precisionLimit` slot, or max-precision fractions
        // lose their last digit (e.g. "0.30000000000000004" needs all 17 fraction digits).
        if (digitCount == 0 && digit == 0) {
            position++
            continue
        }
        if (digitCount < precisionLimit) {
            mantissa = mantissa * C.BASE_TEN + digit
            digitCount++
        } else {
            exponent++
        }
        position++
    }

    if (!sawIntDigit) {
        setPosition(position)
        throwError(C.ERR_EXPECTED_INT_PART)
    }

    if (position < limit && getByte(position) == C.DOT_INT) {
        position++
        val fractionStart = position
        while (position < limit) {
            if (allowBulkDigitRead && digitCount + 8 <= precisionLimit) {
                val eight = tryParseEightDigitsAt(position, limit, getByte)
                if (eight >= 0) {
                    mantissa = mantissa * HUNDRED_MILLION + eight
                    digitCount += 8
                    exponent -= 8
                    position += 8
                    continue
                }
            }
            val byte = getByte(position)
            if (!isDigit(byte)) break
            val digit = byte - C.ZERO_INT
            if (digitCount < precisionLimit) {
                mantissa = mantissa * C.BASE_TEN + digit
                digitCount++
                exponent--
            }
            position++
        }
        if (position == fractionStart) {
            setPosition(position)
            throwError(C.ERR_EXPECTED_DECIMAL_DIGITS)
        }
    }

    setPosition(position)
    if (position < limit && isExponentMarker(getByte(position))) {
        exponent += parseExponentValue()
    }

    return finish(mantissa, exponent)
}

/**
 * Skips a JSON number body (integer/fraction/exponent) after the numeric header. Flat and
 * string readers share this via [getByte]; streaming keeps its own [readNumericLoop]-based
 * skip to avoid cross-buffer coupling here.
 */
internal inline fun skipNumberBodyCore(
    getPosition: () -> Int,
    setPosition: (Int) -> Unit,
    limit: Int,
    getByte: (Int) -> Int,
    throwError: (String) -> Nothing,
) {
    var hasDigits = false
    var position = getPosition()

    if (position < limit && getByte(position) == C.ZERO_INT) {
        val newPos = position + 1
        position = newPos
        hasDigits = true
        if (newPos < limit && isDigit(getByte(newPos))) {
            throwError(C.ERR_LEADING_ZEROS)
        }
    } else {
        while (position < limit) {
            val byte = getByte(position)
            if (isDigit(byte)) {
                hasDigits = true
                position++
            } else {
                break
            }
        }
    }

    if (!hasDigits) {
        throwError(C.ERR_EXPECTED_INT_PART)
    }

    if (position < limit && getByte(position) == C.DOT_INT) {
        position++
        var hasDecimalDigits = false
        while (position < limit) {
            val byte = getByte(position)
            if (isDigit(byte)) {
                hasDecimalDigits = true
                position++
            } else {
                break
            }
        }
        if (!hasDecimalDigits) {
            throwError(C.ERR_EXPECTED_DECIMAL_DIGITS)
        }
    }

    if (position < limit) {
        val byte = getByte(position)
        if (byte == C.EXP_LOWER_INT || byte == C.EXP_UPPER_INT) {
            var newPos = position + 1
            position = newPos
            if (newPos < limit) {
                val sign = getByte(newPos)
                if (sign == C.PLUS_INT || sign == C.MINUS_INT) {
                    newPos++
                    position = newPos
                }
            }

            var hasExpDigits = false
            while (position < limit) {
                val byteCode = getByte(position)
                if (isDigit(byteCode)) {
                    hasExpDigits = true
                    position++
                } else {
                    break
                }
            }
            if (!hasExpDigits) {
                throwError(C.ERR_EXPECTED_EXPONENT_DIGITS)
            }
        }
    }

    setPosition(position)
}

/**
 * Accumulates an [Int] from a digit run with overflow checks; early-exits on a
 * fractional/exponent separator (caller rewinds and parses as floating). Digit walk lives
 * in this core (same shape as [skipNumberBodyCore]) to stay monomorphic after inlining —
 * avoid nested function-type callbacks that can allocate.
 */
internal inline fun parseIntDigitsCore(
    isNegative: Boolean,
    resetNextTokenByte: () -> Unit,
    getPosition: () -> Int,
    setPosition: (Int) -> Unit,
    limit: Int,
    getByte: (Int) -> Int,
    onNumericSeparator: () -> Int,
    throwError: (String) -> Nothing,
): Int {
    var accumulatedValue = 0
    var digitCount = 0
    var hasDigitsFound = false
    resetNextTokenByte()
    var position = getPosition()

    while (position < limit) {
        val byte = getByte(position)
        if (isDigit(byte)) {
            val digit = byte - C.ZERO_INT
            accumulatedValue = if (digitCount < C.INT_SAFE_DIGITS) {
                accumulatedValue * C.BASE_TEN + digit
            } else {
                accumulateIntWithOverflowCheck(accumulatedValue, digit, isNegative) {
                    throwError(C.ERR_INT_OVERFLOW)
                }
            }
            digitCount++
            hasDigitsFound = true
            position++
        } else {
            setPosition(position)
            if (isNumericSeparator(byte)) {
                return onNumericSeparator()
            }
            break
        }
    }
    setPosition(position)
    if (!hasDigitsFound) {
        throwError(C.ERR_EXPECTED_INT_PART)
    }
    return accumulatedValue
}

/**
 * Accumulates a [Long] from a digit run; same early-exit contract as [parseIntDigitsCore].
 */
internal inline fun parseLongDigitsCore(
    isNegative: Boolean,
    resetNextTokenByte: () -> Unit,
    getPosition: () -> Int,
    setPosition: (Int) -> Unit,
    limit: Int,
    getByte: (Int) -> Int,
    onNumericSeparator: () -> Long,
    throwError: (String) -> Nothing,
): Long {
    var accumulatedValue = 0L
    var digitCount = 0
    var hasDigitsFound = false
    resetNextTokenByte()
    var position = getPosition()

    while (position < limit) {
        val byte = getByte(position)
        if (isDigit(byte)) {
            val digit = byte - C.ZERO_INT
            accumulatedValue = if (digitCount < C.LONG_SAFE_DIGITS) {
                accumulatedValue * C.BASE_TEN + digit
            } else {
                accumulateLongWithOverflowCheck(accumulatedValue, digit, isNegative) {
                    throwError(C.ERR_LONG_OVERFLOW)
                }
            }
            digitCount++
            hasDigitsFound = true
            position++
        } else {
            setPosition(position)
            if (isNumericSeparator(byte)) {
                return onNumericSeparator()
            }
            break
        }
    }
    setPosition(position)
    if (!hasDigitsFound) {
        throwError(C.ERR_EXPECTED_INT_PART)
    }
    return accumulatedValue
}

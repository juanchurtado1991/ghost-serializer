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
 */
internal inline fun finalizeParsedDouble(
    mantissa: Long,
    exponent: Int,
    isNegative: Boolean,
    throwError: (String) -> Nothing,
): Double {
    var result = mantissa.toDouble()
    if (exponent > 0) {
        result *= getDoublePowerOfTen(exponent)
    } else if (exponent < 0) {
        // See finalizeParsedFloat: divide by the exact positive power rather than
        // multiplying by the precomputed (inexact) reciprocal.
        result /= getDoublePowerOfTen(-exponent)
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
 * Parses a JSON float body (int digits, optional fraction, optional exponent) after the
 * header/leading-zero check. Digit runs walk via [getByte]/[setPosition] (same allocation-safe
 * shape as [parseIntDigitsCore]) — don't pass nested `readDigitRun` callbacks.
 */
internal inline fun <R> parseJsonFloatingBodyCore(
    precisionLimit: Int,
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
    var position = getPosition()

    while (position < limit) {
        val byte = getByte(position)
        if (!isDigit(byte)) break
        val digit = byte - C.ZERO_INT
        if (digitCount < precisionLimit) {
            mantissa = mantissa * C.BASE_TEN + digit
            digitCount++
        } else {
            exponent++
        }
        position++
    }

    if (digitCount == 0) {
        setPosition(position)
        throwError(C.ERR_EXPECTED_INT_PART)
    }

    if (position < limit && getByte(position) == C.DOT_INT) {
        position++
        val fractionStart = position
        while (position < limit) {
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

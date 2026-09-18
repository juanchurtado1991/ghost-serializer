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
 * Exact for `|exponent| <= 22` (the only range where `10^n` is itself an exactly-representable
 * Double) and, beyond that, for any exponent whose extra digits can still be folded into the
 * mantissa without exceeding 2^53 (e.g. "1e30" with a small mantissa) — both cases are a single
 * correctly-rounded FP operation, same cost as before. Outside that — a mantissa already at the
 * 2^53 boundary, or a large exponent paired with a high-precision mantissa — falls back to
 * string parsing: correctly rounded on every target, but allocates. That fallback is rare for
 * everyday JSON (prices, coordinates, timestamps all stay well inside the exact range), and
 * benchmarking against real documents (Twitter macro dataset, `benchmarkTwitter`) showed no
 * measurable throughput regression from adding it.
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
    var sawIntDigit = false
    var position = getPosition()

    while (position < limit) {
        val byte = getByte(position)
        if (!isDigit(byte)) break
        val digit = byte - C.ZERO_INT
        sawIntDigit = true
        // A sole leading "0" (the only way a '0' can start the integer part — validateLeadingZero
        // already rejects "0" followed by another digit here) carries no significant precision:
        // e.g. "0.30000000000000004" has 17 significant *fraction* digits, not 16. Don't let this
        // placeholder consume a slot of `precisionLimit`, or a legitimate max-precision fraction
        // (exactly what Double/Float.toString() emits for values needing every digit) gets its
        // last digit silently dropped on read.
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

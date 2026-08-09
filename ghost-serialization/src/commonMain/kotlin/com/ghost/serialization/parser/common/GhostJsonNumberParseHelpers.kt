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

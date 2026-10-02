package com.ghost.serialization.parser.common.json

import com.ghost.serialization.parser.common.isDigit
import com.ghost.serialization.parser.common.constants.GhostJsonErrorMessages as EM
import com.ghost.serialization.parser.common.constants.GhostJsonNumericLimits as NUM
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants as SCN
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

/**
 * Numeric header/footer/leading-zero/skip kernels shared by flat, streaming, and string JSON
 * readers — validate or skip parts of a number without producing a value.
 *
 * Reader-specific state (position, nextTokenByte, getByte, skipWhitespace, errors) is
 * supplied via inlined adapters so each call site stays monomorphic after inlining.
 */

/**
 * Prepares the numeric header: optional coercion quote and leading minus.
 *
 * @return Bitmask of [NUM.NUMERIC_HEADER_QUOTED] and/or [NUM.NUMERIC_HEADER_NEGATIVE].
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
    if (getNextTokenByte() == SCN.RESET_TOKEN_BYTE) {
        skipWhitespace()
    }
    if (getPosition() >= limit) {
        throwError(EM.ERR_EXPECTED_NUMBER)
    }

    var header = 0
    var token = getNextTokenByte()

    if (token == TOK.QUOTE_INT) {
        if (!coerceStringsToNumbers) {
            throwError(EM.ERR_COERCION_DISABLED)
        }
        setPosition(getPosition() + 1)
        setNextTokenByte(SCN.RESET_TOKEN_BYTE)
        skipWhitespace()
        if (getPosition() >= limit) {
            throwError(EM.ERR_EXPECTED_NUMBER)
        }
        token = getNextTokenByte()
        header = header or NUM.NUMERIC_HEADER_QUOTED
    }

    if (token == TOK.MINUS_INT) {
        if (getPosition() + 1 >= limit) {
            throwError(EM.ERR_ISOLATED_MINUS)
        }
        setPosition(getPosition() + 1)
        setNextTokenByte(SCN.RESET_TOKEN_BYTE)
        header = header or NUM.NUMERIC_HEADER_NEGATIVE
    }

    return header
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
    if (position >= limit || getByte(position) != TOK.QUOTE_INT) {
        throwError(EM.ERR_EXPECTED_COERCION_QUOTE)
    }
    afterQuote()
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
        if (nextDigitByte in TOK.ZERO_INT..TOK.NINE_INT) {
            throwError(EM.ERR_LEADING_ZEROS)
        }
    }
    consumeOne()
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
    val hasLeadingZeroFollowedByMore = position < limit && getByte(position) == TOK.ZERO_INT && position + 1 < limit
    if (hasLeadingZeroFollowedByMore) {
        val nextDigitByte = getByte(position + 1)
        if (nextDigitByte in TOK.ZERO_INT..TOK.NINE_INT) {
            throwError(EM.ERR_LEADING_ZEROS)
        }
    }
}

/**
 * Skips a JSON number body (integer/fraction/exponent) after the numeric header. Flat and
 * string readers share this via [getByte]; streaming keeps its own `readNumericLoop`-based
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

    if (position < limit && getByte(position) == TOK.ZERO_INT) {
        val newPos = position + 1
        position = newPos
        hasDigits = true
        if (newPos < limit && isDigit(byteCode = getByte(newPos))) {
            throwError(EM.ERR_LEADING_ZEROS)
        }
    } else {
        while (position < limit) {
            val byte = getByte(position)
            if (isDigit(byteCode = byte)) {
                hasDigits = true
                position++
            } else {
                break
            }
        }
    }

    if (!hasDigits) {
        throwError(EM.ERR_EXPECTED_INT_PART)
    }

    if (position < limit && getByte(position) == TOK.DOT_INT) {
        position++
        var hasDecimalDigits = false
        while (position < limit) {
            val byte = getByte(position)
            if (isDigit(byteCode = byte)) {
                hasDecimalDigits = true
                position++
            } else {
                break
            }
        }
        if (!hasDecimalDigits) {
            throwError(EM.ERR_EXPECTED_DECIMAL_DIGITS)
        }
    }

    if (position < limit) {
        val byte = getByte(position)
        if (byte == TOK.EXP_LOWER_INT || byte == TOK.EXP_UPPER_INT) {
            var newPos = position + 1
            position = newPos
            if (newPos < limit) {
                val sign = getByte(newPos)
                if (sign == TOK.PLUS_INT || sign == TOK.MINUS_INT) {
                    newPos++
                    position = newPos
                }
            }

            var hasExpDigits = false
            while (position < limit) {
                val byteCode = getByte(position)
                if (isDigit(byteCode = byteCode)) {
                    hasExpDigits = true
                    position++
                } else {
                    break
                }
            }
            if (!hasExpDigits) {
                throwError(EM.ERR_EXPECTED_EXPONENT_DIGITS)
            }
        }
    }

    setPosition(position)
}

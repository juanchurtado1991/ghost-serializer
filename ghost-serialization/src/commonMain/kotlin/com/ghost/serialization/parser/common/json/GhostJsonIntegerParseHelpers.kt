package com.ghost.serialization.parser.common.json

import com.ghost.serialization.parser.common.accumulateIntWithOverflowCheck
import com.ghost.serialization.parser.common.accumulateLongWithOverflowCheck
import com.ghost.serialization.parser.common.isDigit
import com.ghost.serialization.parser.common.isNumericSeparator
import com.ghost.serialization.parser.common.constants.GhostJsonErrorMessages as EM
import com.ghost.serialization.parser.common.constants.GhostJsonNumericLimits as NUM
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants as WR

/**
 * Integer/Long digit-accumulation kernels shared by flat, streaming, and string JSON readers.
 *
 * Reader-specific state (position, getByte, errors) is supplied via inlined adapters so each
 * call site stays monomorphic after inlining.
 */

/**
 * Accumulates an [Int] from a digit run with overflow checks; early-exits on a
 * fractional/exponent separator (caller rewinds and parses as floating). Digit walk lives
 * in this core (same shape as `skipNumberBodyCore`) to stay monomorphic after inlining —
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
        if (isDigit(byteCode = byte)) {
            val digit = byte - TOK.ZERO_INT
            accumulatedValue = if (digitCount < NUM.INT_SAFE_DIGITS) {
                accumulatedValue * WR.BASE_TEN + digit
            } else {
                accumulateIntWithOverflowCheck(
                    current = accumulatedValue,
                    digitValue = digit,
                    isNegative = isNegative
                ) {
                    throwError(EM.ERR_INT_OVERFLOW)
                }
            }
            digitCount++
            hasDigitsFound = true
            position++
        } else {
            setPosition(position)
            if (isNumericSeparator(byteCode = byte)) {
                return onNumericSeparator()
            }
            break
        }
    }
    setPosition(position)
    if (!hasDigitsFound) {
        throwError(EM.ERR_EXPECTED_INT_PART)
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
        if (isDigit(byteCode = byte)) {
            val digit = byte - TOK.ZERO_INT
            accumulatedValue = if (digitCount < NUM.LONG_SAFE_DIGITS) {
                accumulatedValue * WR.BASE_TEN + digit
            } else {
                accumulateLongWithOverflowCheck(
                    current = accumulatedValue,
                    digitValue = digit,
                    isNegative = isNegative
                ) {
                    throwError(EM.ERR_LONG_OVERFLOW)
                }
            }
            digitCount++
            hasDigitsFound = true
            position++
        } else {
            setPosition(position)
            if (isNumericSeparator(byteCode = byte)) {
                return onNumericSeparator()
            }
            break
        }
    }
    setPosition(position)
    if (!hasDigitsFound) {
        throwError(EM.ERR_EXPECTED_INT_PART)
    }
    return accumulatedValue
}

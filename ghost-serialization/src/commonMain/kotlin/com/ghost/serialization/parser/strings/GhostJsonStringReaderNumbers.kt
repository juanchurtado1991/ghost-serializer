@file:Suppress("NOTHING_TO_INLINE")
@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser.strings

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.accumulateIntWithOverflowCheck
import com.ghost.serialization.parser.common.accumulateLongWithOverflowCheck
import com.ghost.serialization.parser.common.consumeNumericCoercionFooterCore
import com.ghost.serialization.parser.common.finalizeParsedDouble
import com.ghost.serialization.parser.common.finalizeParsedFloat
import com.ghost.serialization.parser.common.isDigit
import com.ghost.serialization.parser.common.isNumericSeparator
import com.ghost.serialization.parser.common.parseExponentValueCore
import com.ghost.serialization.parser.common.parseJsonFloatingBodyCore
import com.ghost.serialization.parser.common.prepareNumericHeaderCore
import com.ghost.serialization.parser.common.skipNumberBodyCore
import com.ghost.serialization.parser.common.validateLeadingZeroCore
import com.ghost.serialization.parser.common.GhostJsonConstants as C

fun GhostJsonStringReader.nextInt(): Int {
    val header = prepareNumericHeader()
    val isQuoted = (header and C.NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and C.NUMERIC_HEADER_NEGATIVE) != 0

    val startOfNumber = position
    validateLeadingZero()

    val accumulatedValue = parseIntDigits(isNegativeValue, startOfNumber)

    if (isQuoted) {
        consumeNumericCoercionFooter()
    }
    nextTokenByte = C.RESET_TOKEN_BYTE

    return if (isNegativeValue) -accumulatedValue else accumulatedValue
}

fun GhostJsonStringReader.nextLong(): Long {
    val header = prepareNumericHeader()
    val isQuoted = (header and C.NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and C.NUMERIC_HEADER_NEGATIVE) != 0

    val startOfNumber = position
    validateLeadingZero()

    val accumulatedValue = parseLongDigits(isNegativeValue, startOfNumber)

    if (isQuoted) {
        consumeNumericCoercionFooter()
    }
    nextTokenByte = C.RESET_TOKEN_BYTE

    return if (isNegativeValue) -accumulatedValue else accumulatedValue
}

fun GhostJsonStringReader.nextULong(): ULong {
    if (peekNextToken() == C.QUOTE_INT) {
        return nextString().toULong()
    }
    return nextLong().toULong()
}

fun GhostJsonStringReader.nextFloat(): Float {
    val header = prepareNumericHeader()
    val isQuoted = (header and C.NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and C.NUMERIC_HEADER_NEGATIVE) != 0

    validateLeadingZero()

    nextTokenByte = C.RESET_TOKEN_BYTE
    val result = parseJsonFloatingBodyCore(
        precisionLimit = C.FLOAT_PRECISION_LIMIT,
        getPosition = { position },
        setPosition = { position = it },
        getLimit = { limit },
        getByte = { getByte(it) },
        readDigitRun = { onDigit -> readDigitRun(onDigit) },
        parseExponentValue = { parseExponentValue() },
        throwError = { throwError(it) },
    ) { mantissa, exponent ->
        finalizeParsedFloat(mantissa, exponent, isNegativeValue) { throwError(it) }
    }

    if (isQuoted) {
        consumeNumericCoercionFooter()
    }

    return result
}

fun GhostJsonStringReader.nextDouble(): Double {
    val header = prepareNumericHeader()
    val isQuoted = (header and C.NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and C.NUMERIC_HEADER_NEGATIVE) != 0

    validateLeadingZero()

    nextTokenByte = C.RESET_TOKEN_BYTE
    val result = parseJsonFloatingBodyCore(
        precisionLimit = C.DOUBLE_PRECISION_LIMIT,
        getPosition = { position },
        setPosition = { position = it },
        getLimit = { limit },
        getByte = { getByte(it) },
        readDigitRun = { onDigit -> readDigitRun(onDigit) },
        parseExponentValue = { parseExponentValue() },
        throwError = { throwError(it) },
    ) { mantissa, exponent ->
        finalizeParsedDouble(mantissa, exponent, isNegativeValue) { throwError(it) }
    }

    if (isQuoted) {
        consumeNumericCoercionFooter()
    }

    return result
}

private fun GhostJsonStringReader.parseExponentValue(): Int =
    parseExponentValueCore(
        startPosition = position,
        limit = limit,
        getByte = { getByte(it) },
        setPosition = { position = it },
        throwError = { throwError(it) },
    )

private fun GhostJsonStringReader.prepareNumericHeader(): Int =
    prepareNumericHeaderCore(
        getNextTokenByte = { nextTokenByte },
        setNextTokenByte = { nextTokenByte = it },
        getPosition = { position },
        setPosition = { position = it },
        limit = limit,
        coerceStringsToNumbers = coerceStringsToNumbers,
        skipWhitespace = { skipWhitespace() },
        throwError = { throwError(it) },
    )

private fun GhostJsonStringReader.parseIntDigits(isNegative: Boolean, startOfNumber: Int): Int {
    var accumulatedValue = 0
    var digitCount = 0
    var hasDigitsFound = false
    nextTokenByte = C.RESET_TOKEN_BYTE
    var earlyExitResult: Int? = null

    val localLimit = limit
    val chars = rawChars
    while (position < localLimit) {
        val byte = chars[position].code
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
            if (isNumericSeparator(byte)) {
                position = startOfNumber
                earlyExitResult = nextDouble().toInt()
            }
            break
        }
    }

    if (earlyExitResult != null) {
        return earlyExitResult
    }
    if (!hasDigitsFound) {
        throwError(C.ERR_EXPECTED_INT_PART)
    }
    return accumulatedValue
}

private fun GhostJsonStringReader.parseLongDigits(isNegative: Boolean, startOfNumber: Int): Long {
    var accumulatedValue = 0L
    var digitCount = 0
    var hasDigitsFound = false
    nextTokenByte = C.RESET_TOKEN_BYTE
    var earlyExitResult: Long? = null

    val localLimit = limit
    val chars = rawChars
    while (position < localLimit) {
        val byte = chars[position].code
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
            if (isNumericSeparator(byte)) {
                position = startOfNumber
                earlyExitResult = nextDouble().toLong()
            }
            break
        }
    }

    if (earlyExitResult != null) {
        return earlyExitResult
    }
    if (!hasDigitsFound) {
        throwError(C.ERR_EXPECTED_INT_PART)
    }
    return accumulatedValue
}

private inline fun GhostJsonStringReader.consumeNumericCoercionFooter() {
    consumeNumericCoercionFooterCore(
        position = position,
        limit = limit,
        getByte = { getByte(it) },
        throwError = { throwError(it) },
        afterQuote = {
            internalSkip(1)
            skipWhitespace()
        },
    )
}

private fun GhostJsonStringReader.validateLeadingZero() {
    validateLeadingZeroCore(
        position = position,
        limit = limit,
        getByte = { getByte(it) },
        throwError = { throwError(it) },
    )
}

private inline fun GhostJsonStringReader.readDigitRun(onDigit: (Int) -> Unit) {
    val localLimit = limit
    val chars = rawChars
    while (position < localLimit) {
        val byte = chars[position].code
        if (isDigit(byte)) {
            onDigit(byte)
            position++
        } else {
            break
        }
    }
}

fun GhostJsonStringReader.skipNumber() {
    val header = prepareNumericHeader()
    val isQuoted = (header and C.NUMERIC_HEADER_QUOTED) != 0

    skipNumberBodyCore(
        getPosition = { position },
        setPosition = { position = it },
        limit = limit,
        getByte = { getByte(it) },
        throwError = { throwError(it) },
    )

    if (isQuoted) {
        consumeNumericCoercionFooter()
    }
    nextTokenByte = C.RESET_TOKEN_BYTE
}

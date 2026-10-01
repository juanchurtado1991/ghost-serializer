@file:Suppress("NOTHING_TO_INLINE")
@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser.strings

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.json.consumeNumericCoercionFooterCore
import com.ghost.serialization.parser.common.json.finalizeParsedDouble
import com.ghost.serialization.parser.common.json.finalizeParsedFloat
import com.ghost.serialization.parser.common.json.parseExponentValueCore
import com.ghost.serialization.parser.common.json.parseIntDigitsCore
import com.ghost.serialization.parser.common.json.parseJsonFloatingBodyCore
import com.ghost.serialization.parser.common.json.parseLongDigitsCore
import com.ghost.serialization.parser.common.json.prepareNumericHeaderCore
import com.ghost.serialization.parser.common.json.skipNumberBodyCore
import com.ghost.serialization.parser.common.json.validateLeadingZeroCore
import com.ghost.serialization.parser.common.constants.GhostJsonNumericLimits as NUM
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants as SCN
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

fun GhostJsonStringReader.nextInt(): Int {
    val header = prepareNumericHeader()
    val isQuoted = (header and NUM.NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and NUM.NUMERIC_HEADER_NEGATIVE) != 0

    val startOfNumber = position
    validateLeadingZero()

    val accumulatedValue = parseIntDigits(isNegative = isNegativeValue, startOfNumber = startOfNumber)

    if (isQuoted) {
        consumeNumericCoercionFooter()
    }
    nextTokenByte = SCN.RESET_TOKEN_BYTE

    pathTracker.finishScalarValue()
    return if (isNegativeValue) -accumulatedValue else accumulatedValue
}

fun GhostJsonStringReader.nextLong(): Long {
    val header = prepareNumericHeader()
    val isQuoted = (header and NUM.NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and NUM.NUMERIC_HEADER_NEGATIVE) != 0

    val startOfNumber = position
    validateLeadingZero()

    val accumulatedValue = parseLongDigits(isNegative = isNegativeValue, startOfNumber = startOfNumber)

    if (isQuoted) {
        consumeNumericCoercionFooter()
    }
    nextTokenByte = SCN.RESET_TOKEN_BYTE

    pathTracker.finishScalarValue()
    return if (isNegativeValue) -accumulatedValue else accumulatedValue
}

fun GhostJsonStringReader.nextULong(): ULong {
    if (peekNextToken() == TOK.QUOTE_INT) {
        return nextString().toULong()
    }
    return nextLong().toULong()
}

fun GhostJsonStringReader.nextFloat(): Float {
    val header = prepareNumericHeader()
    val isQuoted = (header and NUM.NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and NUM.NUMERIC_HEADER_NEGATIVE) != 0

    validateLeadingZero()

    nextTokenByte = SCN.RESET_TOKEN_BYTE
    val result = parseJsonFloatingBodyCore(
        precisionLimit = NUM.FLOAT_PRECISION_LIMIT,
        allowBulkDigitRead = true,
        getPosition = { position },
        setPosition = { position = it },
        limit = limit,
        getByte = { getByte(it) },
        parseExponentValue = { parseExponentValue() },
        throwError = { throwError(it) },
    ) { mantissa, exponent ->
        finalizeParsedFloat(mantissa = mantissa, exponent = exponent, isNegative = isNegativeValue) { throwError(it) }
    }

    if (isQuoted) {
        consumeNumericCoercionFooter()
    }

    pathTracker.finishScalarValue()
    return result
}

fun GhostJsonStringReader.nextDouble(): Double {
    val header = prepareNumericHeader()
    val isQuoted = (header and NUM.NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and NUM.NUMERIC_HEADER_NEGATIVE) != 0

    validateLeadingZero()

    nextTokenByte = SCN.RESET_TOKEN_BYTE
    val result = parseJsonFloatingBodyCore(
        precisionLimit = NUM.DOUBLE_PRECISION_LIMIT,
        allowBulkDigitRead = true,
        getPosition = { position },
        setPosition = { position = it },
        limit = limit,
        getByte = { getByte(it) },
        parseExponentValue = { parseExponentValue() },
        throwError = { throwError(it) },
    ) { mantissa, exponent ->
        finalizeParsedDouble(mantissa = mantissa, exponent = exponent, isNegative = isNegativeValue) { throwError(it) }
    }

    if (isQuoted) {
        consumeNumericCoercionFooter()
    }

    pathTracker.finishScalarValue()
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
    return parseIntDigitsCore(
        isNegative = isNegative,
        resetNextTokenByte = { nextTokenByte = SCN.RESET_TOKEN_BYTE },
        getPosition = { position },
        setPosition = { position = it },
        limit = limit,
        getByte = { getByte(it) },
        onNumericSeparator = {
            position = startOfNumber
            nextDouble().toInt()
        },
        throwError = { throwError(it) },
    )
}

private fun GhostJsonStringReader.parseLongDigits(isNegative: Boolean, startOfNumber: Int): Long {
    return parseLongDigitsCore(
        isNegative = isNegative,
        resetNextTokenByte = { nextTokenByte = SCN.RESET_TOKEN_BYTE },
        getPosition = { position },
        setPosition = { position = it },
        limit = limit,
        getByte = { getByte(it) },
        onNumericSeparator = {
            position = startOfNumber
            nextDouble().toLong()
        },
        throwError = { throwError(it) },
    )
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

fun GhostJsonStringReader.skipNumber() {
    val header = prepareNumericHeader()
    val isQuoted = (header and NUM.NUMERIC_HEADER_QUOTED) != 0

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
    nextTokenByte = SCN.RESET_TOKEN_BYTE
}

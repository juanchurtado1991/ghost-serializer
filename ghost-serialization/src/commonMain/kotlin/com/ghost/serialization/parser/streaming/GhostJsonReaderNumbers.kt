@file:OptIn(InternalGhostApi::class)
@file:Suppress("NOTHING_TO_INLINE")

package com.ghost.serialization.parser.streaming

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.consumeNumericCoercionFooterCore
import com.ghost.serialization.parser.common.finalizeParsedDouble
import com.ghost.serialization.parser.common.finalizeParsedFloat
import com.ghost.serialization.parser.common.handleLeadingZeroCore
import com.ghost.serialization.parser.common.parseExponentValueCore
import com.ghost.serialization.parser.common.parseIntDigitsCore
import com.ghost.serialization.parser.common.parseJsonFloatingBodyCore
import com.ghost.serialization.parser.common.parseLongDigitsCore
import com.ghost.serialization.parser.common.prepareNumericHeaderCore
import com.ghost.serialization.parser.common.skipNumberBodyCore
import com.ghost.serialization.parser.common.validateLeadingZeroCore
import com.ghost.serialization.parser.common.GhostJsonConstants as C


/** Reads the next number as a [Float] via a zero-allocation, register-based loop. */
fun GhostJsonReader.nextFloat(): Float {
    val header = prepareNumericHeader()
    val isQuoted = (header and C.NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and C.NUMERIC_HEADER_NEGATIVE) != 0

    validateLeadingZero()

    nextTokenByte = C.RESET_TOKEN_BYTE
    val result = parseJsonFloatingBodyCore(
        precisionLimit = C.FLOAT_PRECISION_LIMIT,
        allowBulkDigitRead = false,
        getPosition = { position },
        setPosition = { position = it },
        limit = limit,
        getByte = { getByte(it) },
        parseExponentValue = { parseExponentValue() },
        throwError = { throwError(it) },
    ) { mantissa, exponent ->
        finalizeParsedFloat(mantissa, exponent, isNegativeValue) { throwError(it) }
    }

    if (isQuoted) {
        consumeNumericCoercionFooter()
    }

    pathTracker.finishScalarValue()
    return result
}

/** Reads the next number as a [Double] via a zero-allocation, register-based loop. */
fun GhostJsonReader.nextDouble(): Double {
    val header = prepareNumericHeader()
    val isQuoted = (header and C.NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and C.NUMERIC_HEADER_NEGATIVE) != 0

    validateLeadingZero()

    nextTokenByte = C.RESET_TOKEN_BYTE
    val result = parseJsonFloatingBodyCore(
        precisionLimit = C.DOUBLE_PRECISION_LIMIT,
        allowBulkDigitRead = false,
        getPosition = { position },
        setPosition = { position = it },
        limit = limit,
        getByte = { getByte(it) },
        parseExponentValue = { parseExponentValue() },
        throwError = { throwError(it) },
    ) { mantissa, exponent ->
        finalizeParsedDouble(mantissa, exponent, isNegativeValue) { throwError(it) }
    }

    if (isQuoted) {
        consumeNumericCoercionFooter()
    }

    pathTracker.finishScalarValue()
    return result
}

/** Parses the exponent suffix value (e.g. `e-5`, `e+12`). */
private inline fun GhostJsonReader.parseExponentValue(): Int =
    parseExponentValueCore(
        startPosition = position,
        limit = limit,
        getByte = { getByte(it) },
        setPosition = { position = it },
        throwError = { throwError(it) },
    )

/** Reads the next number as an [Int], optimized for common small integers. */
fun GhostJsonReader.nextInt(): Int {
    val header = prepareNumericHeader()
    val isQuoted = (header and C.NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and C.NUMERIC_HEADER_NEGATIVE) != 0

    val startOfNumber = position

    val absoluteValue = if (
        startOfNumber < limit &&
        getByte(startOfNumber) == C.ZERO_INT
    ) {
        handleLeadingZero()
        0
    } else {
        parseIntDigits(isNegativeValue, startOfNumber)
    }

    val finalIntResult = if (isNegativeValue) {
        -absoluteValue
    } else {
        absoluteValue
    }

    if (isQuoted) {
        consumeNumericCoercionFooter()
    }
    pathTracker.finishScalarValue()
    return finalIntResult
}

/** Reads the next number as a [Long], optimized for common small longs. */
fun GhostJsonReader.nextLong(): Long {
    val header = prepareNumericHeader()
    val isQuoted = (header and C.NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and C.NUMERIC_HEADER_NEGATIVE) != 0

    val startOfNumber = position

    val absoluteValue = if (
        startOfNumber < limit &&
        getByte(startOfNumber) == C.ZERO_INT
    ) {
        handleLeadingZero()
        0L
    } else {
        parseLongDigits(isNegativeValue, startOfNumber)
    }

    val finalLongResult = if (absoluteValue == Long.MIN_VALUE) {
        absoluteValue
    } else {
        (if (isNegativeValue) -absoluteValue else absoluteValue)
    }

    if (isQuoted) {
        consumeNumericCoercionFooter()
    }
    pathTracker.finishScalarValue()
    return finalLongResult
}

/** Reads a JSON/YAML unsigned long scalar (quoted decimal string for full `uint64` range). */
fun GhostJsonReader.nextULong(): ULong {
    if (nextTokenByte == C.RESET_TOKEN_BYTE) {
        skipWhitespace()
    }
    if (position < limit && getByte(position) == C.QUOTE_INT) {
        return nextString().toULong()
    }
    return nextLong().toULong()
}

/** Checks for a negative sign and string-coercion quote before the number body. */
private fun GhostJsonReader.prepareNumericHeader(): Int =
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

private fun GhostJsonReader.handleLeadingZero() {
    handleLeadingZeroCore(
        position = position,
        limit = limit,
        getByte = { getByte(it) },
        throwError = { throwError(it) },
        consumeOne = { internalSkip(1) },
    )
}

private fun GhostJsonReader.parseIntDigits(
    isNegative: Boolean,
    startOfNumber: Int
): Int {
    return parseIntDigitsCore(
        isNegative = isNegative,
        resetNextTokenByte = { nextTokenByte = C.RESET_TOKEN_BYTE },
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

private fun GhostJsonReader.parseLongDigits(
    isNegative: Boolean,
    startOfNumber: Int
): Long {
    return parseLongDigitsCore(
        isNegative = isNegative,
        resetNextTokenByte = { nextTokenByte = C.RESET_TOKEN_BYTE },
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

/** Consumes the trailing quote when parsing a coerced numeric string value. */
private inline fun GhostJsonReader.consumeNumericCoercionFooter() {
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

private fun GhostJsonReader.validateLeadingZero() {
    validateLeadingZeroCore(
        position = position,
        limit = limit,
        getByte = { getByte(it) },
        throwError = { throwError(it) },
    )
}

@InternalGhostApi
fun GhostJsonReader.skipNumber() {
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

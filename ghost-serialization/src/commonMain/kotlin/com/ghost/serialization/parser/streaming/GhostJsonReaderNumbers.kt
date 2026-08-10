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


/**
 * Reads a JSON number and returns it as a Float.
 * Uses a zero-allocation, register-based loop for maximum speed.
 * Used by KSP-generated serializers.
 *
 * @throws com.ghost.serialization.exception.GhostJsonException
 * if float format is invalid or overflows.
 */
fun GhostJsonReader.nextFloat(): Float {
    val header = prepareNumericHeader()
    val isQuoted = (header and C.NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and C.NUMERIC_HEADER_NEGATIVE) != 0

    validateLeadingZero()

    nextTokenByte = C.RESET_TOKEN_BYTE
    val result = parseJsonFloatingBodyCore(
        precisionLimit = C.FLOAT_PRECISION_LIMIT,
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

    return result
}

/**
 * Reads a JSON number and returns it as a Double.
 * Uses a zero-allocation, register-based loop for maximum speed.
 * Used by KSP-generated serializers.
 *
 * @throws com.ghost.serialization.exception.GhostJsonException
 * if double format is invalid or overflows.
 */
fun GhostJsonReader.nextDouble(): Double {
    val header = prepareNumericHeader()
    val isQuoted = (header and C.NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and C.NUMERIC_HEADER_NEGATIVE) != 0

    validateLeadingZero()

    nextTokenByte = C.RESET_TOKEN_BYTE
    val result = parseJsonFloatingBodyCore(
        precisionLimit = C.DOUBLE_PRECISION_LIMIT,
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

    return result
}

/**
 * Parses the exponent value suffix (e.g. e-5) from the stream.
 */
private inline fun GhostJsonReader.parseExponentValue(): Int =
    parseExponentValueCore(
        startPosition = position,
        limit = limit,
        getByte = { getByte(it) },
        setPosition = { position = it },
        throwError = { throwError(it) },
    )

/**
 * Reads a JSON integer and returns it as an Int.
 * Optimized for common small integers.
 * Used by KSP-generated serializers.
 *
 * @throws com.ghost.serialization.exception.GhostJsonException
 * if the integer is invalid or overflows.
 */
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
    return finalIntResult
}

/**
 * Reads a JSON long and returns it as a Long.
 * Optimized for common small longs.
 * Used by KSP-generated serializers.
 *
 * @throws com.ghost.serialization.exception.GhostJsonException
 * if the long is invalid or overflows.
 */
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

/**
 * Prepares the numeric header by checking the negative sign and checking string coercion quote.
 */
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

/**
 * Validates and consumes a leading zero.
 */
private fun GhostJsonReader.handleLeadingZero() {
    handleLeadingZeroCore(
        position = position,
        limit = limit,
        getByte = { getByte(it) },
        throwError = { throwError(it) },
        consumeOne = { internalSkip(1) },
    )
}

/**
 * Bitwise parsing loop for integer digits with overflow verification.
 */
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

/**
 * Bitwise parsing loop for long digits with overflow verification.
 */
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

/**
 * Consumes the closing quotation mark for coerced numeric string values.
 */
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

/**
 * Asserts that leading zero doesn't precede another digit.
 */
private fun GhostJsonReader.validateLeadingZero() {
    validateLeadingZeroCore(
        position = position,
        limit = limit,
        getByte = { getByte(it) },
        throwError = { throwError(it) },
    )
}

/**
 * Skips a JSON numeric token value from the source.
 */
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

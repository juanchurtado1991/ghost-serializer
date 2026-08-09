@file:OptIn(InternalGhostApi::class)
@file:Suppress("NOTHING_TO_INLINE")

package com.ghost.serialization.parser.bytes

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.consumeNumericCoercionFooterCore
import com.ghost.serialization.parser.common.finalizeParsedDouble
import com.ghost.serialization.parser.common.finalizeParsedFloat
import com.ghost.serialization.parser.common.handleLeadingZeroCore
import com.ghost.serialization.parser.common.isDigit
import com.ghost.serialization.parser.common.parseExponentValueCore
import com.ghost.serialization.parser.common.parseIntDigitsCore
import com.ghost.serialization.parser.common.parseJsonFloatingBodyCore
import com.ghost.serialization.parser.common.parseLongDigitsCore
import com.ghost.serialization.parser.common.prepareNumericHeaderCore
import com.ghost.serialization.parser.common.skipNumberBodyCore
import com.ghost.serialization.parser.common.validateLeadingZeroCore
import com.ghost.serialization.parser.common.GhostJsonConstants as C


/**
 * Parses and returns the next [Float] value from the JSON stream.
 *
 * Supports coercion from strings if enabled, exponent notations, decimal fractions, and range checks.
 *
 * @throws com.ghost.serialization.exception.GhostJsonException
 * if float format is invalid or overflows.
 */
fun GhostJsonFlatReader.nextFloatExtension(): Float {
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

/**
 * Parses and returns the next [Double] value from the JSON stream.
 *
 * Supports coercion from strings if enabled,
 * exponent notations, decimal fractions, and range checks.
 *
 * @throws com.ghost.serialization.exception.GhostJsonException
 * if double format is invalid or overflows.
 */
fun GhostJsonFlatReader.nextDoubleExtension(): Double {
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

/**
 * Helper to parse the exponent suffix value (e.g. e-5 or e+12) from a number.
 */
private inline fun GhostJsonFlatReader.parseExponentValue(): Int =
    parseExponentValueCore(
        startPosition = position,
        limit = limit,
        getByte = { getByte(it) },
        setPosition = { position = it },
        throwError = { throwError(it) },
    )

/**
 * Parses and returns the next [Int] value from the JSON stream.
 *
 * Supports coercion from strings if enabled, validates format, leading zeros, and range overflow.
 *
 * @throws com.ghost.serialization.exception.GhostJsonException if integer is invalid or overflows.
 */
fun GhostJsonFlatReader.nextIntExtension(): Int {
    val header = prepareNumericHeader()
    val isQuoted = (header and C.NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and C.NUMERIC_HEADER_NEGATIVE) != 0

    val startOfNumber = position

    val absoluteValue = if (startOfNumber < limit && getByte(startOfNumber) == C.ZERO_INT) {
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
 * Parses and returns the next [Long] value from the JSON stream.
 *
 * Supports coercion from strings if enabled, validates format, leading zeros, and range overflow.
 *
 * @throws com.ghost.serialization.exception.GhostJsonException if long value is invalid or overflows.
 */
fun GhostJsonFlatReader.nextLongExtension(): Long {
    val header = prepareNumericHeader()
    val isQuoted = (header and C.NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and C.NUMERIC_HEADER_NEGATIVE) != 0

    val startOfNumber = position

    val absoluteValue = if (startOfNumber < limit && getByte(startOfNumber) == C.ZERO_INT) {
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

/**
 * Prepares the numeric header by checking negative signs and string coercion quotes.
 */
private fun GhostJsonFlatReader.prepareNumericHeader(): Int =
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
 * Handles validation and skipping of a single leading zero.
 */
private fun GhostJsonFlatReader.handleLeadingZero() {
    handleLeadingZeroCore(
        position = position,
        limit = limit,
        getByte = { getByte(it) },
        throwError = { throwError(it) },
        consumeOne = { internalSkip(1) },
    )
}

/**
 * Parses integer digits bitwise with overflow checks.
 */
private fun GhostJsonFlatReader.parseIntDigits(isNegative: Boolean, startOfNumber: Int): Int {
    return parseIntDigitsCore(
        isNegative = isNegative,
        resetNextTokenByte = { nextTokenByte = C.RESET_TOKEN_BYTE },
        forEachNumericUnit = { onDigitByte, onNonDigit ->
            val data = rawData
            val localLimit = limit
            while (position < localLimit) {
                val byte = data[position].toInt() and C.BYTE_MASK
                if (isDigit(byte)) {
                    onDigitByte(byte)
                    position++
                } else {
                    onNonDigit(byte)
                    break
                }
            }
        },
        onNumericSeparator = {
            position = startOfNumber
            nextDouble().toInt()
        },
        throwError = { throwError(it) },
    )
}

/**
 * Parses long digits bitwise with overflow checks.
 */
private fun GhostJsonFlatReader.parseLongDigits(isNegative: Boolean, startOfNumber: Int): Long {
    return parseLongDigitsCore(
        isNegative = isNegative,
        resetNextTokenByte = { nextTokenByte = C.RESET_TOKEN_BYTE },
        forEachNumericUnit = { onDigitByte, onNonDigit ->
            val data = rawData
            val localLimit = limit
            while (position < localLimit) {
                val byte = data[position].toInt() and C.BYTE_MASK
                if (isDigit(byte)) {
                    onDigitByte(byte)
                    position++
                } else {
                    onNonDigit(byte)
                    break
                }
            }
        },
        onNumericSeparator = {
            position = startOfNumber
            nextDouble().toLong()
        },
        throwError = { throwError(it) },
    )
}

/**
 * Consumes the trailing quotation mark when parsing coerced numeric string values.
 */
private inline fun GhostJsonFlatReader.consumeNumericCoercionFooter() {
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
 * Validates leading zero presence for numbers.
 */
private fun GhostJsonFlatReader.validateLeadingZero() {
    validateLeadingZeroCore(
        position = position,
        limit = limit,
        getByte = { getByte(it) },
        throwError = { throwError(it) },
    )
}

private inline fun GhostJsonFlatReader.readDigitRun(onDigit: (Int) -> Unit) {
    val data = rawData
    val localLimit = limit
    while (position < localLimit) {
        val byte = data[position].toInt() and C.BYTE_MASK
        if (isDigit(byte)) {
            onDigit(byte)
            position++
        } else {
            break
        }
    }
}

/**
 * Skips the next numeric token value in the JSON stream.
 *
 * Validates scientific exponent format, dot separation, and handles string coercion bounds.
 *
 * @throws com.ghost.serialization.exception.GhostJsonException if number is malformed.
 */
fun GhostJsonFlatReader.skipNumber() {
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

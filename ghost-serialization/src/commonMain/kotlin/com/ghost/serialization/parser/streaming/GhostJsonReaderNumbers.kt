@file:OptIn(InternalGhostApi::class)
@file:Suppress("NOTHING_TO_INLINE")

package com.ghost.serialization.parser.streaming

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.json.consumeNumericCoercionFooterCore
import com.ghost.serialization.parser.common.json.finalizeParsedDouble
import com.ghost.serialization.parser.common.json.finalizeParsedFloat
import com.ghost.serialization.parser.common.json.parseExponentValueCore
import com.ghost.serialization.parser.common.json.parseIntDigitsCore
import com.ghost.serialization.parser.common.json.parseIntegralMagnitudeCore
import com.ghost.serialization.parser.common.json.parseJsonFloatingBodyCore
import com.ghost.serialization.parser.common.json.parseLongDigitsCore
import com.ghost.serialization.parser.common.json.prepareNumericHeaderCore
import com.ghost.serialization.parser.common.json.skipNumberBodyCore
import com.ghost.serialization.parser.common.json.validateLeadingZeroCore
import com.ghost.serialization.parser.common.constants.GhostJsonErrorMessages as EM
import com.ghost.serialization.parser.common.constants.GhostJsonNumericLimits as NUM
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants as SCN
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

/** Reads the next number as a [Float] via a zero-allocation, register-based loop. */
fun GhostJsonReader.nextFloat(): Float {
    val header = prepareNumericHeader()
    val isQuoted = (header and NUM.NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and NUM.NUMERIC_HEADER_NEGATIVE) != 0

    validateLeadingZero()

    nextTokenByte = SCN.RESET_TOKEN_BYTE
    val result = parseJsonFloatingBodyCore(
        precisionLimit = NUM.DOUBLE_PRECISION_LIMIT,
        allowBulkDigitRead = false,
        getPosition = { position },
        setPosition = { position = it },
        limit = limit,
        getByte = { numberByteAt(index = it) },
        parseExponentValue = { parseExponentValue() },
        throwError = { throwError(it) },
    ) { mantissa, exponent, truncatedLiteral ->
        finalizeParsedFloat(
            mantissa = mantissa,
            exponent = exponent,
            isNegative = isNegativeValue,
            truncatedLiteral = truncatedLiteral
        ) { throwError(it) }
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
    val isQuoted = (header and NUM.NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and NUM.NUMERIC_HEADER_NEGATIVE) != 0

    validateLeadingZero()

    nextTokenByte = SCN.RESET_TOKEN_BYTE
    val result = parseJsonFloatingBodyCore(
        precisionLimit = NUM.DOUBLE_PRECISION_LIMIT,
        allowBulkDigitRead = false,
        getPosition = { position },
        setPosition = { position = it },
        limit = limit,
        getByte = { numberByteAt(index = it) },
        parseExponentValue = { parseExponentValue() },
        throwError = { throwError(it) },
    ) { mantissa, exponent, truncatedLiteral ->
        finalizeParsedDouble(
            mantissa = mantissa,
            exponent = exponent,
            isNegative = isNegativeValue,
            truncatedLiteral = truncatedLiteral
        ) { throwError(it) }
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
        getByte = { numberByteAt(index = it) },
        setPosition = { position = it },
        throwError = { throwError(it) },
    )

/** Reads the next number as an [Int], optimized for common small integers. */
fun GhostJsonReader.nextInt(): Int {
    val header = prepareNumericHeader()
    val isQuoted = (header and NUM.NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and NUM.NUMERIC_HEADER_NEGATIVE) != 0

    validateLeadingZero()
    val absoluteValue = parseIntDigits(isNegative = isNegativeValue)

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
    val isQuoted = (header and NUM.NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and NUM.NUMERIC_HEADER_NEGATIVE) != 0

    validateLeadingZero()
    val absoluteValue = parseLongDigits(isNegative = isNegativeValue)

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

/** Reads a [ULong]: a quoted decimal string or a bare number, exact over the full range. */
fun GhostJsonReader.nextULong(): ULong {
    if (nextTokenByte == SCN.RESET_TOKEN_BYTE) {
        skipWhitespace()
    }
    val isQuoted = position < limit && numberByteAt(index = position) == TOK.QUOTE_INT
    if (isQuoted) {
        return nextString().toULongOrNull() ?: throwError(EM.ERR_INVALID_ULONG)
    }
    val header = prepareNumericHeader()
    val isNegativeValue = (header and NUM.NUMERIC_HEADER_NEGATIVE) != 0

    validateLeadingZero()
    nextTokenByte = SCN.RESET_TOKEN_BYTE
    val magnitude = parseIntegralMagnitudeCore(
        startPosition = position,
        limit = limit,
        getByte = { numberByteAt(index = it) },
        setPosition = { position = it },
        overflowMessage = EM.ERR_ULONG_OVERFLOW,
        throwError = { throwError(it) },
    )
    val isNegativeNonZero = isNegativeValue && magnitude != 0uL
    if (isNegativeNonZero) {
        throwError(EM.ERR_NEGATIVE_UNSIGNED)
    }
    pathTracker.finishScalarValue()
    return magnitude
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

private fun GhostJsonReader.parseIntDigits(
    isNegative: Boolean
): Int {
    return parseIntDigitsCore(
        isNegative = isNegative,
        resetNextTokenByte = { nextTokenByte = SCN.RESET_TOKEN_BYTE },
        getPosition = { position },
        setPosition = { position = it },
        limit = limit,
        getByte = { numberByteAt(index = it) },
        throwError = { throwError(it) },
    )
}

private fun GhostJsonReader.parseLongDigits(
    isNegative: Boolean
): Long {
    return parseLongDigitsCore(
        isNegative = isNegative,
        resetNextTokenByte = { nextTokenByte = SCN.RESET_TOKEN_BYTE },
        getPosition = { position },
        setPosition = { position = it },
        limit = limit,
        getByte = { numberByteAt(index = it) },
        throwError = { throwError(it) },
    )
}

/** Consumes the trailing quote when parsing a coerced numeric string value. */
private inline fun GhostJsonReader.consumeNumericCoercionFooter() {
    consumeNumericCoercionFooterCore(
        position = position,
        limit = limit,
        getByte = { numberByteAt(index = it) },
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
        getByte = { numberByteAt(index = it) },
        throwError = { throwError(it) },
    )
}

@InternalGhostApi
fun GhostJsonReader.skipNumber() {
    val header = prepareNumericHeader()
    val isQuoted = (header and NUM.NUMERIC_HEADER_QUOTED) != 0

    skipNumberBodyCore(
        getPosition = { position },
        setPosition = { position = it },
        limit = limit,
        getByte = { numberByteAt(index = it) },
        throwError = { throwError(it) },
    )

    if (isQuoted) {
        consumeNumericCoercionFooter()
    }
    nextTokenByte = SCN.RESET_TOKEN_BYTE
}

@file:OptIn(InternalGhostApi::class)
@file:Suppress("NOTHING_TO_INLINE")

package com.ghost.serialization.parser.bytes.extensions

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
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

/** Reads a [ULong]: a quoted decimal string or a bare number, exact over the full range. */
fun GhostJsonFlatReader.nextULongExtension(): ULong {
    peekNextToken()
    val isQuoted = position < limit && getByte(position) == TOK.QUOTE_INT
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
        getByte = { getByte(it) },
        setPosition = { position = it },
        overflowMessage = EM.ERR_ULONG_OVERFLOW,
        throwError = { throwError(it) },
    )
    val isNegativeNonZero = isNegativeValue && magnitude != 0uL
    if (isNegativeNonZero) {
        throwError(EM.ERR_NEGATIVE_UNSIGNED)
    }
    return magnitude
}

/** Parses the next [Float], supporting string coercion, exponents, and decimal fractions. */
fun GhostJsonFlatReader.nextFloatExtension(): Float {
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

    return result
}

/** Parses the next [Double], supporting string coercion, exponents, and decimal fractions. */
fun GhostJsonFlatReader.nextDoubleExtension(): Double {
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

    return result
}

/** Parses the exponent suffix value (e.g. `e-5`, `e+12`). */
private inline fun GhostJsonFlatReader.parseExponentValue(): Int =
    parseExponentValueCore(
        startPosition = position,
        limit = limit,
        getByte = { getByte(it) },
        setPosition = { position = it },
        throwError = { throwError(it) },
    )

/** Parses the next [Int], supporting string coercion; validates leading zeros and overflow. */
fun GhostJsonFlatReader.nextIntExtension(): Int {
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
    return finalIntResult
}

/** Parses the next [Long], supporting string coercion; validates leading zeros and overflow. */
fun GhostJsonFlatReader.nextLongExtension(): Long {
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
    return finalLongResult
}

/** Checks for a negative sign and string-coercion quote before the number body. */
private fun GhostJsonFlatReader.prepareNumericHeader(): Int = prepareNumericHeaderCore(
    getNextTokenByte = { nextTokenByte },
    setNextTokenByte = { nextTokenByte = it },
    getPosition = { position },
    setPosition = { position = it },
    limit = limit,
    coerceStringsToNumbers = coerceStringsToNumbers,
    skipWhitespace = { skipWhitespace() },
    throwError = { throwError(it) },
)

private fun GhostJsonFlatReader.parseIntDigits(
    isNegative: Boolean
): Int = parseIntDigitsCore(
    isNegative = isNegative,
    resetNextTokenByte = { nextTokenByte = SCN.RESET_TOKEN_BYTE },
    getPosition = { position },
    setPosition = { position = it },
    limit = limit,
    getByte = { getByte(it) },
    throwError = { throwError(it) },
)

private fun GhostJsonFlatReader.parseLongDigits(
    isNegative: Boolean
): Long = parseLongDigitsCore(
    isNegative = isNegative,
    resetNextTokenByte = { nextTokenByte = SCN.RESET_TOKEN_BYTE },
    getPosition = { position },
    setPosition = { position = it },
    limit = limit,
    getByte = { getByte(it) },
    throwError = { throwError(it) },
)

/** Consumes the trailing quote when parsing a coerced numeric string value. */
private inline fun GhostJsonFlatReader.consumeNumericCoercionFooter() =
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

private fun GhostJsonFlatReader.validateLeadingZero() = validateLeadingZeroCore(
    position = position,
    limit = limit,
    getByte = { getByte(it) },
    throwError = { throwError(it) },
)

/** Skips the next numeric token, validating exponent/dot format and string-coercion bounds. */
fun GhostJsonFlatReader.skipNumber() {
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

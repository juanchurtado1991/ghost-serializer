@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser

import com.ghost.serialization.parser.GhostJsonConstants as C

import com.ghost.serialization.InternalGhostApi
import kotlin.math.pow

/**
 * Reads a JSON number and returns it as a Float.
 * Uses a zero-allocation, register-based loop for maximum speed.
 * Used by KSP-generated serializers.
 */
fun GhostJsonReader.nextFloat(): Float = nextFloatImpl(
    prepareNumericHeader = { prepareNumericHeader() },
    validateLeadingZero = { validateLeadingZero() },
    readNumericLoop = { onDigit -> readNumericLoop(onDigit) },
    throwError = { throwError(it) },
    getPosition = { position },
    setPosition = { position = it },
    getLimit = { limit },
    getByte = { getByte(it) },
    isExponentMarker = { isExponentMarker(it) },
    parseExponentValue = { parseExponentValue() },
    getFloatPowerOfTen = { getFloatPowerOfTen(it) },
    validateNumericRangeFloat = { validateNumericRangeFloat(it) },
    consumeNumericCoercionFooter = { consumeNumericCoercionFooter() },
    setNextTokenByte = { nextTokenByte = it }
)

/**
 * Reads a JSON number and returns it as a Double.
 * Uses a zero-allocation, register-based loop for maximum speed.
 * Used by KSP-generated serializers.
 */
fun GhostJsonReader.nextDouble(): Double = nextDoubleImpl(
    prepareNumericHeader = { prepareNumericHeader() },
    validateLeadingZero = { validateLeadingZero() },
    readNumericLoop = { onDigit -> readNumericLoop(onDigit) },
    throwError = { throwError(it) },
    getPosition = { position },
    setPosition = { position = it },
    getLimit = { limit },
    getByte = { getByte(it) },
    isExponentMarker = { isExponentMarker(it) },
    parseExponentValue = { parseExponentValue() },
    getDoublePowerOfTen = { getDoublePowerOfTen(it) },
    validateNumericRange = { validateNumericRange(it) },
    consumeNumericCoercionFooter = { consumeNumericCoercionFooter() },
    setNextTokenByte = { nextTokenByte = it }
)

@Suppress("NOTHING_TO_INLINE")
private inline fun GhostJsonReader.parseExponentValue(): Int {
    position++

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
            expValue = expValue * C.BASE_TEN + (currentByteInt - C.ZERO_INT)
            hasExpDigits = true
            position++
        } else break
    }

    if (!hasExpDigits) throwError(C.ERR_EXPECTED_EXPONENT_DIGITS)
    return if (isExpNegative) -expValue else expValue
}

@Suppress("NOTHING_TO_INLINE")
private inline fun getFloatPowerOfTen(exponent: Int): Float {
    return if (exponent > 0) {
        if (exponent < C.POWERS_OF_TEN_FLOAT.size) {
            C.POWERS_OF_TEN_FLOAT[exponent]
        } else {
            10.0f.pow(exponent.toFloat())
        }
    } else {
        val absExp = -exponent
        if (absExp < C.INVERSE_POWERS_OF_TEN_FLOAT.size) {
            C.INVERSE_POWERS_OF_TEN_FLOAT[absExp]
        } else {
            10.0f.pow(exponent.toFloat())
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun getDoublePowerOfTen(exponent: Int): Double {
    return if (exponent > 0) {
        if (exponent < C.POWERS_OF_TEN.size) {
            C.POWERS_OF_TEN[exponent]
        } else {
            10.0.pow(exponent.toDouble())
        }
    } else {
        val absExp = -exponent
        if (absExp < C.INVERSE_POWERS_OF_TEN.size) {
            C.INVERSE_POWERS_OF_TEN[absExp]
        } else {
            10.0.pow(exponent.toDouble())
        }
    }
}

/**
 * Reads a JSON integer and returns it as an Int.
 * Optimized for common small integers.
 * Used by KSP-generated serializers.
 */
fun GhostJsonReader.nextInt(): Int = nextIntImpl(
    prepareNumericHeader = { prepareNumericHeader() },
    getPosition = { position },
    getLimit = { limit },
    getByte = { getByte(it) },
    handleLeadingZero = { handleLeadingZero() },
    parseIntDigits = { neg, start -> parseIntDigits(neg, start) },
    consumeNumericCoercionFooter = { consumeNumericCoercionFooter() }
)

/**
 * Reads a JSON long and returns it as a Long.
 * Optimized for common small longs.
 * Used by KSP-generated serializers.
 */
fun GhostJsonReader.nextLong(): Long = nextLongImpl(
    prepareNumericHeader = { prepareNumericHeader() },
    getPosition = { position },
    getLimit = { limit },
    getByte = { getByte(it) },
    handleLeadingZero = { handleLeadingZero() },
    parseLongDigits = { neg, start -> parseLongDigits(neg, start) },
    consumeNumericCoercionFooter = { consumeNumericCoercionFooter() }
)

private fun GhostJsonReader.prepareNumericHeader(): Int {
    if (nextTokenByte == -1) skipWhitespace()
    if (position >= limit) throwError(C.ERR_EXPECTED_NUMBER)

    var header = 0
    var token = nextTokenByte

    if (token == C.QUOTE_INT) {
        if (!coerceStringsToNumbers) throwError(C.ERR_COERCION_DISABLED)
        position++
        nextTokenByte = -1
        skipWhitespace()
        if (position >= limit) throwError(C.ERR_EXPECTED_NUMBER)
        token = nextTokenByte
        header = header or C.NUMERIC_HEADER_QUOTED
    }

    if (token == C.MINUS_INT) {
        if (position + 1 >= limit) throwError(C.ERR_ISOLATED_MINUS)
        position++
        nextTokenByte = -1
        header = header or C.NUMERIC_HEADER_NEGATIVE
    }

    return header
}

private fun GhostJsonReader.handleLeadingZero() {
    val nextCursor = position + 1
    if (nextCursor < limit) {
        val nextDigitByte = getByte(nextCursor)
        if ((C.DIGIT_BITMASK shr nextDigitByte) and C.BYTE_SHIFT_UNIT != C.RESULT_NONE) {
            throwError(C.ERR_LEADING_ZEROS)
        }
    }
    internalSkip(1)
}

private fun GhostJsonReader.parseIntDigits(
    isNegative: Boolean,
    startOfNumber: Int
): Int = parseIntDigitsImpl(
    isNegative = isNegative,
    startOfNumber = startOfNumber,
    readNumericLoop = { onDigit, onBreak -> readNumericLoop(onDigit, onBreak) },
    calculateIntWithOverflowCheck = { curr, digit, neg -> calculateIntWithOverflowCheck(curr, digit, neg) },
    isNumericSeparator = { isNumericSeparator(it) },
    setPosition = { position = it },
    nextDouble = { nextDouble() },
    throwError = { throwError(it) },
    setNextTokenByte = { nextTokenByte = it }
)

private fun GhostJsonReader.parseLongDigits(
    isNegative: Boolean,
    startOfNumber: Int
): Long = parseLongDigitsImpl(
    isNegative = isNegative,
    startOfNumber = startOfNumber,
    readNumericLoop = { onDigit, onBreak -> readNumericLoop(onDigit, onBreak) },
    calculateLongWithOverflowCheck = { curr, digit, neg -> calculateLongWithOverflowCheck(curr, digit, neg) },
    isNumericSeparator = { isNumericSeparator(it) },
    setPosition = { position = it },
    nextDouble = { nextDouble() },
    throwError = { throwError(it) },
    setNextTokenByte = { nextTokenByte = it }
)

@Suppress("NOTHING_TO_INLINE")
private inline fun GhostJsonReader.consumeNumericCoercionFooter() {
    if (position >= limit || getByte(position) != C.QUOTE_INT) {
        throwError(C.ERR_EXPECTED_COERCION_QUOTE)
    }
    internalSkip(1)
    skipWhitespace()
}

@Suppress("NOTHING_TO_INLINE")
private inline fun GhostJsonReader.calculateIntWithOverflowCheck(
    current: Int,
    digitValue: Int,
    isNegative: Boolean
): Int {
    if (current > C.INT_OVERFLOW_LIMIT ||
        (current == C.INT_OVERFLOW_LIMIT &&
                digitValue > (if (isNegative) C.INT_MIN_LAST_DIGIT else C.INT_MAX_LAST_DIGIT))
    ) {
        throwError(C.ERR_INT_OVERFLOW)
    }
    return current * C.BASE_TEN + digitValue
}

@Suppress("NOTHING_TO_INLINE")
private inline fun GhostJsonReader.calculateLongWithOverflowCheck(
    current: Long,
    digitValue: Int,
    isNegative: Boolean
): Long {
    if (current > C.LONG_OVERFLOW_LIMIT ||
        (current == C.LONG_OVERFLOW_LIMIT &&
                digitValue > C.LONG_MAX_LAST_DIGIT)
    ) {
        if (isNegative && current == C.LONG_OVERFLOW_LIMIT &&
            digitValue == C.LONG_MIN_LAST_DIGIT
        ) {
            return Long.MIN_VALUE
        }
        throwError(C.ERR_LONG_OVERFLOW)
    }
    return current * C.BASE_TEN + digitValue
}

private fun GhostJsonReader.validateLeadingZero() {
    if (
        position < limit &&
        getByte(position) == C.ZERO_INT &&
        position + 1 < limit
    ) {
        val nextDigitByte = getByte(position + 1)
        if ((C.DIGIT_BITMASK shr nextDigitByte) and 1L != 0L) {
            throwError(C.ERR_LEADING_ZEROS)
        }
    }
}

private fun GhostJsonReader.validateNumericRangeFloat(valueToValidate: Float) {
    if (valueToValidate.isInfinite() || valueToValidate.isNaN()) {
        throwError(C.ERR_NUMERIC_OVERFLOW)
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun isNumericSeparator(byteCode: Int): Boolean {
    return byteCode == C.DOT_INT || byteCode == C.EXP_LOWER_INT || byteCode == C.EXP_UPPER_INT
}

@Suppress("NOTHING_TO_INLINE")
private inline fun isExponentMarker(markerByte: Int): Boolean {
    return (markerByte or C.CASE_INSENSITIVE_MASK) == C.EXP_LOWER_INT
}

private fun GhostJsonReader.validateNumericRange(valueToValidate: Double) {
    if (valueToValidate.isInfinite() || valueToValidate.isNaN()) {
        throwError(C.ERR_NUMERIC_OVERFLOW)
    }
}

@InternalGhostApi
fun GhostJsonReader.skipNumber() = skipNumberImpl(
    prepareNumericHeader = { prepareNumericHeader() },
    getPosition = { position },
    setPosition = { position = it },
    getLimit = { limit },
    getByte = { getByte(it) },
    isDigit = { isDigit(it) },
    readNumericLoop = { onDigit -> readNumericLoop(onDigit) },
    throwError = { throwError(it) },
    consumeNumericCoercionFooter = { consumeNumericCoercionFooter() },
    setNextTokenByte = { nextTokenByte = it }
)

@Suppress("NOTHING_TO_INLINE")
private inline fun GhostJsonReader.readNumericLoop(
    crossinline onDigit: (byte: Int) -> Unit,
    crossinline onBreak: (byte: Int) -> Unit = {}
) {
    if (isStreaming) {
        while (position < limit) {
            val byte = source[position]
            if (isDigit(byte)) {
                onDigit(byte)
                position++
            } else {
                onBreak(byte)
                break
            }
        }
    } else {
        val data = rawData
        while (position < limit) {
            val byte = data[position].toInt() and C.BYTE_MASK
            if (isDigit(byte)) {
                onDigit(byte)
                position++
            } else {
                onBreak(byte)
                break
            }
        }
    }
}

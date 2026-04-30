@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import kotlin.math.pow

/**
 * Reads a JSON number and returns it as a Float.
 * Uses a zero-allocation, register-based loop for maximum speed.
 */
fun GhostJsonReader.nextFloat(): Float {
    prepareForNumericParsing()
    val isQuoted = consumeNumericCoercionHeader()

    val isNegativeValue = isNegative(position)
    if (isNegativeValue) internalSkip(1)

    validateLeadingZero()

    var mantissa = 0L
    var exponent = 0
    var digitCount = 0

    // Integer part
    while (position < limit) {
        val currentByteInt = getByte(position)
        if (isDigit(currentByteInt)) {
            val digit = currentByteInt - GhostJsonConstants.ZERO_INT
            if (digitCount < GhostJsonConstants.FLOAT_PRECISION_LIMIT) {
                mantissa = mantissa * GhostJsonConstants.BASE_TEN + digit
                digitCount++
            } else {
                exponent++
            }
            position++
            nextTokenByte = -1
        } else break
    }

    if (digitCount == 0) throwError(GhostJsonConstants.ERR_EXPECTED_INT_PART)

    // Decimal part
    if (position < limit && getByte(position) == GhostJsonConstants.DOT_INT) {
        internalSkip(1)
        val startPos = position
        while (position < limit) {
            val currentByteInt = getByte(position)
            if (isDigit(currentByteInt)) {
                val digit = currentByteInt - GhostJsonConstants.ZERO_INT
                if (digitCount < GhostJsonConstants.FLOAT_PRECISION_LIMIT) {
                    mantissa = mantissa * GhostJsonConstants.BASE_TEN + digit
                    digitCount++
                    exponent--
                }
                position++
                nextTokenByte = -1
            } else break
        }
        if (position == startPos) {
            throwError(GhostJsonConstants.ERR_EXPECTED_DECIMAL_DIGITS)
        }
    }

    // Exponent part
    if (position < limit && isExponentMarker(getByte(position))) {
        exponent += parseExponentValue()
    }

    var result = mantissa.toFloat()
    if (exponent != 0) {
        result *= getFloatPowerOfTen(exponent)
    }

    if (isNegativeValue) result = -result
    validateNumericRangeFloat(result)

    if (isQuoted) consumeNumericCoercionFooter()

    return result
}

fun GhostJsonReader.nextDouble(): Double {
    prepareForNumericParsing()
    val isQuoted = consumeNumericCoercionHeader()

    val isNegativeValue = isNegative(position)
    if (isNegativeValue) internalSkip(1)

    validateLeadingZero()

    var mantissa = 0L
    var exponent = 0
    var digitCount = 0

    // Integer part
    while (position < limit) {
        val currentByteInt = getByte(position)
        if (isDigit(currentByteInt)) {
            val digit = currentByteInt - GhostJsonConstants.ZERO_INT
            if (digitCount < GhostJsonConstants.DOUBLE_PRECISION_LIMIT) {
                mantissa = mantissa * GhostJsonConstants.BASE_TEN + digit
                digitCount++
            } else {
                exponent++
            }
            position++
            nextTokenByte = -1
        } else break
    }

    if (digitCount == 0) throwError(GhostJsonConstants.ERR_EXPECTED_INT_PART)

    // Decimal part
    if (position < limit && getByte(position) == GhostJsonConstants.DOT_INT) {
        internalSkip(1)
        val startPos = position
        while (position < limit) {
            val currentByteInt = getByte(position)
            if (isDigit(currentByteInt)) {
                val digitValue = currentByteInt - GhostJsonConstants.ZERO_INT
                if (digitCount < GhostJsonConstants.DOUBLE_PRECISION_LIMIT) {
                    mantissa = mantissa * GhostJsonConstants.BASE_TEN + digitValue
                    digitCount++
                    exponent--
                }
                position++
                nextTokenByte = -1
            } else break
        }
        if (position == startPos) throwError(GhostJsonConstants.ERR_EXPECTED_DECIMAL_DIGITS)
    }

    // Exponent part
    if (position < limit && isExponentMarker(getByte(position))) {
        exponent += parseExponentValue()
    }

    var result = mantissa.toDouble()
    if (exponent != 0) {
        result *= getDoublePowerOfTen(exponent)
    }

    if (isNegativeValue) result = -result
    validateNumericRange(result)

    if (isQuoted) consumeNumericCoercionFooter()

    return result
}

@Suppress("NOTHING_TO_INLINE")
private inline fun GhostJsonReader.parseExponentValue(): Int {
    internalSkip(1)
    var isExpNegative = false
    if (position < limit) {
        val marker = getByte(position)
        if (marker == GhostJsonConstants.MINUS_INT) {
            isExpNegative = true; internalSkip(1)
        } else if (marker == GhostJsonConstants.PLUS_INT) {
            internalSkip(1)
        }
    }
    var expValue = 0
    var hasExpDigits = false
    while (position < limit) {
        val currentByteInt = getByte(position)
        if (isDigit(currentByteInt)) {
            expValue =
                expValue * GhostJsonConstants.BASE_TEN + (currentByteInt - GhostJsonConstants.ZERO_INT)
            hasExpDigits = true
            position++
            nextTokenByte = -1
        } else break
    }
    if (!hasExpDigits) throwError(GhostJsonConstants.ERR_EXPECTED_EXPONENT_DIGITS)
    return if (isExpNegative) -expValue else expValue
}

@Suppress("NOTHING_TO_INLINE")
private inline fun getFloatPowerOfTen(exponent: Int): Float {
    return if (exponent > 0) {
        if (exponent < GhostJsonConstants.POWERS_OF_TEN_FLOAT.size) {
            GhostJsonConstants.POWERS_OF_TEN_FLOAT[exponent]
        } else {
            10.0f.pow(exponent.toFloat())
        }
    } else {
        val absExp = -exponent
        if (absExp < GhostJsonConstants.INVERSE_POWERS_OF_TEN_FLOAT.size) {
            GhostJsonConstants.INVERSE_POWERS_OF_TEN_FLOAT[absExp]
        } else {
            10.0f.pow(exponent.toFloat())
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun getDoublePowerOfTen(exponent: Int): Double {
    return if (exponent > 0) {
        if (exponent < GhostJsonConstants.POWERS_OF_TEN.size) {
            GhostJsonConstants.POWERS_OF_TEN[exponent]
        } else {
            10.0.pow(exponent.toDouble())
        }
    } else {
        val absExp = -exponent
        if (absExp < GhostJsonConstants.INVERSE_POWERS_OF_TEN.size) {
            GhostJsonConstants.INVERSE_POWERS_OF_TEN[absExp]
        } else {
            10.0.pow(exponent.toDouble())
        }
    }
}

/**
 * Reads a JSON integer and returns it as an Int.
 * Optimized for common small integers.
 */
fun GhostJsonReader.nextInt(): Int {
    prepareForNumericParsing()
    val isQuoted = consumeNumericCoercionHeader()
    val startOfNumber = position
    val isNegativeValue = isNegative(position)
    if (isNegativeValue) internalSkip(1)

    val absoluteValue = if (position < limit && getByte(position) == GhostJsonConstants.ZERO_INT) {
        handleLeadingZero()
        0
    } else {
        parseIntDigits(isNegativeValue, startOfNumber)
    }
    val finalIntResult = if (isNegativeValue) -absoluteValue else absoluteValue
    if (isQuoted) consumeNumericCoercionFooter()
    return finalIntResult
}

fun GhostJsonReader.nextLong(): Long {
    prepareForNumericParsing()
    val isQuoted = consumeNumericCoercionHeader()
    val startOfNumber = position
    val isNegativeValue = isNegative(position)
    if (isNegativeValue) internalSkip(1)

    val absoluteValue = if (position < limit && getByte(position) == GhostJsonConstants.ZERO_INT) {
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
    if (isQuoted) consumeNumericCoercionFooter()
    return finalLongResult
}

private fun GhostJsonReader.prepareForNumericParsing() {
    if (nextTokenByte == -1) skipWhitespace()
    if (position >= limit) throwError(GhostJsonConstants.ERR_EXPECTED_NUMBER)
}

private fun GhostJsonReader.consumeNumericCoercionHeader(): Boolean {
    val isQuoted = getByte(position) == GhostJsonConstants.QUOTE_INT
    if (isQuoted) {
        if (!coerceStringsToNumbers) throwError(GhostJsonConstants.ERR_COERCION_DISABLED)
        internalSkip(1)
        peekNextToken().ignore()
    }
    return isQuoted
}

private fun GhostJsonReader.consumeNumericCoercionFooter() {
    if (position >= limit || getByte(position) != GhostJsonConstants.QUOTE_INT) {
        throwError(GhostJsonConstants.ERR_EXPECTED_COERCION_QUOTE)
    }
    internalSkip(1)
    peekNextToken().ignore()
}

private fun GhostJsonReader.isNegative(cursor: Int): Boolean {
    if (cursor < limit && getByte(cursor) == GhostJsonConstants.MINUS_INT) {
        if (cursor + 1 >= limit) throwError(GhostJsonConstants.ERR_ISOLATED_MINUS)
        return true
    }
    return false
}

private fun GhostJsonReader.handleLeadingZero() {
    val nextCursor = position + 1
    if (nextCursor < limit) {
        val nextDigitByte = getByte(nextCursor)
        // Bitwise digit check: (MASK shr value) & 1
        if ((GhostJsonConstants.DIGIT_BITMASK shr nextDigitByte) and 1L != 0L) {
            throwError(GhostJsonConstants.ERR_LEADING_ZEROS)
        }
    }
    internalSkip(1)
}

private fun GhostJsonReader.parseIntDigits(isNegative: Boolean, startOfNumber: Int): Int {
    var accumulatedValue = 0
    var hasDigitsFound = false
    while (position < limit) {
        val currentByteInt = getByte(position)
        if (isDigit(currentByteInt)) {
            accumulatedValue = calculateIntWithOverflowCheck(
                accumulatedValue,
                currentByteInt - GhostJsonConstants.ZERO_INT,
                isNegative
            )
            hasDigitsFound = true
            position++
            nextTokenByte = -1
        } else if (isNumericSeparator(currentByteInt)) {
            position = startOfNumber
            return nextDouble().toInt()
        } else break
    }
    if (!hasDigitsFound) throwError(GhostJsonConstants.ERR_EXPECTED_INT_PART)
    return accumulatedValue
}

private fun GhostJsonReader.parseLongDigits(isNegative: Boolean, startOfNumber: Int): Long {
    var accumulatedValue = 0L
    var hasDigitsFound = false
    while (position < limit) {
        val currentByteInt = getByte(position)
        if (isDigit(currentByteInt)) {
            accumulatedValue = calculateLongWithOverflowCheck(
                accumulatedValue,
                currentByteInt - GhostJsonConstants.ZERO_INT,
                isNegative
            )
            hasDigitsFound = true
            position++
            nextTokenByte = -1
        } else if (isNumericSeparator(currentByteInt)) {
            position = startOfNumber
            return nextDouble().toLong()
        } else break
    }
    if (!hasDigitsFound) throwError(GhostJsonConstants.ERR_EXPECTED_INT_PART)
    return accumulatedValue
}

@Suppress("NOTHING_TO_INLINE")
private inline fun GhostJsonReader.calculateIntWithOverflowCheck(
    current: Int,
    digitValue: Int,
    isNegative: Boolean
): Int {
    if (current > GhostJsonConstants.INT_OVERFLOW_LIMIT ||
        (current == GhostJsonConstants.INT_OVERFLOW_LIMIT &&
                digitValue > (if (isNegative) GhostJsonConstants.INT_MIN_LAST_DIGIT else GhostJsonConstants.INT_MAX_LAST_DIGIT))
    ) {
        throwError(GhostJsonConstants.ERR_INT_OVERFLOW)
    }
    return current * GhostJsonConstants.BASE_TEN + digitValue
}

@Suppress("NOTHING_TO_INLINE")
private inline fun GhostJsonReader.calculateLongWithOverflowCheck(
    current: Long,
    digitValue: Int,
    isNegative: Boolean
): Long {
    if (current > GhostJsonConstants.LONG_OVERFLOW_LIMIT ||
        (current == GhostJsonConstants.LONG_OVERFLOW_LIMIT &&
                digitValue > GhostJsonConstants.LONG_MAX_LAST_DIGIT)
    ) {
        if (isNegative && current == GhostJsonConstants.LONG_OVERFLOW_LIMIT &&
            digitValue == GhostJsonConstants.LONG_MIN_LAST_DIGIT
        ) {
            return Long.MIN_VALUE
        }
        throwError(GhostJsonConstants.ERR_LONG_OVERFLOW)
    }
    return current * GhostJsonConstants.BASE_TEN + digitValue
}

private fun GhostJsonReader.validateLeadingZero() {
    if (position < limit && getByte(position) == GhostJsonConstants.ZERO_INT && position + 1 < limit) {
        val nextDigitByte = getByte(position + 1)
        if ((GhostJsonConstants.DIGIT_BITMASK shr nextDigitByte) and 1L != 0L) {
            throwError(GhostJsonConstants.ERR_LEADING_ZEROS)
        }
    }
}

private fun GhostJsonReader.validateNumericRangeFloat(valueToValidate: Float) {
    if (valueToValidate.isInfinite() || valueToValidate.isNaN()) {
        throwError(GhostJsonConstants.ERR_NUMERIC_OVERFLOW)
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun isNumericSeparator(byteCode: Int): Boolean {
    return byteCode == GhostJsonConstants.DOT_INT ||
            byteCode == GhostJsonConstants.EXP_LOWER_INT ||
            byteCode == GhostJsonConstants.EXP_UPPER_INT
}

@Suppress("NOTHING_TO_INLINE")
private inline fun isExponentMarker(markerByte: Int): Boolean {
    return markerByte == GhostJsonConstants.EXP_LOWER_INT ||
            markerByte == GhostJsonConstants.EXP_UPPER_INT
}

private fun GhostJsonReader.validateNumericRange(valueToValidate: Double) {
    if (valueToValidate.isInfinite() || valueToValidate.isNaN()) {
        throwError(GhostJsonConstants.ERR_NUMERIC_OVERFLOW)
    }
}

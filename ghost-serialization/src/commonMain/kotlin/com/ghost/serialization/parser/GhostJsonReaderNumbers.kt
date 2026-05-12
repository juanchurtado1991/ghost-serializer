@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.GhostJsonConstants.BASE_TEN
import com.ghost.serialization.parser.GhostJsonConstants.BYTE_MASK
import com.ghost.serialization.parser.GhostJsonConstants.BYTE_SHIFT_UNIT
import com.ghost.serialization.parser.GhostJsonConstants.CASE_INSENSITIVE_MASK
import com.ghost.serialization.parser.GhostJsonConstants.DIGIT_BITMASK
import com.ghost.serialization.parser.GhostJsonConstants.DOT_INT
import com.ghost.serialization.parser.GhostJsonConstants.DOUBLE_PRECISION_LIMIT
import com.ghost.serialization.parser.GhostJsonConstants.ERR_COERCION_DISABLED
import com.ghost.serialization.parser.GhostJsonConstants.ERR_EXPECTED_COERCION_QUOTE
import com.ghost.serialization.parser.GhostJsonConstants.ERR_EXPECTED_DECIMAL_DIGITS
import com.ghost.serialization.parser.GhostJsonConstants.ERR_EXPECTED_EXPONENT_DIGITS
import com.ghost.serialization.parser.GhostJsonConstants.ERR_EXPECTED_INT_PART
import com.ghost.serialization.parser.GhostJsonConstants.ERR_EXPECTED_NUMBER
import com.ghost.serialization.parser.GhostJsonConstants.ERR_INT_OVERFLOW
import com.ghost.serialization.parser.GhostJsonConstants.ERR_ISOLATED_MINUS
import com.ghost.serialization.parser.GhostJsonConstants.ERR_LEADING_ZEROS
import com.ghost.serialization.parser.GhostJsonConstants.ERR_LONG_OVERFLOW
import com.ghost.serialization.parser.GhostJsonConstants.ERR_NUMERIC_OVERFLOW
import com.ghost.serialization.parser.GhostJsonConstants.EXP_LOWER_INT
import com.ghost.serialization.parser.GhostJsonConstants.EXP_UPPER_INT
import com.ghost.serialization.parser.GhostJsonConstants.FLOAT_PRECISION_LIMIT
import com.ghost.serialization.parser.GhostJsonConstants.INT_MAX_LAST_DIGIT
import com.ghost.serialization.parser.GhostJsonConstants.INT_MIN_LAST_DIGIT
import com.ghost.serialization.parser.GhostJsonConstants.INT_OVERFLOW_LIMIT
import com.ghost.serialization.parser.GhostJsonConstants.INT_SAFE_DIGITS
import com.ghost.serialization.parser.GhostJsonConstants.INVERSE_POWERS_OF_TEN
import com.ghost.serialization.parser.GhostJsonConstants.INVERSE_POWERS_OF_TEN_FLOAT
import com.ghost.serialization.parser.GhostJsonConstants.LONG_MAX_LAST_DIGIT
import com.ghost.serialization.parser.GhostJsonConstants.LONG_MIN_LAST_DIGIT
import com.ghost.serialization.parser.GhostJsonConstants.LONG_OVERFLOW_LIMIT
import com.ghost.serialization.parser.GhostJsonConstants.LONG_SAFE_DIGITS
import com.ghost.serialization.parser.GhostJsonConstants.MINUS_INT
import com.ghost.serialization.parser.GhostJsonConstants.NUMERIC_HEADER_NEGATIVE
import com.ghost.serialization.parser.GhostJsonConstants.NUMERIC_HEADER_QUOTED
import com.ghost.serialization.parser.GhostJsonConstants.PLUS_INT
import com.ghost.serialization.parser.GhostJsonConstants.POWERS_OF_TEN
import com.ghost.serialization.parser.GhostJsonConstants.POWERS_OF_TEN_FLOAT
import com.ghost.serialization.parser.GhostJsonConstants.QUOTE_INT
import com.ghost.serialization.parser.GhostJsonConstants.RESULT_NONE
import com.ghost.serialization.parser.GhostJsonConstants.ZERO_INT
import kotlin.math.pow

/**
 * Reads a JSON number and returns it as a Float.
 * Uses a zero-allocation, register-based loop for maximum speed.
 */
fun GhostJsonReader.nextFloat(): Float {
    val header = prepareNumericHeader()
    val isQuoted = (header and NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and NUMERIC_HEADER_NEGATIVE) != 0

    validateLeadingZero()

    var mantissa = 0L
    var exponent = 0
    var digitCount = 0

    // Integer part
    nextTokenByte = -1
    readNumericLoop(
        onDigit = { byte ->
            val digit = byte - ZERO_INT
            if (digitCount < FLOAT_PRECISION_LIMIT) {
                mantissa = mantissa * BASE_TEN + digit
                digitCount++
            } else {
                exponent++
            }
        }
    )

    if (digitCount == 0) throwError(ERR_EXPECTED_INT_PART)

    // Decimal part
    if (position < limit && getByte(position) == DOT_INT) {
        position++ // nextTokenByte already -1
        val startPos = position
        readNumericLoop(
            onDigit = { byte ->
                val digit = byte - ZERO_INT
                if (digitCount < FLOAT_PRECISION_LIMIT) {
                    mantissa = mantissa * BASE_TEN + digit
                    digitCount++
                    exponent--
                }
            }
        )
        if (position == startPos) throwError(ERR_EXPECTED_DECIMAL_DIGITS)
    }

    // Exponent part
    if (
        position < limit &&
        isExponentMarker(getByte(position))
    ) {
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
    val header = prepareNumericHeader()
    val isQuoted = (header and NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and NUMERIC_HEADER_NEGATIVE) != 0

    validateLeadingZero()

    var mantissa = 0L
    var exponent = 0
    var digitCount = 0

    // Integer part
    nextTokenByte = -1
    readNumericLoop(
        onDigit = { byte ->
            val digit = byte - ZERO_INT
            if (digitCount < DOUBLE_PRECISION_LIMIT) {
                mantissa = mantissa * BASE_TEN + digit
                digitCount++
            } else {
                exponent++
            }
        }
    )

    if (digitCount == 0) throwError(ERR_EXPECTED_INT_PART)

    // Decimal part
    if (position < limit && getByte(position) == DOT_INT) {
        position++ // nextTokenByte already -1
        val startPos = position
        readNumericLoop(
            onDigit = { byte ->
                val digitValue = byte - ZERO_INT
                if (digitCount < DOUBLE_PRECISION_LIMIT) {
                    mantissa = mantissa * BASE_TEN + digitValue
                    digitCount++
                    exponent--
                }
            }
        )
        if (position == startPos) throwError(ERR_EXPECTED_DECIMAL_DIGITS)
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
    position++

    var isExpNegative = false
    if (position < limit) {
        val marker = getByte(position)
        if (marker == MINUS_INT) {
            isExpNegative = true
            position++
        } else if (marker == PLUS_INT) {
            position++
        }
    }

    var expValue = 0
    var hasExpDigits = false

    while (position < limit) {
        val currentByteInt = getByte(position)
        if (isDigit(currentByteInt)) {
            expValue = expValue * BASE_TEN + (currentByteInt - ZERO_INT)
            hasExpDigits = true
            position++
        } else break
    }

    if (!hasExpDigits) throwError(ERR_EXPECTED_EXPONENT_DIGITS)
    return if (isExpNegative) -expValue else expValue
}

@Suppress("NOTHING_TO_INLINE")
private inline fun getFloatPowerOfTen(exponent: Int): Float {
    return if (exponent > 0) {
        if (exponent < POWERS_OF_TEN_FLOAT.size) {
            POWERS_OF_TEN_FLOAT[exponent]
        } else {
            10.0f.pow(exponent.toFloat())
        }
    } else {
        val absExp = -exponent
        if (absExp < INVERSE_POWERS_OF_TEN_FLOAT.size) {
            INVERSE_POWERS_OF_TEN_FLOAT[absExp]
        } else {
            10.0f.pow(exponent.toFloat())
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun getDoublePowerOfTen(exponent: Int): Double {
    return if (exponent > 0) {
        if (exponent < POWERS_OF_TEN.size) {
            POWERS_OF_TEN[exponent]
        } else {
            10.0.pow(exponent.toDouble())
        }
    } else {
        val absExp = -exponent
        if (absExp < INVERSE_POWERS_OF_TEN.size) {
            INVERSE_POWERS_OF_TEN[absExp]
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
    val header = prepareNumericHeader()
    val isQuoted = (header and NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and NUMERIC_HEADER_NEGATIVE) != 0
    
    val startOfNumber = position

    val absoluteValue = if (
        position < limit &&
        getByte(position) == ZERO_INT
    ) {
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
    val header = prepareNumericHeader()
    val isQuoted = (header and NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and NUMERIC_HEADER_NEGATIVE) != 0
    
    val startOfNumber = position

    val absoluteValue = if (
        position < limit &&
        getByte(position) == ZERO_INT
    ) {
        handleLeadingZero()
        RESULT_NONE
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

private fun GhostJsonReader.prepareNumericHeader(): Int {
    if (nextTokenByte == -1) skipWhitespace()
    if (position >= limit) throwError(ERR_EXPECTED_NUMBER)

    var header = 0
    var token = nextTokenByte

    if (token == QUOTE_INT) {
        if (!coerceStringsToNumbers) throwError(ERR_COERCION_DISABLED)
        position++
        nextTokenByte = -1
        skipWhitespace()
        if (position >= limit) throwError(ERR_EXPECTED_NUMBER)
        token = nextTokenByte
        header = header or NUMERIC_HEADER_QUOTED
    }

    if (token == MINUS_INT) {
        if (position + 1 >= limit) throwError(ERR_ISOLATED_MINUS)
        position++
        nextTokenByte = -1
        header = header or NUMERIC_HEADER_NEGATIVE
    }

    return header
}

private fun GhostJsonReader.handleLeadingZero() {
    val nextCursor = position + 1
    if (nextCursor < limit) {
        val nextDigitByte = getByte(nextCursor)
        if ((DIGIT_BITMASK shr nextDigitByte) and BYTE_SHIFT_UNIT != RESULT_NONE) {
            throwError(ERR_LEADING_ZEROS)
        }
    }
    internalSkip(1)
}

private fun GhostJsonReader.parseIntDigits(
    isNegative: Boolean,
    startOfNumber: Int
): Int {
    var accumulatedValue = 0
    var digitCount = 0
    var hasDigitsFound = false
    nextTokenByte = -1
    var earlyExitResult: Int? = null
    readNumericLoop(
        onDigit = { byte ->
            val digit = byte - ZERO_INT
            if (digitCount < INT_SAFE_DIGITS) {
                accumulatedValue = accumulatedValue * BASE_TEN + digit
            } else {
                accumulatedValue = calculateIntWithOverflowCheck(
                    accumulatedValue,
                    digit,
                    isNegative
                )
            }
            digitCount++
            hasDigitsFound = true
        },
        onBreak = { byte ->
            if (isNumericSeparator(byte)) {
                position = startOfNumber
                earlyExitResult = nextDouble().toInt()
            }
        }
    )

    if (earlyExitResult != null) return earlyExitResult
    if (!hasDigitsFound) throwError(ERR_EXPECTED_INT_PART)
    return accumulatedValue
}

private fun GhostJsonReader.parseLongDigits(
    isNegative: Boolean,
    startOfNumber: Int
): Long {
    var accumulatedValue = 0L
    var digitCount = 0
    var hasDigitsFound = false
    nextTokenByte = -1
    var earlyExitResult: Long? = null
    readNumericLoop(
        onDigit = { byte ->
            val digit = byte - ZERO_INT
            accumulatedValue = if (digitCount < LONG_SAFE_DIGITS) {
                accumulatedValue * BASE_TEN + digit
            } else {
                calculateLongWithOverflowCheck(
                    accumulatedValue,
                    digit,
                    isNegative
                )
            }
            digitCount++
            hasDigitsFound = true
        },
        onBreak = { byte ->
            if (isNumericSeparator(byte)) {
                position = startOfNumber
                earlyExitResult = nextDouble().toLong()
            }
        }
    )

    if (earlyExitResult != null) return earlyExitResult
    if (!hasDigitsFound) throwError(ERR_EXPECTED_INT_PART)
    return accumulatedValue
}

@Suppress("NOTHING_TO_INLINE")
private inline fun GhostJsonReader.consumeNumericCoercionFooter() {
    if (position >= limit || getByte(position) != QUOTE_INT) {
        throwError(ERR_EXPECTED_COERCION_QUOTE)
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
    if (current > INT_OVERFLOW_LIMIT ||
        (current == INT_OVERFLOW_LIMIT &&
                digitValue > (if (isNegative) INT_MIN_LAST_DIGIT else INT_MAX_LAST_DIGIT))
    ) {
        throwError(ERR_INT_OVERFLOW)
    }
    return current * BASE_TEN + digitValue
}

@Suppress("NOTHING_TO_INLINE")
private inline fun GhostJsonReader.calculateLongWithOverflowCheck(
    current: Long,
    digitValue: Int,
    isNegative: Boolean
): Long {
    if (current > LONG_OVERFLOW_LIMIT ||
        (current == LONG_OVERFLOW_LIMIT &&
                digitValue > LONG_MAX_LAST_DIGIT)
    ) {
        if (isNegative && current == LONG_OVERFLOW_LIMIT &&
            digitValue == LONG_MIN_LAST_DIGIT
        ) {
            return Long.MIN_VALUE
        }
        throwError(ERR_LONG_OVERFLOW)
    }
    return current * BASE_TEN + digitValue
}

private fun GhostJsonReader.validateLeadingZero() {
    if (
        position < limit &&
        getByte(position) == ZERO_INT &&
        position + 1 < limit
    ) {
        val nextDigitByte = getByte(position + 1)
        if ((DIGIT_BITMASK shr nextDigitByte) and 1L != 0L) {
            throwError(ERR_LEADING_ZEROS)
        }
    }
}

private fun GhostJsonReader.validateNumericRangeFloat(valueToValidate: Float) {
    if (valueToValidate.isInfinite() || valueToValidate.isNaN()) {
        throwError(ERR_NUMERIC_OVERFLOW)
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun isNumericSeparator(byteCode: Int): Boolean {
    return byteCode == DOT_INT || byteCode == EXP_LOWER_INT || byteCode == EXP_UPPER_INT
}

@Suppress("NOTHING_TO_INLINE")
private inline fun isExponentMarker(markerByte: Int): Boolean {
    return (markerByte or CASE_INSENSITIVE_MASK) == EXP_LOWER_INT
}

private fun GhostJsonReader.validateNumericRange(valueToValidate: Double) {
    if (valueToValidate.isInfinite() || valueToValidate.isNaN()) {
        throwError(ERR_NUMERIC_OVERFLOW)
    }
}

@InternalGhostApi
fun GhostJsonReader.skipNumber() {
    val header = prepareNumericHeader()
    val isQuoted = (header and NUMERIC_HEADER_QUOTED) != 0

    var hasDigits = false

    // Integer part
    if (position < limit && getByte(position) == ZERO_INT) {
        position++
        hasDigits = true
        if (position < limit && isDigit(getByte(position))) {
            throwError(ERR_LEADING_ZEROS)
        }
    } else {
        readNumericLoop(onDigit = { hasDigits = true })
    }

    if (!hasDigits) {
        throwError(ERR_EXPECTED_INT_PART)
    }

    // Decimal part
    if (position < limit && getByte(position) == DOT_INT) {
        position++
        var hasDecimalDigits = false
        readNumericLoop(onDigit = { hasDecimalDigits = true })
        if (!hasDecimalDigits) throwError(ERR_EXPECTED_DECIMAL_DIGITS)
    }

    // Exponent part
    if (position < limit) {
        val byte = getByte(position)
        if (byte == EXP_LOWER_INT || byte == EXP_UPPER_INT) {
            position++
            if (position < limit) {
                val sign = getByte(position)
                if (sign == PLUS_INT || sign == MINUS_INT) {
                    position++
                }
            }

            var hasExpDigits = false
            readNumericLoop(onDigit = { hasExpDigits = true })
            if (!hasExpDigits) throwError(ERR_EXPECTED_EXPONENT_DIGITS)
        }
    }

    if (isQuoted) consumeNumericCoercionFooter()
    nextTokenByte = -1
}

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
            val byte = data[position].toInt() and BYTE_MASK
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

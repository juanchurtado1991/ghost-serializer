@file:OptIn(InternalGhostApi::class)
@file:Suppress("NOTHING_TO_INLINE")

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.GhostJsonConstants as C

/**
 * Parses and returns the next [Float] value from the JSON stream.
 *
 * Supports coercion from strings if enabled, exponent notations, decimal fractions, and range checks.
 *
 * @throws com.ghost.serialization.exception.GhostJsonException
 * if float format is invalid or overflows.
 */
fun GhostJsonFlatReader.nextFloat(): Float {
    val header = prepareNumericHeader()
    val isQuoted = (header and C.NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and C.NUMERIC_HEADER_NEGATIVE) != 0

    validateLeadingZero()

    var mantissa = 0L
    var exponent = 0
    var digitCount = 0

    // Integer part
    nextTokenByte = -1
    val data = rawData
    val localLimit = limit
    while (position < localLimit) {
        val byte = data[position].toInt() and C.BYTE_MASK
        if (isDigit(byte)) {
            val digit = byte - C.ZERO_INT
            if (digitCount < C.FLOAT_PRECISION_LIMIT) {
                mantissa = mantissa * C.BASE_TEN + digit
                digitCount++
            } else {
                exponent++
            }
            position++
        } else {
            break
        }
    }

    if (digitCount == 0) {
        throwError(C.ERR_EXPECTED_INT_PART)
    }

    // Decimal part
    if (position < localLimit && getByte(position) == C.DOT_INT) {
        val newPos = position + 1
        position = newPos
        while (position < localLimit) {
            val byte = data[position].toInt() and C.BYTE_MASK
            if (isDigit(byte)) {
                val digit = byte - C.ZERO_INT
                if (digitCount < C.FLOAT_PRECISION_LIMIT) {
                    mantissa = mantissa * C.BASE_TEN + digit
                    digitCount++
                    exponent--
                }
                position++
            } else {
                break
            }
        }
        if (position == newPos) {
            throwError(C.ERR_EXPECTED_DECIMAL_DIGITS)
        }
    }

    // Exponent part
    if (position < localLimit && isExponentMarker(getByte(position))) {
        exponent += parseExponentValue()
    }

    var result = mantissa.toFloat()
    if (exponent != 0) {
        result *= getFloatPowerOfTen(exponent)
    }

    if (isNegativeValue) {
        result = -result
    }
    validateNumericRangeFloat(result)

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
fun GhostJsonFlatReader.nextDouble(): Double {
    val header = prepareNumericHeader()
    val isQuoted = (header and C.NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and C.NUMERIC_HEADER_NEGATIVE) != 0

    validateLeadingZero()

    var mantissa = 0L
    var exponent = 0
    var digitCount = 0

    // Integer part
    nextTokenByte = -1
    val data = rawData
    val localLimit = limit
    while (position < localLimit) {
        val byte = data[position].toInt() and C.BYTE_MASK
        if (isDigit(byte)) {
            val digit = byte - C.ZERO_INT
            if (digitCount < C.DOUBLE_PRECISION_LIMIT) {
                mantissa = mantissa * C.BASE_TEN + digit
                digitCount++
            } else {
                exponent++
            }
            position++
        } else {
            break
        }
    }

    if (digitCount == 0) {
        throwError(C.ERR_EXPECTED_INT_PART)
    }

    // Decimal part
    if (position < localLimit && getByte(position) == C.DOT_INT) {
        val newPos = position + 1
        position = newPos
        while (position < localLimit) {
            val byte = data[position].toInt() and C.BYTE_MASK
            if (isDigit(byte)) {
                val digitValue = byte - C.ZERO_INT
                if (digitCount < C.DOUBLE_PRECISION_LIMIT) {
                    mantissa = mantissa * C.BASE_TEN + digitValue
                    digitCount++
                    exponent--
                }
                position++
            } else {
                break
            }
        }
        if (position == newPos) {
            throwError(C.ERR_EXPECTED_DECIMAL_DIGITS)
        }
    }

    // Exponent part
    if (position < localLimit && isExponentMarker(getByte(position))) {
        exponent += parseExponentValue()
    }

    var result = mantissa.toDouble()
    if (exponent != 0) {
        result *= getDoublePowerOfTen(exponent)
    }

    if (isNegativeValue) {
        result = -result
    }
    validateNumericRange(result)

    if (isQuoted) {
        consumeNumericCoercionFooter()
    }

    return result
}

/**
 * Helper to parse the exponent suffix value (e.g. e-5 or e+12) from a number.
 */
private inline fun GhostJsonFlatReader.parseExponentValue(): Int {
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
        } else {
            break
        }
    }

    if (!hasExpDigits) {
        throwError(C.ERR_EXPECTED_EXPONENT_DIGITS)
    }
    return if (isExpNegative) -expValue else expValue
}

/**
 * Parses and returns the next [Int] value from the JSON stream.
 *
 * Supports coercion from strings if enabled, validates format, leading zeros, and range overflow.
 *
 * @throws com.ghost.serialization.exception.GhostJsonException if integer is invalid or overflows.
 */
fun GhostJsonFlatReader.nextInt(): Int {
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
fun GhostJsonFlatReader.nextLong(): Long {
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
private fun GhostJsonFlatReader.prepareNumericHeader(): Int {
    if (nextTokenByte == C.RESET_TOKEN_BYTE) {
        skipWhitespace()
    }
    if (position >= limit) {
        throwError(C.ERR_EXPECTED_NUMBER)
    }

    var header = 0
    var token = nextTokenByte

    if (token == C.QUOTE_INT) {
        if (!coerceStringsToNumbers) {
            throwError(C.ERR_COERCION_DISABLED)
        }
        position++
        nextTokenByte = C.RESET_TOKEN_BYTE
        skipWhitespace()
        if (position >= limit) {
            throwError(C.ERR_EXPECTED_NUMBER)
        }
        token = nextTokenByte
        header = header or C.NUMERIC_HEADER_QUOTED
    }

    if (token == C.MINUS_INT) {
        if (position + 1 >= limit) {
            throwError(C.ERR_ISOLATED_MINUS)
        }
        position++
        nextTokenByte = C.RESET_TOKEN_BYTE
        header = header or C.NUMERIC_HEADER_NEGATIVE
    }

    return header
}

/**
 * Handles validation and skipping of a single leading zero.
 */
private fun GhostJsonFlatReader.handleLeadingZero() {
    val nextCursor = position + 1
    if (nextCursor < limit) {
        val nextDigitByte = getByte(nextCursor)
        if ((C.DIGIT_BITMASK shr nextDigitByte) and C.BYTE_SHIFT_UNIT != C.RESULT_NONE) {
            throwError(C.ERR_LEADING_ZEROS)
        }
    }
    internalSkip(1)
}

/**
 * Parses integer digits bitwise with overflow checks.
 */
private fun GhostJsonFlatReader.parseIntDigits(isNegative: Boolean, startOfNumber: Int): Int {
    var accumulatedValue = 0
    var digitCount = 0
    var hasDigitsFound = false
    nextTokenByte = -1
    var earlyExitResult: Int? = null

    val data = rawData
    val localLimit = limit
    while (position < localLimit) {
        val byte = data[position].toInt() and C.BYTE_MASK
        if (isDigit(byte)) {
            val digit = byte - C.ZERO_INT
            accumulatedValue = if (digitCount < C.INT_SAFE_DIGITS) {
                accumulatedValue * C.BASE_TEN + digit
            } else {
                calculateIntWithOverflowCheck(accumulatedValue, digit, isNegative)
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

/**
 * Parses long digits bitwise with overflow checks.
 */
private fun GhostJsonFlatReader.parseLongDigits(isNegative: Boolean, startOfNumber: Int): Long {
    var accumulatedValue = 0L
    var digitCount = 0
    var hasDigitsFound = false
    nextTokenByte = -1
    var earlyExitResult: Long? = null

    val data = rawData
    val localLimit = limit
    while (position < localLimit) {
        val byte = data[position].toInt() and C.BYTE_MASK
        if (isDigit(byte)) {
            val digit = byte - C.ZERO_INT
            accumulatedValue = if (digitCount < C.LONG_SAFE_DIGITS) {
                accumulatedValue * C.BASE_TEN + digit
            } else {
                calculateLongWithOverflowCheck(accumulatedValue, digit, isNegative)
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

/**
 * Consumes the trailing quotation mark when parsing coerced numeric string values.
 */
private inline fun GhostJsonFlatReader.consumeNumericCoercionFooter() {
    if (position >= limit || getByte(position) != C.QUOTE_INT) {
        throwError(C.ERR_EXPECTED_COERCION_QUOTE)
    }
    internalSkip(1)
    skipWhitespace()
}

/**
 * Accumulates int value and throws if it overflows the safe JVM Int limit.
 */
private inline fun GhostJsonFlatReader.calculateIntWithOverflowCheck(
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

/**
 * Accumulates long value and throws if it overflows the safe JVM Long limit.
 */
private inline fun GhostJsonFlatReader.calculateLongWithOverflowCheck(
    current: Long,
    digitValue: Int,
    isNegative: Boolean
): Long {
    if (current > C.LONG_OVERFLOW_LIMIT ||
        (current == C.LONG_OVERFLOW_LIMIT && digitValue > C.LONG_MAX_LAST_DIGIT)
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

/**
 * Validates leading zero presence for numbers.
 */
private fun GhostJsonFlatReader.validateLeadingZero() {
    if (position < limit && getByte(position) == C.ZERO_INT &&
        position + 1 < limit
    ) {
        val nextDigitByte = getByte(position + 1)
        if ((C.DIGIT_BITMASK shr nextDigitByte) and 1L != 0L) {
            throwError(C.ERR_LEADING_ZEROS)
        }
    }
}

/**
 * Validates that float values parsed are finite numbers.
 */
private fun GhostJsonFlatReader.validateNumericRangeFloat(valueToValidate: Float) {
    if (valueToValidate.isInfinite() || valueToValidate.isNaN()) {
        throwError(C.ERR_NUMERIC_OVERFLOW)
    }
}


/**
 * Validates that double values parsed are finite numbers.
 */
private fun GhostJsonFlatReader.validateNumericRange(valueToValidate: Double) {
    if (valueToValidate.isInfinite() || valueToValidate.isNaN()) {
        throwError(C.ERR_NUMERIC_OVERFLOW)
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

    var hasDigits = false

    // Integer part
    val numberPosition = position
    val numberLimit = limit
    val data = rawData
    if (numberPosition < numberLimit && getByte(numberPosition) == C.ZERO_INT) {
        val newPos = numberPosition + 1
        position = newPos
        hasDigits = true
        if (newPos < numberLimit && isDigit(getByte(newPos))) {
            throwError(C.ERR_LEADING_ZEROS)
        }
    } else {
        while (position < numberLimit) {
            val byte = data[position].toInt() and C.BYTE_MASK
            if (isDigit(byte)) {
                hasDigits = true
                position++
            } else {
                break
            }
        }
    }

    if (!hasDigits) {
        throwError(C.ERR_EXPECTED_INT_PART)
    }

    // Decimal part
    if (position < numberLimit && getByte(position) == C.DOT_INT) {
        position++
        var hasDecimalDigits = false
        while (position < numberLimit) {
            val byte = data[position].toInt() and C.BYTE_MASK
            if (isDigit(byte)) {
                hasDecimalDigits = true
                position++
            } else {
                break
            }
        }
        if (!hasDecimalDigits) {
            throwError(C.ERR_EXPECTED_DECIMAL_DIGITS)
        }
    }

    // Exponent part
    if (position < numberLimit) {
        val byte = getByte(position)
        if (byte == C.EXP_LOWER_INT || byte == C.EXP_UPPER_INT) {
            var newPos = position + 1
            position = newPos
            if (newPos < numberLimit) {
                val sign = getByte(newPos)
                if (sign == C.PLUS_INT || sign == C.MINUS_INT) {
                    newPos++
                    position = newPos
                }
            }

            var hasExpDigits = false
            while (position < numberLimit) {
                val byteCode = data[position].toInt() and C.BYTE_MASK
                if (isDigit(byteCode)) {
                    hasExpDigits = true
                    position++
                } else {
                    break
                }
            }
            if (!hasExpDigits) {
                throwError(C.ERR_EXPECTED_EXPONENT_DIGITS)
            }
        }
    }

    if (isQuoted) {
        consumeNumericCoercionFooter()
    }
    nextTokenByte = -1
}

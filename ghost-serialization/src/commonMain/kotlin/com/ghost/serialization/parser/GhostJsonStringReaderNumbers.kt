@file:Suppress("NOTHING_TO_INLINE")
@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser

import com.ghost.serialization.parser.GhostJsonConstants as C
import com.ghost.serialization.InternalGhostApi

fun GhostJsonStringReader.nextInt(): Int {
    val header = prepareNumericHeader()
    val isQuoted = (header and C.NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and C.NUMERIC_HEADER_NEGATIVE) != 0

    val startOfNumber = position
    validateLeadingZero()

    val accumulatedValue = parseIntDigits(isNegativeValue, startOfNumber)

    if (isQuoted) {
        consumeNumericCoercionFooter()
    }
    nextTokenByte = C.RESET_TOKEN_BYTE

    return if (isNegativeValue) -accumulatedValue else accumulatedValue
}

fun GhostJsonStringReader.nextLong(): Long {
    val header = prepareNumericHeader()
    val isQuoted = (header and C.NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and C.NUMERIC_HEADER_NEGATIVE) != 0

    val startOfNumber = position
    validateLeadingZero()

    val accumulatedValue = parseLongDigits(isNegativeValue, startOfNumber)

    if (isQuoted) {
        consumeNumericCoercionFooter()
    }
    nextTokenByte = C.RESET_TOKEN_BYTE

    return if (isNegativeValue) -accumulatedValue else accumulatedValue
}

fun GhostJsonStringReader.nextFloat(): Float {
    val header = prepareNumericHeader()
    val isQuoted = (header and C.NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and C.NUMERIC_HEADER_NEGATIVE) != 0

    validateLeadingZero()

    var mantissa = 0L
    var exponent = 0
    var digitCount = 0

    nextTokenByte = -1
    val localLimit = limit
    val chars = rawData
    while (position < localLimit) {
        val byte = chars[position].code
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

    if (position < localLimit && getByte(position) == C.DOT_INT) {
        val newPos = position + 1
        position = newPos
        while (position < localLimit) {
            val byte = chars[position].code
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
    if (result.isInfinite() || result.isNaN()) {
        throwError(C.ERR_NUMERIC_OVERFLOW)
    }

    if (isQuoted) {
        consumeNumericCoercionFooter()
    }

    return result
}

fun GhostJsonStringReader.nextDouble(): Double {
    val header = prepareNumericHeader()
    val isQuoted = (header and C.NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and C.NUMERIC_HEADER_NEGATIVE) != 0

    validateLeadingZero()

    var mantissa = 0L
    var exponent = 0
    var digitCount = 0

    nextTokenByte = -1
    val localLimit = limit
    val chars = rawData
    while (position < localLimit) {
        val byte = chars[position].code
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

    if (position < localLimit && getByte(position) == C.DOT_INT) {
        val newPos = position + 1
        position = newPos
        while (position < localLimit) {
            val byte = chars[position].code
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
    if (result.isInfinite() || result.isNaN()) {
        throwError(C.ERR_NUMERIC_OVERFLOW)
    }

    if (isQuoted) {
        consumeNumericCoercionFooter()
    }

    return result
}

private fun GhostJsonStringReader.parseExponentValue(): Int {
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
            if (expValue < C.EXPONENT_CLAMP_THRESHOLD) {
                expValue = expValue * C.BASE_TEN + (currentByteInt - C.ZERO_INT)
            }
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

private fun GhostJsonStringReader.prepareNumericHeader(): Int {
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

private fun GhostJsonStringReader.handleLeadingZero() {
    val nextCursor = position + 1
    if (nextCursor < limit) {
        val nextDigitByte = getByte(nextCursor)
        if (nextDigitByte in C.ZERO_INT..C.NINE_INT) {
            throwError(C.ERR_LEADING_ZEROS)
        }
    }
    internalSkip(1)
}

private fun GhostJsonStringReader.parseIntDigits(isNegative: Boolean, startOfNumber: Int): Int {
    var accumulatedValue = 0
    var digitCount = 0
    var hasDigitsFound = false
    nextTokenByte = -1
    var earlyExitResult: Int? = null

    val localLimit = limit
    val chars = rawData
    while (position < localLimit) {
        val byte = chars[position].code
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

private fun GhostJsonStringReader.parseLongDigits(isNegative: Boolean, startOfNumber: Int): Long {
    var accumulatedValue = 0L
    var digitCount = 0
    var hasDigitsFound = false
    nextTokenByte = -1
    var earlyExitResult: Long? = null

    val localLimit = limit
    val chars = rawData
    while (position < localLimit) {
        val byte = chars[position].code
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

private inline fun GhostJsonStringReader.consumeNumericCoercionFooter() {
    if (position >= limit || getByte(position) != C.QUOTE_INT) {
        throwError(C.ERR_EXPECTED_COERCION_QUOTE)
    }
    internalSkip(1)
    skipWhitespace()
}

private inline fun GhostJsonStringReader.calculateIntWithOverflowCheck(
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

private inline fun GhostJsonStringReader.calculateLongWithOverflowCheck(
    current: Long,
    digitValue: Int,
    isNegative: Boolean
): Long {
    if (current == Long.MIN_VALUE ||
        current > C.LONG_OVERFLOW_LIMIT ||
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

private fun GhostJsonStringReader.validateLeadingZero() {
    if (position < limit && getByte(position) == C.ZERO_INT &&
        position + 1 < limit
    ) {
        val nextDigitByte = getByte(position + 1)
        if (nextDigitByte in C.ZERO_INT..C.NINE_INT) {
            throwError(C.ERR_LEADING_ZEROS)
        }
    }
}

fun GhostJsonStringReader.skipNumber() {
    val header = prepareNumericHeader()
    val isQuoted = (header and C.NUMERIC_HEADER_QUOTED) != 0

    var hasDigits = false

    val numberPosition = position
    val numberLimit = limit
    val chars = rawData
    if (numberPosition < numberLimit && chars[numberPosition].code == C.ZERO_INT) {
        val newPos = numberPosition + 1
        position = newPos
        hasDigits = true
        if (newPos < numberLimit && isDigit(chars[newPos].code)) {
            throwError(C.ERR_LEADING_ZEROS)
        }
    } else {
        while (position < numberLimit) {
            val byte = chars[position].code
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

    if (position < numberLimit && chars[position].code == C.DOT_INT) {
        position++
        var hasDecimalDigits = false
        while (position < numberLimit) {
            val byte = chars[position].code
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

    if (position < numberLimit) {
        val byte = chars[position].code
        if (byte == C.EXP_LOWER_INT || byte == C.EXP_UPPER_INT) {
            var newPos = position + 1
            position = newPos
            if (newPos < numberLimit) {
                val sign = chars[newPos].code
                if (sign == C.PLUS_INT || sign == C.MINUS_INT) {
                    newPos++
                    position = newPos
                }
            }

            var hasExpDigits = false
            while (position < numberLimit) {
                val byteCode = chars[position].code
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

@file:OptIn(InternalGhostApi::class)
@file:Suppress("UNNECESSARY_NOT_NULL_ASSERTION", "NOTHING_TO_INLINE")

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.GhostJsonConstants as C

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

    var mantissa = 0L
    var exponent = 0
    var digitCount = 0

    // Integer part
    nextTokenByte = -1
    readNumericLoop { byte ->
        val digit = byte - C.ZERO_INT
        if (digitCount < C.FLOAT_PRECISION_LIMIT) {
            mantissa = mantissa * C.BASE_TEN + digit
            digitCount++
        } else {
            exponent++
        }
    }

    if (digitCount == 0) {
        throwError(C.ERR_EXPECTED_INT_PART)
    }

    // Decimal part
    val pos = position
    val lim = limit
    if (pos < lim && getByte(pos) == C.DOT_INT) {
        val newPos = pos + 1
        position = newPos
        val startPos = newPos
        readNumericLoop { byte ->
            val digit = byte - C.ZERO_INT
            if (digitCount < C.FLOAT_PRECISION_LIMIT) {
                mantissa = mantissa * C.BASE_TEN + digit
                digitCount++
                exponent--
            }
        }
        if (position == startPos) {
            throwError(C.ERR_EXPECTED_DECIMAL_DIGITS)
        }
    }

    // Exponent part
    val currentPos = position
    val currentLimit = limit
    if (currentPos < currentLimit && isExponentMarker(getByte(currentPos))) {
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

    var mantissa = 0L
    var exponent = 0
    var digitCount = 0

    // Integer part
    nextTokenByte = -1
    readNumericLoop { byte ->
        val digit = byte - C.ZERO_INT
        if (digitCount < C.DOUBLE_PRECISION_LIMIT) {
            mantissa = mantissa * C.BASE_TEN + digit
            digitCount++
        } else {
            exponent++
        }
    }

    if (digitCount == 0) {
        throwError(C.ERR_EXPECTED_INT_PART)
    }

    // Decimal part
    val pos = position
    val lim = limit
    if (pos < lim && getByte(pos) == C.DOT_INT) {
        val newPos = pos + 1
        position = newPos
        readNumericLoop { byte ->
            val digitValue = byte - C.ZERO_INT
            if (digitCount < C.DOUBLE_PRECISION_LIMIT) {
                mantissa = mantissa * C.BASE_TEN + digitValue
                digitCount++
                exponent--
            }
        }
        if (position == newPos) {
            throwError(C.ERR_EXPECTED_DECIMAL_DIGITS)
        }
    }

    // Exponent part
    val currentPos = position
    val currentLimit = limit
    if (currentPos < currentLimit && isExponentMarker(getByte(currentPos))) {
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
 * Parses the exponent value suffix (e.g. e-5) from the stream.
 */
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

/**
 * Prepares the numeric header by checking the negative sign and checking string coercion quote.
 */
private fun GhostJsonReader.prepareNumericHeader(): Int {
    if (nextTokenByte == -1) {
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
        nextTokenByte = -1
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
        nextTokenByte = -1
        header = header or C.NUMERIC_HEADER_NEGATIVE
    }

    return header
}

/**
 * Validates and consumes a leading zero.
 */
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

/**
 * Bitwise parsing loop for integer digits with overflow verification.
 */
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
        { byte ->
            val digit = byte - C.ZERO_INT
            accumulatedValue = if (digitCount < C.INT_SAFE_DIGITS) {
                accumulatedValue * C.BASE_TEN + digit
            } else {
                calculateIntWithOverflowCheck(
                    accumulatedValue,
                    digit,
                    isNegative
                )
            }
            digitCount++
            hasDigitsFound = true
        },
        { byte ->
            if (isNumericSeparator(byte)) {
                position = startOfNumber
                earlyExitResult = nextDouble().toInt()
            }
        }
    )

    if (earlyExitResult != null) {
        return earlyExitResult!!
    }
    if (!hasDigitsFound) {
        throwError(C.ERR_EXPECTED_INT_PART)
    }
    return accumulatedValue
}

/**
 * Bitwise parsing loop for long digits with overflow verification.
 */
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
        { byte ->
            val digit = byte - C.ZERO_INT
            accumulatedValue = if (digitCount < C.LONG_SAFE_DIGITS) {
                accumulatedValue * C.BASE_TEN + digit
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
        { byte ->
            if (isNumericSeparator(byte)) {
                position = startOfNumber
                earlyExitResult = nextDouble().toLong()
            }
        }
    )

    if (earlyExitResult != null) {
        return earlyExitResult!!
    }
    if (!hasDigitsFound) {
        throwError(C.ERR_EXPECTED_INT_PART)
    }
    return accumulatedValue
}

/**
 * Consumes the closing quotation mark for coerced numeric string values.
 */
private inline fun GhostJsonReader.consumeNumericCoercionFooter() {
    if (position >= limit || getByte(position) != C.QUOTE_INT) {
        throwError(C.ERR_EXPECTED_COERCION_QUOTE)
    }
    internalSkip(1)
    skipWhitespace()
}

/**
 * Accumulates and checks Int bounds to throw
 * [com.ghost.serialization.exception.GhostJsonException] on overflow.
 */
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

/**
 * Accumulates and checks Long bounds to throw
 * [com.ghost.serialization.exception.GhostJsonException] on overflow.
 */
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

/**
 * Asserts that leading zero doesn't precede another digit.
 */
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

/**
 * Verifies that parsed Float values are finite numbers.
 */
private fun GhostJsonReader.validateNumericRangeFloat(valueToValidate: Float) {
    if (valueToValidate.isInfinite() || valueToValidate.isNaN()) {
        throwError(C.ERR_NUMERIC_OVERFLOW)
    }
}


/**
 * Verifies that parsed Double values are finite numbers.
 */
private fun GhostJsonReader.validateNumericRange(valueToValidate: Double) {
    if (valueToValidate.isInfinite() || valueToValidate.isNaN()) {
        throwError(C.ERR_NUMERIC_OVERFLOW)
    }
}

/**
 * Skips a JSON numeric token value from the source.
 */
@InternalGhostApi
fun GhostJsonReader.skipNumber() {
    val header = prepareNumericHeader()
    val isQuoted = (header and C.NUMERIC_HEADER_QUOTED) != 0

    var hasDigits = false

    // Integer part
    val pos = position
    val lim = limit
    if (pos < lim && getByte(pos) == C.ZERO_INT) {
        val newPos = pos + 1
        position = newPos
        hasDigits = true
        if (
            newPos < lim &&
            isDigit(getByte(newPos))
        ) {
            throwError(C.ERR_LEADING_ZEROS)
        }
    } else {
        readNumericLoop { hasDigits = true }
    }

    if (!hasDigits) {
        throwError(C.ERR_EXPECTED_INT_PART)
    }

    // Decimal part
    val decPos = position
    val decLim = limit
    if (
        decPos < decLim &&
        getByte(decPos) == C.DOT_INT
    ) {
        position = decPos + 1
        var hasDecimalDigits = false
        readNumericLoop { hasDecimalDigits = true }
        if (!hasDecimalDigits) {
            throwError(C.ERR_EXPECTED_DECIMAL_DIGITS)
        }
    }

    // Exponent part
    val expPos = position
    val expLim = limit
    if (expPos < expLim) {
        val byte = getByte(expPos)
        if (byte == C.EXP_LOWER_INT || byte == C.EXP_UPPER_INT) {
            var newPos = expPos + 1
            position = newPos
            if (newPos < expLim) {
                val sign = getByte(newPos)
                if (sign == C.PLUS_INT || sign == C.MINUS_INT) {
                    newPos++
                    position = newPos
                }
            }

            var hasExpDigits = false
            readNumericLoop { hasExpDigits = true }
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

/**
 * Loops and consumes digits from the stream.
 */
private inline fun GhostJsonReader.readNumericLoop(
    crossinline onDigit: (byte: Int) -> Unit
) {
    readNumericLoop(onDigit, {})
}

/**
 * Loops and consumes digits from the stream, with optional break hook.
 */
private inline fun GhostJsonReader.readNumericLoop(
    crossinline onDigit: (byte: Int) -> Unit,
    crossinline onBreak: (byte: Int) -> Unit
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

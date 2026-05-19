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
    readNumericLoop(
        onDigit = { byte ->
            val digit = byte - C.ZERO_INT
            if (digitCount < C.FLOAT_PRECISION_LIMIT) {
                mantissa = mantissa * C.BASE_TEN + digit
                digitCount++
            } else {
                exponent++
            }
        }
    )

    if (digitCount == 0) throwError(C.ERR_EXPECTED_INT_PART)

    // Decimal part
    if (position < limit && getByte(position) == C.DOT_INT) {
        position++ // nextTokenByte already -1
        val startPos = position
        readNumericLoop(
            onDigit = { byte ->
                val digit = byte - C.ZERO_INT
                if (digitCount < C.FLOAT_PRECISION_LIMIT) {
                    mantissa = mantissa * C.BASE_TEN + digit
                    digitCount++
                    exponent--
                }
            }
        )
        if (position == startPos) throwError(C.ERR_EXPECTED_DECIMAL_DIGITS)
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

/**
 * Reads a JSON number and returns it as a Double.
 * Uses a zero-allocation, register-based loop for maximum speed.
 * Used by KSP-generated serializers.
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
    readNumericLoop(
        onDigit = { byte ->
            val digit = byte - C.ZERO_INT
            if (digitCount < C.DOUBLE_PRECISION_LIMIT) {
                mantissa = mantissa * C.BASE_TEN + digit
                digitCount++
            } else {
                exponent++
            }
        }
    )

    if (digitCount == 0) throwError(C.ERR_EXPECTED_INT_PART)

    // Decimal part
    if (position < limit && getByte(position) == C.DOT_INT) {
        position++ // nextTokenByte already -1
        val startPos = position
        readNumericLoop(
            onDigit = { byte ->
                val digitValue = byte - C.ZERO_INT
                if (digitCount < C.DOUBLE_PRECISION_LIMIT) {
                    mantissa = mantissa * C.BASE_TEN + digitValue
                    digitCount++
                    exponent--
                }
            }
        )
        if (position == startPos) throwError(C.ERR_EXPECTED_DECIMAL_DIGITS)
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
fun GhostJsonReader.nextInt(): Int {
    val header = prepareNumericHeader()
    val isQuoted = (header and C.NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and C.NUMERIC_HEADER_NEGATIVE) != 0
    
    val startOfNumber = position

    val absoluteValue = if (
        position < limit &&
        getByte(position) == C.ZERO_INT
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

    if (isQuoted) consumeNumericCoercionFooter()
    return finalIntResult
}

/**
 * Reads a JSON long and returns it as a Long.
 * Optimized for common small longs.
 * Used by KSP-generated serializers.
 */
fun GhostJsonReader.nextLong(): Long {
    val header = prepareNumericHeader()
    val isQuoted = (header and C.NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and C.NUMERIC_HEADER_NEGATIVE) != 0
    
    val startOfNumber = position

    val absoluteValue = if (
        position < limit &&
        getByte(position) == C.ZERO_INT
    ) {
        handleLeadingZero()
        C.RESULT_NONE
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

@Suppress("AssignedValueIsNeverRead")
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
        onBreak = { byte ->
            if (isNumericSeparator(byte)) {
                position = startOfNumber
                earlyExitResult = nextDouble().toInt()
            }
        }
    )

    if (earlyExitResult != null) return earlyExitResult!!
    if (!hasDigitsFound) throwError(C.ERR_EXPECTED_INT_PART)
    return accumulatedValue
}

@Suppress("AssignedValueIsNeverRead", "UNNECESSARY_NOT_NULL_ASSERTION")
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
        onBreak = { byte ->
            if (isNumericSeparator(byte)) {
                position = startOfNumber
                earlyExitResult = nextDouble().toLong()
            }
        }
    )

    if (earlyExitResult != null) return earlyExitResult!!
    if (!hasDigitsFound) throwError(C.ERR_EXPECTED_INT_PART)
    return accumulatedValue
}

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
fun GhostJsonReader.skipNumber() {
    val header = prepareNumericHeader()
    val isQuoted = (header and C.NUMERIC_HEADER_QUOTED) != 0

    var hasDigits = false

    // Integer part
    if (position < limit && getByte(position) == C.ZERO_INT) {
        position++
        hasDigits = true
        if (
            position < limit &&
            isDigit(getByte(position))
        ) {
            throwError(C.ERR_LEADING_ZEROS)
        }
    } else {
        readNumericLoop(onDigit = { hasDigits = true })
    }

    if (!hasDigits) {
        throwError(C.ERR_EXPECTED_INT_PART)
    }

    // Decimal part
    if (
        position < limit &&
        getByte(position) == C.DOT_INT
    ) {
        position++
        var hasDecimalDigits = false
        readNumericLoop(onDigit = { hasDecimalDigits = true })
        if (!hasDecimalDigits) throwError(C.ERR_EXPECTED_DECIMAL_DIGITS)
    }

    // Exponent part
    if (position < limit) {
        val byte = getByte(position)
        if (byte == C.EXP_LOWER_INT || byte == C.EXP_UPPER_INT) {
            position++
            if (position < limit) {
                val sign = getByte(position)
                if (sign == C.PLUS_INT || sign == C.MINUS_INT) {
                    position++
                }
            }

            var hasExpDigits = false
            readNumericLoop(onDigit = { hasExpDigits = true })
            if (!hasExpDigits) throwError(C.ERR_EXPECTED_EXPONENT_DIGITS)
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

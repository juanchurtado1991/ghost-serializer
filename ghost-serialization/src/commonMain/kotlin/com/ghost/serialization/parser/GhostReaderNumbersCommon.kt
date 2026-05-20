package com.ghost.serialization.parser

import com.ghost.serialization.parser.GhostJsonConstants as C

internal inline fun nextFloatImpl(
    crossinline prepareNumericHeader: () -> Int,
    crossinline validateLeadingZero: () -> Unit,
    crossinline readNumericLoop: (onDigit: (Int) -> Unit) -> Unit,
    crossinline throwError: (String) -> Nothing,
    crossinline getPosition: () -> Int,
    crossinline setPosition: (Int) -> Unit,
    crossinline getLimit: () -> Int,
    crossinline getByte: (Int) -> Int,
    crossinline isExponentMarker: (Int) -> Boolean,
    crossinline parseExponentValue: () -> Int,
    crossinline getFloatPowerOfTen: (Int) -> Float,
    crossinline validateNumericRangeFloat: (Float) -> Unit,
    crossinline consumeNumericCoercionFooter: () -> Unit,
    crossinline setNextTokenByte: (Int) -> Unit
): Float {
    val header = prepareNumericHeader()
    val isQuoted = (header and C.NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and C.NUMERIC_HEADER_NEGATIVE) != 0

    validateLeadingZero()

    var mantissa = 0L
    var exponent = 0
    var digitCount = 0

    // Integer part
    setNextTokenByte(-1)
    readNumericLoop { byte ->
        val digit = byte - C.ZERO_INT
        if (digitCount < C.FLOAT_PRECISION_LIMIT) {
            mantissa = mantissa * C.BASE_TEN + digit
            digitCount++
        } else {
            exponent++
        }
    }

    if (digitCount == 0) throwError(C.ERR_EXPECTED_INT_PART)

    // Decimal part
    val pos = getPosition()
    val lim = getLimit()
    if (pos < lim && getByte(pos) == C.DOT_INT) {
        val newPos = pos + 1
        setPosition(newPos)
        val startPos = newPos
        readNumericLoop { byte ->
            val digit = byte - C.ZERO_INT
            if (digitCount < C.FLOAT_PRECISION_LIMIT) {
                mantissa = mantissa * C.BASE_TEN + digit
                digitCount++
                exponent--
            }
        }
        if (getPosition() == startPos) throwError(C.ERR_EXPECTED_DECIMAL_DIGITS)
    }

    // Exponent part
    val currentPos = getPosition()
    val currentLimit = getLimit()
    if (currentPos < currentLimit && isExponentMarker(getByte(currentPos))) {
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

internal inline fun nextDoubleImpl(
    crossinline prepareNumericHeader: () -> Int,
    crossinline validateLeadingZero: () -> Unit,
    crossinline readNumericLoop: (onDigit: (Int) -> Unit) -> Unit,
    crossinline throwError: (String) -> Nothing,
    crossinline getPosition: () -> Int,
    crossinline setPosition: (Int) -> Unit,
    crossinline getLimit: () -> Int,
    crossinline getByte: (Int) -> Int,
    crossinline isExponentMarker: (Int) -> Boolean,
    crossinline parseExponentValue: () -> Int,
    crossinline getDoublePowerOfTen: (Int) -> Double,
    crossinline validateNumericRange: (Double) -> Unit,
    crossinline consumeNumericCoercionFooter: () -> Unit,
    crossinline setNextTokenByte: (Int) -> Unit
): Double {
    val header = prepareNumericHeader()
    val isQuoted = (header and C.NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and C.NUMERIC_HEADER_NEGATIVE) != 0

    validateLeadingZero()

    var mantissa = 0L
    var exponent = 0
    var digitCount = 0

    // Integer part
    setNextTokenByte(-1)
    readNumericLoop { byte ->
        val digit = byte - C.ZERO_INT
        if (digitCount < C.DOUBLE_PRECISION_LIMIT) {
            mantissa = mantissa * C.BASE_TEN + digit
            digitCount++
        } else {
            exponent++
        }
    }

    if (digitCount == 0) throwError(C.ERR_EXPECTED_INT_PART)

    // Decimal part
    val pos = getPosition()
    val lim = getLimit()
    if (pos < lim && getByte(pos) == C.DOT_INT) {
        val newPos = pos + 1
        setPosition(newPos)
        val startPos = newPos
        readNumericLoop { byte ->
            val digitValue = byte - C.ZERO_INT
            if (digitCount < C.DOUBLE_PRECISION_LIMIT) {
                mantissa = mantissa * C.BASE_TEN + digitValue
                digitCount++
                exponent--
            }
        }
        if (getPosition() == startPos) throwError(C.ERR_EXPECTED_DECIMAL_DIGITS)
    }

    // Exponent part
    val currentPos = getPosition()
    val currentLimit = getLimit()
    if (currentPos < currentLimit && isExponentMarker(getByte(currentPos))) {
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

internal inline fun nextIntImpl(
    crossinline prepareNumericHeader: () -> Int,
    crossinline getPosition: () -> Int,
    crossinline getLimit: () -> Int,
    crossinline getByte: (Int) -> Int,
    crossinline handleLeadingZero: () -> Unit,
    crossinline parseIntDigits: (Boolean, Int) -> Int,
    crossinline consumeNumericCoercionFooter: () -> Unit
): Int {
    val header = prepareNumericHeader()
    val isQuoted = (header and C.NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and C.NUMERIC_HEADER_NEGATIVE) != 0
    
    val startOfNumber = getPosition()

    val absoluteValue = if (
        startOfNumber < getLimit() &&
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

    if (isQuoted) consumeNumericCoercionFooter()
    return finalIntResult
}

internal inline fun nextLongImpl(
    crossinline prepareNumericHeader: () -> Int,
    crossinline getPosition: () -> Int,
    crossinline getLimit: () -> Int,
    crossinline getByte: (Int) -> Int,
    crossinline handleLeadingZero: () -> Unit,
    crossinline parseLongDigits: (Boolean, Int) -> Long,
    crossinline consumeNumericCoercionFooter: () -> Unit
): Long {
    val header = prepareNumericHeader()
    val isQuoted = (header and C.NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and C.NUMERIC_HEADER_NEGATIVE) != 0
    
    val startOfNumber = getPosition()

    val absoluteValue = if (
        startOfNumber < getLimit() &&
        getByte(startOfNumber) == C.ZERO_INT
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

internal inline fun skipNumberImpl(
    crossinline prepareNumericHeader: () -> Int,
    crossinline getPosition: () -> Int,
    crossinline setPosition: (Int) -> Unit,
    crossinline getLimit: () -> Int,
    crossinline getByte: (Int) -> Int,
    crossinline isDigit: (Int) -> Boolean,
    crossinline readNumericLoop: (onDigit: (Int) -> Unit) -> Unit,
    crossinline throwError: (String) -> Nothing,
    crossinline consumeNumericCoercionFooter: () -> Unit,
    crossinline setNextTokenByte: (Int) -> Unit
) {
    val header = prepareNumericHeader()
    val isQuoted = (header and C.NUMERIC_HEADER_QUOTED) != 0

    var hasDigits = false

    // Integer part
    val pos = getPosition()
    val lim = getLimit()
    if (pos < lim && getByte(pos) == C.ZERO_INT) {
        val newPos = pos + 1
        setPosition(newPos)
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
    val decPos = getPosition()
    val decLim = getLimit()
    if (
        decPos < decLim &&
        getByte(decPos) == C.DOT_INT
    ) {
        setPosition(decPos + 1)
        var hasDecimalDigits = false
        readNumericLoop { hasDecimalDigits = true }
        if (!hasDecimalDigits) throwError(C.ERR_EXPECTED_DECIMAL_DIGITS)
    }

    // Exponent part
    val expPos = getPosition()
    val expLim = getLimit()
    if (expPos < expLim) {
        val byte = getByte(expPos)
        if (byte == C.EXP_LOWER_INT || byte == C.EXP_UPPER_INT) {
            var newPos = expPos + 1
            setPosition(newPos)
            if (newPos < expLim) {
                val sign = getByte(newPos)
                if (sign == C.PLUS_INT || sign == C.MINUS_INT) {
                    newPos++
                    setPosition(newPos)
                }
            }

            var hasExpDigits = false
            readNumericLoop { hasExpDigits = true }
            if (!hasExpDigits) throwError(C.ERR_EXPECTED_EXPONENT_DIGITS)
        }
    }

    if (isQuoted) consumeNumericCoercionFooter()
    setNextTokenByte(-1)
}

internal inline fun parseIntDigitsImpl(
    isNegative: Boolean,
    startOfNumber: Int,
    crossinline readNumericLoop: (onDigit: (Int) -> Unit, onBreak: (Int) -> Unit) -> Unit,
    crossinline calculateIntWithOverflowCheck: (Int, Int, Boolean) -> Int,
    crossinline isNumericSeparator: (Int) -> Boolean,
    crossinline setPosition: (Int) -> Unit,
    crossinline nextDouble: () -> Double,
    crossinline throwError: (String) -> Nothing,
    crossinline setNextTokenByte: (Int) -> Unit
): Int {
    var accumulatedValue = 0
    var digitCount = 0
    var hasDigitsFound = false
    setNextTokenByte(-1)
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
                setPosition(startOfNumber)
                earlyExitResult = nextDouble().toInt()
            }
        }
    )

    if (earlyExitResult != null) return earlyExitResult!!
    if (!hasDigitsFound) throwError(C.ERR_EXPECTED_INT_PART)
    return accumulatedValue
}

internal inline fun parseLongDigitsImpl(
    isNegative: Boolean,
    startOfNumber: Int,
    crossinline readNumericLoop: (onDigit: (Int) -> Unit, onBreak: (Int) -> Unit) -> Unit,
    crossinline calculateLongWithOverflowCheck: (Long, Int, Boolean) -> Long,
    crossinline isNumericSeparator: (Int) -> Boolean,
    crossinline setPosition: (Int) -> Unit,
    crossinline nextDouble: () -> Double,
    crossinline throwError: (String) -> Nothing,
    crossinline setNextTokenByte: (Int) -> Unit
): Long {
    var accumulatedValue = 0L
    var digitCount = 0
    var hasDigitsFound = false
    setNextTokenByte(-1)
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
                setPosition(startOfNumber)
                earlyExitResult = nextDouble().toLong()
            }
        }
    )

    if (earlyExitResult != null) return earlyExitResult!!
    if (!hasDigitsFound) throwError(C.ERR_EXPECTED_INT_PART)
    return accumulatedValue
}

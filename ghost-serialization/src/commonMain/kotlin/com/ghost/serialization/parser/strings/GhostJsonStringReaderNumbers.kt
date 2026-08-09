@file:Suppress("NOTHING_TO_INLINE")
@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser.strings

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.accumulateIntWithOverflowCheck
import com.ghost.serialization.parser.common.accumulateLongWithOverflowCheck
import com.ghost.serialization.parser.common.consumeNumericCoercionFooterCore
import com.ghost.serialization.parser.common.getDoublePowerOfTen
import com.ghost.serialization.parser.common.getFloatPowerOfTen
import com.ghost.serialization.parser.common.isDigit
import com.ghost.serialization.parser.common.isExponentMarker
import com.ghost.serialization.parser.common.isNumericSeparator
import com.ghost.serialization.parser.common.parseExponentValueCore
import com.ghost.serialization.parser.common.prepareNumericHeaderCore
import com.ghost.serialization.parser.common.validateLeadingZeroCore
import com.ghost.serialization.parser.common.GhostJsonConstants as C

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

fun GhostJsonStringReader.nextULong(): ULong {
    if (peekNextToken() == C.QUOTE_INT) {
        return nextString().toULong()
    }
    return nextLong().toULong()
}

fun GhostJsonStringReader.nextFloat(): Float {
    val header = prepareNumericHeader()
    val isQuoted = (header and C.NUMERIC_HEADER_QUOTED) != 0
    val isNegativeValue = (header and C.NUMERIC_HEADER_NEGATIVE) != 0

    validateLeadingZero()

    var mantissa = 0L
    var exponent = 0
    var digitCount = 0

    nextTokenByte = C.RESET_TOKEN_BYTE
    val localLimit = limit
    val chars = rawChars
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

    nextTokenByte = C.RESET_TOKEN_BYTE
    val localLimit = limit
    val chars = rawChars
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

private fun GhostJsonStringReader.parseExponentValue(): Int =
    parseExponentValueCore(
        startPosition = position,
        limit = limit,
        getByte = { getByte(it) },
        setPosition = { position = it },
        throwError = { throwError(it) },
    )

private fun GhostJsonStringReader.prepareNumericHeader(): Int =
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

private fun GhostJsonStringReader.parseIntDigits(isNegative: Boolean, startOfNumber: Int): Int {
    var accumulatedValue = 0
    var digitCount = 0
    var hasDigitsFound = false
    nextTokenByte = C.RESET_TOKEN_BYTE
    var earlyExitResult: Int? = null

    val localLimit = limit
    val chars = rawChars
    while (position < localLimit) {
        val byte = chars[position].code
        if (isDigit(byte)) {
            val digit = byte - C.ZERO_INT
            accumulatedValue = if (digitCount < C.INT_SAFE_DIGITS) {
                accumulatedValue * C.BASE_TEN + digit
            } else {
                accumulateIntWithOverflowCheck(accumulatedValue, digit, isNegative) {
                    throwError(C.ERR_INT_OVERFLOW)
                }
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
    nextTokenByte = C.RESET_TOKEN_BYTE
    var earlyExitResult: Long? = null

    val localLimit = limit
    val chars = rawChars
    while (position < localLimit) {
        val byte = chars[position].code
        if (isDigit(byte)) {
            val digit = byte - C.ZERO_INT
            accumulatedValue = if (digitCount < C.LONG_SAFE_DIGITS) {
                accumulatedValue * C.BASE_TEN + digit
            } else {
                accumulateLongWithOverflowCheck(accumulatedValue, digit, isNegative) {
                    throwError(C.ERR_LONG_OVERFLOW)
                }
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
}

private fun GhostJsonStringReader.validateLeadingZero() {
    validateLeadingZeroCore(
        position = position,
        limit = limit,
        getByte = { getByte(it) },
        throwError = { throwError(it) },
    )
}

fun GhostJsonStringReader.skipNumber() {
    val header = prepareNumericHeader()
    val isQuoted = (header and C.NUMERIC_HEADER_QUOTED) != 0

    var hasDigits = false

    val numberPosition = position
    val numberLimit = limit
    val chars = rawChars
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
    nextTokenByte = C.RESET_TOKEN_BYTE
}

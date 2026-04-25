@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.core.parser

import com.ghost.serialization.InternalGhostApi
import kotlin.math.pow

fun GhostJsonReader.nextFloat(): Float = nextDouble().toFloat()

fun GhostJsonReader.nextInt(): Int {
    val value = nextLong()
    if (value > Int.MAX_VALUE || value < Int.MIN_VALUE) {
        throwError("Integer overflow: $value")
    }
    return value.toInt()
}

fun GhostJsonReader.nextLong(): Long {
    if (nextTokenByte == -1) skipWhitespace()
    if (position >= limit) throwError("Expected number but reached EOF")

    val isQuoted = source[position] == GhostJsonConstants.QUOTE
    if (isQuoted) {
        if (!coerceStringsToNumbers) throwError("Unexpected string for numeric type (coercion disabled)")
        internalSkip(1)
        peekNextToken()
    }

    var cursor = position
    var negative = false
    if (cursor < limit && source[cursor] == GhostJsonConstants.MINUS) {
        negative = true
        cursor++
        if (cursor >= limit) throwError("Isolated minus sign")
    }

    // Optimized integer parsing path
    var value = 0L
    var hasDigits = false

    // Check first digit for leading zero (Rule: leading zeros only allowed if it's JUST '0')
    val firstDigit = source[cursor].toInt()
    if (firstDigit == 48) { // '0'
        cursor++
        hasDigits = true
        if (cursor < limit) {
            val nextDigit = source[cursor].toInt()
            if (nextDigit in 48..57) {
                throwError("Leading zeros are not allowed")
            }
        }
    } else {
        // Common case: Positive integer
        while (cursor < limit) {
            val currentByte = source[cursor].toInt()
            val digitValue = currentByte - 48
            if (digitValue in 0..9) {
                // Overflow check: value * 10 + digitValue > Long.MAX_VALUE
                if (value > 922337203685477580L || (value == 922337203685477580L && digitValue > 7)) {
                    // Potential overflow. Check if it's the special MIN_VALUE case
                    if (negative && value == 922337203685477580L && digitValue == 8) {
                        value = Long.MIN_VALUE
                        hasDigits = true
                        cursor++
                        // Ensure no more digits follow
                        if (cursor < limit && source[cursor].toInt() - 48 in 0..9) {
                            throwError("Long overflow")
                        }
                        break
                    }
                    throwError("Long overflow")
                }
                value = value * 10 + digitValue
                hasDigits = true
                cursor++
            } else if (
                currentByte == GhostJsonConstants.DOT.toInt() ||
                currentByte == GhostJsonConstants.EXP_LOWER.toInt() ||
                currentByte == GhostJsonConstants.EXP_UPPER.toInt()
            ) {
                return nextDouble().toLong()
            } else break
        }
    }

    if (!hasDigits) {
        throwError("Expected digits but found ${source[cursor].toInt().toChar()}")
    }

    internalSkip(cursor - position)
    val result = if (value == Long.MIN_VALUE) value else (if (negative) -value else value)

    if (isQuoted) {
        if (position >= limit || source[position] != GhostJsonConstants.QUOTE) {
            throwError("Expected closing quote for coerced number")
        }
        internalSkip(1)
        peekNextToken()
    }

    return result
}

fun GhostJsonReader.nextDouble(): Double {
    if (nextTokenByte == -1) skipWhitespace()
    if (position >= limit) throwError("Expected number but reached EOF")

    val isQuoted = source[position] == GhostJsonConstants.QUOTE
    if (isQuoted) {
        if (!coerceStringsToNumbers) throwError("Unexpected string for numeric type (coercion disabled)")
        internalSkip(1)
        peekNextToken()
    }

    var cursor = position
    var negative = false
    if (cursor < limit && source[cursor] == GhostJsonConstants.MINUS) {
        negative = true
        cursor++
    }

    // Leading zero check
    if (cursor < limit && source[cursor] == 48.toByte() && cursor + 1 < limit) {
        val nextDigit = source[cursor + 1].toInt()
        if (nextDigit in 48..57) throwError("Leading zeros are not allowed")
    }

    var result = 0.0
    var hasIntDigits = false
    var intDigits = 0
    while (cursor < limit) {
        val digitValue = source[cursor].toInt() - 48
        if (digitValue in 0..9) {
            if (intDigits < 18) {
                result = result * 10.0 + digitValue
                intDigits++
            } else {
                result = Double.POSITIVE_INFINITY // This will be caught or handled
            }
            cursor++
            hasIntDigits = true
        } else {
            break
        }
    }

    if (!hasIntDigits) throwError("Expected integer part of number")

    if (cursor < limit && source[cursor] == GhostJsonConstants.DOT) {
        cursor++
        if (cursor >= limit) {
            throwError("Expected digits after decimal point")
        }
        var scale = 0
        var decimalValue = 0.0
        while (cursor < limit) {
            val digitValue = source[cursor].toInt() - 48
            if (digitValue in 0..9) {
                // Precision injection protection: Only parse up to ~17-18 significant decimal digits
                // to avoid Double overflow during calculation.
                if (scale < 18) {
                    decimalValue = decimalValue * 10.0 + digitValue
                    scale++
                } else {
                    // Still consume the digits but ignore them for the value
                }
                cursor++
            } else break
        }
        if (scale == 0) {
            throwError("Expected digits after decimal point")
        }
        val decimalDouble = if (scale < GhostJsonConstants.INVERSE_POWERS_OF_TEN.size) {
            decimalValue * GhostJsonConstants.INVERSE_POWERS_OF_TEN[scale]
        } else {
            decimalValue / 10.0.pow(scale.toDouble())
        }
        result += decimalDouble
    }

    if (cursor < limit) {
        val currentByte = source[cursor]
        if (
            currentByte == GhostJsonConstants.EXP_LOWER ||
            currentByte == GhostJsonConstants.EXP_UPPER
        ) {
            cursor++
            var expNegative = false
            if (cursor < limit) {
                if (source[cursor] == GhostJsonConstants.MINUS) {
                    expNegative = true; cursor++
                } else if (source[cursor] == GhostJsonConstants.PLUS) {
                    cursor++
                }
            }
            var exponent = 0
            var hasExpDigits = false
            while (cursor < limit) {
                val digitValue = source[cursor] - '0'.code.toByte()
                if (digitValue in 0..9) {
                    exponent = exponent * 10 + digitValue
                    hasExpDigits = true
                    cursor++
                } else break
            }
            if (!hasExpDigits) {
                throwError("Expected digits in exponent")
            }
            if (exponent > 0) {
                if (expNegative) {
                    val factor = if (exponent < GhostJsonConstants.INVERSE_POWERS_OF_TEN.size) {
                        GhostJsonConstants.INVERSE_POWERS_OF_TEN[exponent]
                    } else {
                        1.0 / 10.0.pow(exponent.toDouble())
                    }
                    result *= factor
                } else {
                    val factor = if (exponent < GhostJsonConstants.POWERS_OF_TEN.size) {
                        GhostJsonConstants.POWERS_OF_TEN[exponent]
                    } else {
                        10.0.pow(exponent.toDouble())
                    }
                    result *= factor
                }
            }
        }
    }

    if (negative) result = -result

    if (result.isInfinite() || result.isNaN()) {
        throwError("Numeric overflow or NaN is not allowed in JSON")
    }

    val consumedBytes = cursor - position
    internalSkip(consumedBytes)
    val finalResult = result

    if (isQuoted) {
        if (position >= limit || source[position] != GhostJsonConstants.QUOTE) {
            throwError("Expected closing quote for coerced number")
        }
        internalSkip(1)
        peekNextToken()
    }

    return finalResult
}

internal fun GhostJsonReader.skipRawNumber() {
    skipWhitespace()
    if (position >= limit) throwError("Expected number but reached EOF")

    var cursor = position
    if (source[cursor] == GhostJsonConstants.MINUS) cursor++

    var hasDot = false
    var hasE = false
    var hasDigits = false

    while (cursor < limit) {
        val currentByte = source[cursor]
        when {
            currentByte >= '0'.code.toByte() && currentByte <= '9'.code.toByte() -> {
                if (!hasDigits && currentByte == '0'.code.toByte() && cursor + 1 < limit) {
                    val nextByte = source[cursor + 1]
                    if (nextByte >= '0'.code.toByte() && nextByte <= '9'.code.toByte()) {
                        throwError("Leading zeros are not allowed")
                    }
                }
                hasDigits = true
                cursor++
            }

            currentByte == GhostJsonConstants.DOT -> {
                if (hasDot || hasE || !hasDigits) throwError("Invalid decimal point")
                hasDot = true
                cursor++
                if (cursor >= limit) throwError("Trailing decimal point")
                val nextByte = source[cursor]
                if (nextByte < '0'.code.toByte() || nextByte > '9'.code.toByte()) {
                    throwError("Trailing decimal point")
                }
                cursor++
            }

            currentByte == GhostJsonConstants.EXP_LOWER || currentByte == GhostJsonConstants.EXP_UPPER -> {
                if (hasE || !hasDigits) throwError("Invalid exponent")
                hasE = true
                cursor++
                if (cursor < limit) {
                    val exponentByte = source[cursor]
                    if (exponentByte == GhostJsonConstants.MINUS || exponentByte == GhostJsonConstants.PLUS) cursor++
                }
                if (cursor >= limit) throwError("Empty exponent")
                val nextByte = source[cursor]
                if (nextByte < '0'.code.toByte() || nextByte > '9'.code.toByte()) {
                    throwError("Empty exponent")
                }
                cursor++
            }

            else -> {
                val byteCode = currentByte.toInt() and 0xFF
                if (byteCode < 128 && GhostJsonConstants.IS_TERMINATOR[byteCode]) break
                throwError("Unexpected character in number: ${currentByte.toInt().toChar()}")
            }
        }
    }

    if (!hasDigits) throwError("Empty number")
    val consumedBytes = cursor - position
    internalSkip(consumedBytes)
}

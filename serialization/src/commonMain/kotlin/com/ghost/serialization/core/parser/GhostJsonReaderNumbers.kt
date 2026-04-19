package com.ghostserializer.core.parser

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
    if (positon >= data.size) throwError("Expected number but reached EOF")

    val isQuoted = data[positon] == GhostJsonConstants.QUOTE
    if (isQuoted) {
        if (!coerceStringsToNumbers) throwError("Unexpected string for numeric type (coercion disabled)")
        internalSkip(1)
        peekNextToken()
    }

    var cursor = positon
    var negative = false
    if (cursor < data.size && data[cursor] == GhostJsonConstants.MINUS) {
        negative = true
        cursor++
        if (cursor >= data.size) throwError("Isolated minus sign")
    }

    // Optimized integer parsing path
    var value = 0L
    var hasDigits = false

    // Check first digit for leading zero (Rule: leading zeros only allowed if it's JUST '0')
    val firstDigit = data[cursor].toInt()
    if (firstDigit == 48) { // '0'
        cursor++
        hasDigits = true
        if (cursor < data.size) {
            val nextDigit = data[cursor].toInt()
            if (nextDigit in 48..57) {
                throwError("Leading zeros are not allowed")
            }
        }
    } else {
        // Common case: Positive integer
        while (cursor < data.size) {
            val currentByte = data[cursor].toInt()
            val digitValue = currentByte - 48
            if (digitValue in 0..9) {
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
        throwError("Expected digits but found ${data[cursor].toInt().toChar()}")
    }

    internalSkip(cursor - positon)
    val result = if (negative) -value else value

    if (isQuoted) {
        if (positon >= data.size || data[positon] != GhostJsonConstants.QUOTE) {
            throwError("Expected closing quote for coerced number")
        }
        internalSkip(1)
        peekNextToken()
    }

    return result
}

fun GhostJsonReader.nextDouble(): Double {
    if (nextTokenByte == -1) skipWhitespace()
    if (positon >= data.size) throwError("Expected number but reached EOF")

    val isQuoted = data[positon] == GhostJsonConstants.QUOTE
    if (isQuoted) {
        if (!coerceStringsToNumbers) throwError("Unexpected string for numeric type (coercion disabled)")
        internalSkip(1)
        peekNextToken()
    }

    var cursor = positon
    var negative = false
    if (cursor < data.size && data[cursor] == GhostJsonConstants.MINUS) {
        negative = true
        cursor++
    }

    // Leading zero check
    if (cursor < data.size && data[cursor] == 48.toByte() && cursor + 1 < data.size) {
        val nextDigit = data[cursor + 1].toInt()
        if (nextDigit in 48..57) throwError("Leading zeros are not allowed")
    }

    var result = 0.0
    var hasIntDigits = false
    var intDigits = 0
    while (cursor < data.size) {
        val digitValue = data[cursor].toInt() - 48
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

    if (cursor < data.size && data[cursor] == GhostJsonConstants.DOT) {
        cursor++
        if (cursor >= data.size) {
            throwError("Expected digits after decimal point")
        }
        var scale = 0
        var decimalValue = 0.0
        while (cursor < data.size) {
            val digitValue = data[cursor].toInt() - 48
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

    if (cursor < data.size) {
        val currentByte = data[cursor]
        if (
            currentByte == GhostJsonConstants.EXP_LOWER ||
            currentByte == GhostJsonConstants.EXP_UPPER
        ) {
            cursor++
            var expNegative = false
            if (cursor < data.size) {
                if (data[cursor] == GhostJsonConstants.MINUS) {
                    expNegative = true; cursor++
                } else if (data[cursor] == GhostJsonConstants.PLUS) {
                    cursor++
                }
            }
            var exponent = 0
            var hasExpDigits = false
            while (cursor < data.size) {
                val digitValue = data[cursor] - '0'.code.toByte()
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

    val consumedBytes = cursor - positon
    internalSkip(consumedBytes)
    val finalResult = result

    if (isQuoted) {
        if (positon >= data.size || data[positon] != GhostJsonConstants.QUOTE) {
            throwError("Expected closing quote for coerced number")
        }
        internalSkip(1)
        peekNextToken()
    }

    return finalResult
}

internal fun GhostJsonReader.skipRawNumber() {
    skipWhitespace()
    if (positon >= data.size) throwError("Expected number but reached EOF")

    var cursor = positon
    if (data[cursor] == GhostJsonConstants.MINUS) cursor++

    var hasDot = false
    var hasE = false
    var hasDigits = false

    while (cursor < data.size) {
        val currentByte = data[cursor]
        when {
            currentByte >= '0'.code.toByte() && currentByte <= '9'.code.toByte() -> {
                if (!hasDigits && currentByte == '0'.code.toByte() && cursor + 1 < data.size) {
                    val nextByte = data[cursor + 1]
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
                if (cursor >= data.size) throwError("Trailing decimal point")
                val nextByte = data[cursor]
                if (nextByte < '0'.code.toByte() || nextByte > '9'.code.toByte()) {
                    throwError("Trailing decimal point")
                }
                cursor++
            }

            currentByte == GhostJsonConstants.EXP_LOWER || currentByte == GhostJsonConstants.EXP_UPPER -> {
                if (hasE || !hasDigits) throwError("Invalid exponent")
                hasE = true
                cursor++
                if (cursor < data.size) {
                    val exponentByte = data[cursor]
                    if (exponentByte == GhostJsonConstants.MINUS || exponentByte == GhostJsonConstants.PLUS) cursor++
                }
                if (cursor >= data.size) throwError("Empty exponent")
                val nextByte = data[cursor]
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
    val consumedBytes = cursor - positon
    internalSkip(consumedBytes)
}

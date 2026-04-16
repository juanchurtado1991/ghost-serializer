package com.ghost.serialization.core

import com.ghost.serialization.core.GhostJsonConstants.POWERS_OF_TEN
import kotlin.math.pow

fun GhostJsonReader.nextFloat(): Float = nextDouble().toFloat()

fun GhostJsonReader.nextInt(): Int {
    val value = nextLong()
    if (value > Int.MAX_VALUE || value < Int.MIN_VALUE) {
        internalThrowError("Integer overflow: $value")
    }
    return value.toInt()
}

fun GhostJsonReader.nextLong(): Long {
    skipWhitespace()
    if (pos >= data.size) internalThrowError("Expected number but reached EOF")

    var i = pos
    var value = 0L
    var negative = false

    if (data[i] == GhostJsonConstants.MINUS) {
        negative = true
        i++
        if (i >= data.size) internalThrowError("Isolated minus sign")
    }

    if (data[i] == '0'.code.toByte() && i + 1 < data.size) {
        val next = data[i + 1]
        if (next >= '0'.code.toByte() && next <= '9'.code.toByte()) {
            internalThrowError("Leading zeros are not allowed")
        }
    }

    var hasDigits = false
    while (i < data.size) {
        val b = data[i]
        val digit = b - '0'.code.toByte()
        if (digit in 0..9) {
            value = value * 10 + digit
            hasDigits = true
            i++
        } else if (b == GhostJsonConstants.DOT || b == GhostJsonConstants.EXP_LOWER || b == GhostJsonConstants.EXP_UPPER) {
            return nextDouble().toLong()
        } else if (b.toInt() and 0xFF < 128 && GhostJsonConstants.IS_TERMINATOR[b.toInt() and 0xFF]) {
            break
        } else {
            internalThrowError("Invalid character in number: ${b.toInt().toChar()}")
        }
    }

    if (!hasDigits) internalThrowError("Expected digits but found EOF")

    val consumed = i - pos
    internalSkip(consumed)
    return if (negative) -value else value
}

fun GhostJsonReader.nextDouble(): Double {
    skipWhitespace()
    if (pos >= data.size) internalThrowError("Expected number but reached EOF")

    var i = pos
    var negative = false

    if (data[i] == GhostJsonConstants.MINUS) {
        negative = true
        i++
    }

    if (i < data.size && data[i] == '0'.code.toByte() && i + 1 < data.size) {
        val next = data[i + 1]
        if (next >= '0'.code.toByte() && next <= '9'.code.toByte()) {
            internalThrowError("Leading zeros are not allowed")
        }
    }

    var result = 0.0
    var hasIntDigits = false
    while (i < data.size) {
        val digit = data[i] - '0'.code.toByte()
        if (digit in 0..9) {
            result = result * 10.0 + digit
            i++
            hasIntDigits = true
        } else break
    }

    if (!hasIntDigits) internalThrowError("Expected integer part of number")

    if (i < data.size && data[i] == GhostJsonConstants.DOT) {
        i++
        if (i >= data.size) internalThrowError("Expected digits after decimal point")
        var scale = 0
        var decimalValue = 0.0
        while (i < data.size) {
            val digit = data[i] - '0'.code.toByte()
            if (digit in 0..9) {
                decimalValue = decimalValue * 10.0 + digit
                scale++
                i++
            } else break
        }
        if (scale == 0) internalThrowError("Expected digits after decimal point")
        if (scale < POWERS_OF_TEN.size) {
            result += decimalValue / POWERS_OF_TEN[scale]
        } else {
            result += decimalValue / 10.0.pow(scale.toDouble())
        }
    }

    if (i < data.size) {
        val b = data[i]
        if (b == GhostJsonConstants.EXP_LOWER || b == GhostJsonConstants.EXP_UPPER) {
            i++
            var expNegative = false
            if (i < data.size) {
                if (data[i] == GhostJsonConstants.MINUS) { expNegative = true; i++ }
                else if (data[i] == GhostJsonConstants.PLUS) { i++ }
            }
            var exponent = 0
            var hasExpDigits = false
            while (i < data.size) {
                val digit = data[i] - '0'.code.toByte()
                if (digit in 0..9) {
                    exponent = exponent * 10 + digit
                    hasExpDigits = true
                    i++
                } else break
            }
            if (!hasExpDigits) internalThrowError("Expected digits in exponent")
            if (exponent > 0) {
                val factor = if (exponent < POWERS_OF_TEN.size) POWERS_OF_TEN[exponent] else 10.0.pow(exponent.toDouble())
                if (expNegative) result /= factor else result *= factor
            }
        }
    }

    if (negative) result = -result

    if (result.isInfinite() || result.isNaN()) {
        internalThrowError("Numeric overflow or NaN is not allowed in JSON")
    }

    val consumed = i - pos
    internalSkip(consumed)
    return result
}

internal fun GhostJsonReader.skipRawNumber() {
    skipWhitespace()
    if (pos >= data.size) internalThrowError("Expected number but reached EOF")

    var i = pos
    if (data[i] == GhostJsonConstants.MINUS) i++

    var hasDot = false
    var hasE = false
    var hasDigits = false

    while (i < data.size) {
        val b = data[i]
        when {
            b >= '0'.code.toByte() && b <= '9'.code.toByte() -> {
                if (!hasDigits && b == '0'.code.toByte() && i + 1 < data.size) {
                    val next = data[i + 1]
                    if (next >= '0'.code.toByte() && next <= '9'.code.toByte()) {
                        internalThrowError("Leading zeros are not allowed")
                    }
                }
                hasDigits = true
                i++
            }
            b == GhostJsonConstants.DOT -> {
                if (hasDot || hasE || !hasDigits) internalThrowError("Invalid decimal point")
                hasDot = true
                i++
                if (i >= data.size) internalThrowError("Trailing decimal point")
                val next = data[i]
                if (next < '0'.code.toByte() || next > '9'.code.toByte()) {
                    internalThrowError("Trailing decimal point")
                }
                i++
            }
            b == GhostJsonConstants.EXP_LOWER || b == GhostJsonConstants.EXP_UPPER -> {
                if (hasE || !hasDigits) internalThrowError("Invalid exponent")
                hasE = true
                i++
                if (i < data.size) {
                    val eb = data[i]
                    if (eb == GhostJsonConstants.MINUS || eb == GhostJsonConstants.PLUS) i++
                }
                if (i >= data.size) internalThrowError("Empty exponent")
                val next = data[i]
                if (next < '0'.code.toByte() || next > '9'.code.toByte()) {
                    internalThrowError("Empty exponent")
                }
                i++
            }
            else -> {
                val code = b.toInt() and 0xFF
                if (code < 128 && GhostJsonConstants.IS_TERMINATOR[code]) break
                internalThrowError("Unexpected character in number: ${b.toInt().toChar()}")
            }
        }
    }

    if (!hasDigits) internalThrowError("Empty number")
    val consumed = i - pos
    internalSkip(consumed)
}

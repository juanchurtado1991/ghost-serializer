package com.ghost.serialization.core

import com.ghost.serialization.core.GhostJsonConstants.POWERS_OF_TEN
import kotlin.math.pow

/**
 * Robust 'Vortex' Numeric Scanner V2.
 * High-performance, single-pass scanner with strict JSON compliance.
 * Centralized Consumption (V23).
 */

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
    
    if (!source.request(1)) internalThrowError("Expected number but reached EOF")
    
    val buf = source.buffer
    var i = 0L
    var value = 0L
    var negative = false
    
    if (buf[0] == GhostJsonConstants.MINUS) {
        negative = true
        i++
        if (!source.request(i + 1)) internalThrowError("Isolated minus sign")
    }

    // spec: leading zero check
    val firstDigit = buf[i]
    if (firstDigit == '0'.code.toByte()) {
        if (source.request(i + 2)) {
            val next = buf[i + 1]
            if (next >= '0'.code.toByte() && next <= '9'.code.toByte()) {
                internalThrowError("Leading zeros are not allowed")
            }
        }
    }

    var hasDigits = false
    while (true) {
        if (!source.request(i + 1)) break
        
        val b = buf[i]
        val digit = b - '0'.code.toByte()
        if (digit in 0..9) {
            value = value * 10 + digit
            hasDigits = true
            i++
        } else if (b == GhostJsonConstants.DOT || b == GhostJsonConstants.EXP_LOWER || b == GhostJsonConstants.EXP_UPPER) {
            return nextDouble().toLong()
        } else if (GhostJsonConstants.IS_TERMINATOR[b.toInt() and 0xFF]) {
            break
        } else {
            internalThrowError("Invalid character in number: ${b.toInt().toChar()}")
        }
    }

    if (!hasDigits) {
        val found = if (source.request(1)) source.buffer[0].toInt().toChar().toString() else "EOF"
        internalThrowError("Expected digits but found '$found' at column $column")
    }
    
    internalSkip(i) // V23: Unified consumption
    return if (negative) -value else value
}

fun GhostJsonReader.nextDouble(): Double {
    skipWhitespace()
    
    if (!source.request(1)) internalThrowError("Expected number part but reached EOF")
    
    val buf = source.buffer
    var i = 0L
    var negative = false
    
    if (buf[0] == GhostJsonConstants.MINUS) {
        negative = true
        i++
    }

    if (source.request(i + 1) && buf[i] == '0'.code.toByte()) {
        if (source.request(i + 2)) {
            val next = buf[i + 1]
            if (next >= '0'.code.toByte() && next <= '9'.code.toByte()) {
                internalThrowError("Leading zeros are not allowed")
            }
        }
    }

    var result = 0.0
    var hasIntDigits = false
    while (source.request(i + 1)) {
        val digit = buf[i] - '0'.code.toByte()
        if (digit in 0..9) {
            result = result * 10.0 + digit
            i++
            hasIntDigits = true
        } else break
    }
    
    if (!hasIntDigits) {
        val found = if (source.request(1)) source.buffer[0].toInt().toChar().toString() else "EOF"
        internalThrowError("Expected integer part of number but found '$found' at column $column")
    }

    // Fractional part
    if (source.request(i + 1) && buf[i] == GhostJsonConstants.DOT) {
        i++
        if (!source.request(i + 1)) internalThrowError("Expected digits after decimal point")
        var scale = 0
        var decimalValue = 0.0
        while (source.request(i + 1)) {
            val digit = buf[i] - '0'.code.toByte()
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
    
    // Exponent part
    if (source.request(i + 1)) {
        val b = buf[i]
        if (b == GhostJsonConstants.EXP_LOWER || b == GhostJsonConstants.EXP_UPPER) {
            i++
            var expNegative = false
            if (source.request(i + 1)) {
                if (buf[i] == GhostJsonConstants.MINUS) {
                    expNegative = true
                    i++
                } else if (buf[i] == GhostJsonConstants.PLUS) {
                    i++
                }
            }
            
            var exponent = 0
            var hasExpDigits = false
            while (source.request(i + 1)) {
                val digit = buf[i] - '0'.code.toByte()
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

    internalSkip(i) // V23: Unified consumption
    return result
}

internal fun GhostJsonReader.skipRawNumber() {
    skipWhitespace()
    if (!source.request(1)) internalThrowError("Expected number but reached EOF")
    
    var i = 0L
    val buf = source.buffer
    if (buf[0] == GhostJsonConstants.MINUS) i++
    
    var hasDot = false
    var hasE = false
    var hasDigits = false

    while (source.request(i + 1)) {
        val b = buf[i]
        when (b) {
            in '0'.code.toByte()..'9'.code.toByte() -> {
                if (!hasDigits && b == '0'.code.toByte() && source.request(i + 2)) {
                    val next = buf[i+1]
                    if (next >= '0'.code.toByte() && next <= '9'.code.toByte()) {
                        internalThrowError("Leading zeros are not allowed")
                    }
                }
                hasDigits = true
                i++
            }
            GhostJsonConstants.DOT -> {
                if (hasDot || hasE || !hasDigits) internalThrowError("Invalid decimal point")
                hasDot = true
                i++
                if (!source.request(i + 1)) internalThrowError("Trailing decimal point")
                val next = buf[i]
                if (next < '0'.code.toByte() || next > '9'.code.toByte()) {
                    internalThrowError("Trailing decimal point")
                }
                i++ // V23: Fixed skip point
            }
            GhostJsonConstants.EXP_LOWER, GhostJsonConstants.EXP_UPPER -> {
                if (hasE || !hasDigits) internalThrowError("Invalid exponent")
                hasE = true
                i++
                if (source.request(i + 1)) {
                    val eb = buf[i]
                    if (eb == GhostJsonConstants.MINUS || eb == GhostJsonConstants.PLUS) i++
                }
                if (!source.request(i + 1)) internalThrowError("Empty exponent")
                val next = buf[i]
                if (next < '0'.code.toByte() || next > '9'.code.toByte()) {
                    internalThrowError("Empty exponent")
                }
                i++ // V23: Fixed skip point
            }
            else -> {
                if (GhostJsonConstants.IS_TERMINATOR[b.toInt() and 0xFF]) break
                internalThrowError("Unexpected character in number: ${b.toInt().toChar()}")
            }
        }
    }
    
    if (!hasDigits) internalThrowError("Empty number")
    internalSkip(i) // V23: Unified consumption
}

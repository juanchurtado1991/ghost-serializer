package com.ghostserializer.core.writer

import com.ghostserializer.core.parser.GhostJsonConstants
import kotlin.math.round

/**
 * Zero-Allocation Fast-Path engine for floating point numbers.
 * Handles 99% of API use cases (up to 9 decimals of precision)
 * without generating GC pressure.
 */
internal object GhostDoubleFormatter {

    private const val PRECISION_MULTIPLIER = 1_000_000_000.0
    private const val MAX_DECIMALS = 9

    /**
     * Writes a Double directly into the [scratch] buffer.
     * Returns the new length written.
     */
    fun writeDoubleDirect(
        value: Double,
        scratch: ByteArray,
        offset: Int,
        fallback: (Double) -> Int
    ): Int {
        if (!value.isFinite()) {
            return fallback(value)
        }

        var pos = offset
        var v = value

        if (v < 0.0) {
            scratch[pos++] = GhostJsonConstants.MINUS
            v = -v
        }

        // If number is massive or microscopic, delegate to native system
        if (v > Long.MAX_VALUE || (v > 0.0 && v < 0.000000001)) {
            return fallback(value)
        }

        val intPart = v.toLong()
        val fracPart = v - intPart

        // Round to eliminate IEEE-754 errors (e.g. 0.3 -> 0.299999999)
        var fracInt = round(fracPart * PRECISION_MULTIPLIER).toLong()

        if (fracInt >= 1_000_000_000L) {
            return writeLongDirect(intPart + 1, scratch, pos, writeDecimalZero = true)
        }

        pos = writeLongDirect(intPart, scratch, pos, writeDecimalZero = false)

        scratch[pos++] = GhostJsonConstants.DOT

        if (fracInt == 0L) {
            scratch[pos++] = GhostJsonConstants.ZERO
            return pos - offset
        }

        var decimalsToPrint = MAX_DECIMALS
        while (fracInt % 10L == 0L && decimalsToPrint > 1) {
            fracInt /= 10L
            decimalsToPrint--
        }

        val fracStartPos = pos
        pos += decimalsToPrint
        var writePos = pos - 1

        while (decimalsToPrint > 0) {
            val digit = (fracInt % 10L).toInt()
            scratch[writePos--] = (GhostJsonConstants.ZERO + digit).toByte()
            fracInt /= 10L
            decimalsToPrint--
        }

        return pos - offset
    }

    private fun writeLongDirect(
        value: Long,
        scratch: ByteArray,
        offset: Int,
        writeDecimalZero: Boolean
    ): Int {
        if (value == 0L) {
            scratch[offset] = GhostJsonConstants.ZERO
            if (writeDecimalZero) {
                scratch[offset + 1] = GhostJsonConstants.DOT
                scratch[offset + 2] = GhostJsonConstants.ZERO
                return offset + 3
            }
            return offset + 1
        }

        var v = value
        // Use industrial LUT optimization for the integer part
        var i = 48
        val tempBuffer = scratch // We use the same scratch but from the end to avoid extra allocations
        
        while (v >= 100) {
            val remainder = (v % 100).toInt()
            v /= 100
            scratch[--i] = GhostJsonConstants.FormatUtils.DIGIT_ONES[remainder]
            scratch[--i] = GhostJsonConstants.FormatUtils.DIGIT_TENS[remainder]
        }
        if (v >= 10) {
            val remainder = v.toInt()
            scratch[--i] = GhostJsonConstants.FormatUtils.DIGIT_ONES[remainder]
            scratch[--i] = GhostJsonConstants.FormatUtils.DIGIT_TENS[remainder]
        } else {
            scratch[--i] = (v.toInt() + 48).toByte()
        }
        
        val length = 48 - i
        // Shift to the current offset
        for (j in 0 until length) {
            scratch[offset + j] = scratch[i + j]
        }
        
        var nextOffset = offset + length
        if (writeDecimalZero) {
            scratch[nextOffset++] = GhostJsonConstants.DOT
            scratch[nextOffset++] = GhostJsonConstants.ZERO
        }

        return nextOffset
    }
}

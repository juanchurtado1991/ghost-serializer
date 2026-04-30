package com.ghost.serialization.writer

import com.ghost.serialization.parser.GhostJsonConstants
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * Zero-Allocation Fast-Path engine for floating point numbers.
 * Handles 99% of API use cases (up to 9 decimals of precision)
 * without generating GC pressure.
 */
internal object GhostDoubleFormatter {

    private const val PRECISION_MULTIPLIER = 1_000_000_000.0
    private const val MAX_DECIMALS = 9
    private const val FRAC_LIMIT = 1_000_000_000L
    private const val SMALL_WHOLE_THRESHOLD = 1_000_000_000.0
    private const val MASSIVE_DOUBLE_THRESHOLD = 1e15
    private const val MICROSCOPIC_DOUBLE_THRESHOLD = 1e-9
    private const val TEN_LONG = 10L

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

        // Fast path for small whole numbers (very common in metrics/coordinates)
        if (v <= SMALL_WHOLE_THRESHOLD && v % GhostJsonConstants.WHOLE_NUMBER_CHECK == GhostJsonConstants.ZERO_DOUBLE) {
            return writeLongDirect(v.toLong(), scratch, pos, writeDecimalZero = true) - offset
        }

        // If number is massive or microscopic, delegate to native system
        if (v > MASSIVE_DOUBLE_THRESHOLD || (v > 0.0 && v < MICROSCOPIC_DOUBLE_THRESHOLD)) {
            return fallback(value)
        }

        val intPart = v.toLong()
        val fracPart = v - intPart

        // Round to eliminate IEEE-754 errors
        var fracInt = kotlin.math.round(fracPart * PRECISION_MULTIPLIER).toInt()

        if (fracInt >= FRAC_LIMIT) {
            return writeLongDirect(intPart + 1, scratch, pos, writeDecimalZero = true) - offset
        }

        pos = writeLongDirect(intPart, scratch, pos, writeDecimalZero = false)

        scratch[pos++] = GhostJsonConstants.DOT

        if (fracInt == 0) {
            scratch[pos++] = GhostJsonConstants.ZERO
            return pos - offset
        }

        var decimalsToPrint = MAX_DECIMALS
        while (decimalsToPrint > 1) {
            val q = fracInt / 10
            if (fracInt - (q * 10) != 0) break
            fracInt = q
            decimalsToPrint--
        }

        val fracStartPos = pos
        pos += decimalsToPrint
        var writePos = pos - 1

        var d = decimalsToPrint
        while (d > 0) {
            val q = fracInt / 10
            val digit = (fracInt - (q * 10))
            scratch[writePos--] = (GhostJsonConstants.ZERO + digit).toByte()
            fracInt = q
            d--
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
        var i = GhostJsonConstants.SCRATCH_BUFFER_SIZE
        
        while (v >= GhostJsonConstants.BASE_HUNDRED) {
            val q = v / GhostJsonConstants.BASE_HUNDRED
            val r = (v - (q * GhostJsonConstants.BASE_HUNDRED)).toInt()
            v = q
            scratch[--i] = GhostJsonConstants.FormatUtils.DIGIT_ONES[r]
            scratch[--i] = GhostJsonConstants.FormatUtils.DIGIT_TENS[r]
        }
        if (v >= GhostJsonConstants.BASE_TEN) {
            val r = v.toInt()
            scratch[--i] = GhostJsonConstants.FormatUtils.DIGIT_ONES[r]
            scratch[--i] = GhostJsonConstants.FormatUtils.DIGIT_TENS[r]
        } else {
            scratch[--i] = (v.toInt() + GhostJsonConstants.ASCII_OFFSET).toByte()
        }
        
        val length = GhostJsonConstants.SCRATCH_BUFFER_SIZE - i
        
        // Use a simple loop for the shift, but ensure it's tight
        var j = 0
        while (j < length) {
            scratch[offset + j] = scratch[i + j]
            j++
        }
        
        var nextOffset = offset + length
        if (writeDecimalZero) {
            scratch[nextOffset++] = GhostJsonConstants.DOT
            scratch[nextOffset++] = GhostJsonConstants.ZERO
        }

        return nextOffset
    }
}

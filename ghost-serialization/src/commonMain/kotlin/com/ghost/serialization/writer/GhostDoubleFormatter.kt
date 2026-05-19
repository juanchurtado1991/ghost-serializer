package com.ghost.serialization.writer

import com.ghost.serialization.parser.GhostJsonConstants as C
import kotlin.math.roundToInt

/**
 * Zero-Allocation Fast-Path engine for floating point numbers.
 * Handles 99% of API use cases (up to 9 decimals of precision)
 * without generating GC pressure.
 */
internal object GhostDoubleFormatter {

    private const val SMALL_WHOLE_THRESHOLD = 1_000_000_000.0
    private const val PRECISION_MULTIPLIER = 1_000_000_000.0
    private const val MICROSCOPIC_DOUBLE_THRESHOLD = 1e-9
    private const val MASSIVE_DOUBLE_THRESHOLD = 1e15
    private const val FRAC_LIMIT = 1_000_000_000L
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
        if (!value.isFinite()) return fallback(value)

        var pos = offset
        var localValue = value

        if (localValue < 0.0) {
            scratch[pos++] = C.MINUS
            localValue = -localValue
        }

        // Fast path for small whole numbers
        // (very common in metrics/coordinates)
        if (
            localValue <= SMALL_WHOLE_THRESHOLD &&
            localValue % C.WHOLE_NUMBER_CHECK == C.ZERO_DOUBLE
        ) {
            return writeLongDirect(
                localValue.toLong(),
                scratch,
                pos,
                scratchEnd = pos + 32,
                writeDecimalZero = true
            ) - offset
        }

        // If number is massive or microscopic, delegate to native system
        if (
            localValue > MASSIVE_DOUBLE_THRESHOLD ||
            (localValue > 0.0 && localValue < MICROSCOPIC_DOUBLE_THRESHOLD)
        ) {
            return fallback(value)
        }

        val intPart = localValue.toLong()
        val fracPart = localValue - intPart

        // roundToInt avoids the Double intermediate that round() returns
        var fracInt = (fracPart * PRECISION_MULTIPLIER).roundToInt()

        if (fracInt >= FRAC_LIMIT) {
            return writeLongDirect(
                intPart + 1,
                scratch,
                pos,
                scratchEnd = pos + 32,
                writeDecimalZero = true
            ) - offset
        }

        pos = writeLongDirect(
            intPart,
            scratch,
            pos,
            scratchEnd = pos + 32,
            writeDecimalZero = false
        )

        scratch[pos++] = C.DOT

        if (fracInt == 0) {
            scratch[pos++] = C.ZERO
            return pos - offset
        }

        var decimalsToPrint = MAX_DECIMALS
        // Trim trailing zeros: % instead of multiply-subtract
        while (decimalsToPrint > 1 && fracInt % 10 == 0) {
            fracInt /= 10
            decimalsToPrint--
        }

        pos += decimalsToPrint
        var writePos = pos - 1

        while (decimalsToPrint >= 2) {
            val q = fracInt / 100
            val r = (fracInt - (q * 100)) * 2
            C.DOUBLE_DIGIT_LUT.copyInto(
                scratch,
                writePos - 1,
                r,
                r + 2
            )
            writePos -= 2
            fracInt = q
            decimalsToPrint -= 2
        }
        if (decimalsToPrint == 1) {
            scratch[writePos] = (C.ZERO_INT + fracInt % 10).toByte()
        }

        return pos - offset
    }

    private fun writeLongDirect(
        value: Long,
        scratch: ByteArray,
        offset: Int,
        scratchEnd: Int,
        writeDecimalZero: Boolean
    ): Int {
        if (value == 0L) {
            scratch[offset] = C.ZERO
            if (writeDecimalZero) {
                scratch[offset + 1] = C.DOT
                scratch[offset + 2] = C.ZERO
                return offset + 3
            }
            return offset + 1
        }

        var localValue = value
        // Write digits backward into the scratch zone at the end of our reserved area.
        // scratchEnd is always offset + 32, safely within FAST_BUF_SCRATCH_ZONE.
        var end = scratchEnd

        while (localValue >= C.BASE_HUNDRED) {
            val q = localValue / C.BASE_HUNDRED
            val r = (localValue - (q * C.BASE_HUNDRED)).toInt()
            localValue = q
            scratch[--end] = C.FormatUtils.DIGIT_ONES[r]
            scratch[--end] = C.FormatUtils.DIGIT_TENS[r]
        }
        if (localValue >= C.BASE_TEN) {
            val r = localValue.toInt()
            scratch[--end] = C.FormatUtils.DIGIT_ONES[r]
            scratch[--end] = C.FormatUtils.DIGIT_TENS[r]
        } else {
            scratch[--end] = (localValue.toInt() + C.ASCII_OFFSET).toByte()
        }

        val length = scratchEnd - end
        // Single System.arraycopy — JVM intrinsic, no per-byte loop
        scratch.copyInto(
            scratch,
            offset,
            end,
            end + length
        )

        var nextOffset = offset + length
        if (writeDecimalZero) {
            scratch[nextOffset++] = C.DOT
            scratch[nextOffset++] = C.ZERO
        }

        return nextOffset
    }
}

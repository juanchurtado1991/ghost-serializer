package com.ghost.serialization.writer.common

import com.ghost.serialization.parser.common.GhostFormatUtils
import kotlin.math.roundToInt
import com.ghost.serialization.parser.common.GhostJsonConstants as C

/**
 * Zero-allocation ASCII formatter for [Double] values, writing directly into a pre-allocated
 * [ByteArray] to bypass the GC overhead of `Double.toString()` on the hot serialization path.
 *
 * Handles up to [MAX_DECIMALS] (9) decimal places for values in `[1e-9, 1e9]`; anything outside
 * that range, non-finite, or microscopic returns [FALLBACK_REQUIRED] so the caller falls back to
 * a platform `toString()` without allocating a lambda on the hot path.
 */
internal object GhostDoubleFormatter {

    /** Maximum value below which a whole Double is formatted directly as a Long + ".0" */
    private const val SMALL_WHOLE_THRESHOLD = 1_000_000_000.0

    /** Multiplier to scale up 9 fractional decimal digits to a Long integer space */
    private const val PRECISION_MULTIPLIER = 1_000_000_000.0

    /** The lowest positive Double value processed by the fast-path without fallback */
    private const val MICROSCOPIC_DOUBLE_THRESHOLD = 1e-9

    /** The maximum Double value processed by the fast-path without fallback */
    private const val MASSIVE_DOUBLE_THRESHOLD = 1e9

    /** The carry-over boundary for fractional scaling (10^9) */
    private const val FRAC_LIMIT = 1_000_000_000L

    /** Maximum decimal precision supported (9 decimal places) */
    private const val MAX_DECIMALS = 9

    /**
     * Bytes reserved past position for [writeLongDirect] scratch (always within FAST_BUF_SCRATCH_ZONE).
     */
    private const val LONG_DIRECT_SCRATCH_SPAN = 32

    /** Scale applied to the base-100 remainder when indexing [C.DOUBLE_DIGIT_LUT] (2 ASCII digits). */
    private const val DIGIT_PAIR_LUT_STRIDE = 2
    private const val DIGIT_PAIR_WIDTH = 2

    /**
     * Returned when the fast-path cannot format the value; the caller must use a platform formatter.
     * Negative so call sites can keep `bytesWritten > 0` as the success check.
     */
    const val FALLBACK_REQUIRED = -1

    /**
     * Formats and writes [value] directly into [scratch] starting at [offset].
     * @return Bytes written into [scratch], or [FALLBACK_REQUIRED] if the caller should fall back.
     */
    fun writeDoubleDirect(
        value: Double,
        scratch: ByteArray,
        offset: Int,
    ): Int {
        if (!value.isFinite()) return FALLBACK_REQUIRED

        var position = offset
        var localValue = value

        if (value.toRawBits() < 0) {
            scratch[position++] = C.MINUS
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
                position,
                scratchEnd = position + LONG_DIRECT_SCRATCH_SPAN,
                writeDecimalZero = true
            ) - offset
        }

        // If number is massive or microscopic, delegate to native system
        if (
            localValue > MASSIVE_DOUBLE_THRESHOLD ||
            (localValue > 0.0 && localValue < MICROSCOPIC_DOUBLE_THRESHOLD)
        ) {
            return FALLBACK_REQUIRED
        }

        val intPart = localValue.toLong()
        val fracPart = localValue - intPart

        // roundToInt avoids the Double intermediate that round() returns
        var fracInt = (fracPart * PRECISION_MULTIPLIER).roundToInt()

        if (fracInt >= FRAC_LIMIT) {
            return writeLongDirect(
                intPart + 1,
                scratch,
                position,
                scratchEnd = position + LONG_DIRECT_SCRATCH_SPAN,
                writeDecimalZero = true
            ) - offset
        }

        position = writeLongDirect(
            intPart,
            scratch,
            position,
            scratchEnd = position + LONG_DIRECT_SCRATCH_SPAN,
            writeDecimalZero = false
        )

        scratch[position++] = C.DOT

        if (fracInt == 0) {
            scratch[position++] = C.ZERO
            return position - offset
        }

        var decimalsToPrint = MAX_DECIMALS
        // Trim trailing zeros: % instead of multiply-subtract
        while (decimalsToPrint > 1 && fracInt % 10 == 0) {
            fracInt /= 10
            decimalsToPrint--
        }

        position += decimalsToPrint
        var writePos = position - 1

        while (decimalsToPrint >= 2) {
            val quotient = fracInt / C.BASE_HUNDRED
            val lutOffset = (fracInt - (quotient * C.BASE_HUNDRED)) * DIGIT_PAIR_LUT_STRIDE
            C.DOUBLE_DIGIT_LUT.copyInto(
                scratch,
                writePos - 1,
                lutOffset,
                lutOffset + DIGIT_PAIR_WIDTH
            )
            writePos -= DIGIT_PAIR_WIDTH
            fracInt = quotient
            decimalsToPrint -= DIGIT_PAIR_WIDTH
        }
        if (decimalsToPrint == 1) {
            scratch[writePos] = (C.ZERO_INT + fracInt % 10).toByte()
        }

        return position - offset
    }

    /**
     * Formats and writes the given [Float] value directly into the [scratch] buffer starting at [offset].
     * Uses 7 decimal places of precision suitable for the single-precision Float type to avoid representation noise.
     *
     * @return Bytes written into [scratch], or [FALLBACK_REQUIRED] if the caller should fall back.
     */
    fun writeFloatDirect(
        value: Float,
        scratch: ByteArray,
        offset: Int,
    ): Int {
        if (!value.isFinite()) return FALLBACK_REQUIRED

        var pos = offset
        var localValue = value

        if (value.toRawBits() < 0) {
            scratch[pos++] = C.MINUS
            localValue = -localValue
        }

        val doubleVal = localValue.toDouble()
        // Fast path for small whole numbers
        if (
            doubleVal <= SMALL_WHOLE_THRESHOLD &&
            doubleVal % C.WHOLE_NUMBER_CHECK == C.ZERO_DOUBLE
        ) {
            return writeLongDirect(
                doubleVal.toLong(),
                scratch,
                pos,
                scratchEnd = pos + LONG_DIRECT_SCRATCH_SPAN,
                writeDecimalZero = true
            ) - offset
        }

        // If number is massive or microscopic, delegate to native system
        if (
            doubleVal > MASSIVE_DOUBLE_THRESHOLD ||
            (localValue > 0.0f && doubleVal < MICROSCOPIC_DOUBLE_THRESHOLD)
        ) {
            return FALLBACK_REQUIRED
        }

        val intPart = doubleVal.toLong()
        val fracPart = doubleVal - intPart

        // Float precision limit is 7 decimals (10^7)
        var fracInt = (fracPart * 10_000_000.0).roundToInt()

        if (fracInt >= 10_000_000L) {
            return writeLongDirect(
                intPart + 1,
                scratch,
                pos,
                scratchEnd = pos + LONG_DIRECT_SCRATCH_SPAN,
                writeDecimalZero = true
            ) - offset
        }

        pos = writeLongDirect(
            intPart,
            scratch,
            pos,
            scratchEnd = pos + LONG_DIRECT_SCRATCH_SPAN,
            writeDecimalZero = false
        )

        scratch[pos++] = C.DOT

        if (fracInt == 0) {
            scratch[pos++] = C.ZERO
            return pos - offset
        }

        var decimalsToPrint = 7
        while (decimalsToPrint > 1 && fracInt % 10 == 0) {
            fracInt /= 10
            decimalsToPrint--
        }

        pos += decimalsToPrint
        var writePos = pos - 1

        while (decimalsToPrint >= 2) {
            val quotient = fracInt / C.BASE_HUNDRED
            val lutOffset = (fracInt - (quotient * C.BASE_HUNDRED)) * DIGIT_PAIR_LUT_STRIDE
            C.DOUBLE_DIGIT_LUT.copyInto(
                scratch,
                writePos - 1,
                lutOffset,
                lutOffset + DIGIT_PAIR_WIDTH
            )
            writePos -= DIGIT_PAIR_WIDTH
            fracInt = quotient
            decimalsToPrint -= DIGIT_PAIR_WIDTH
        }
        if (decimalsToPrint == 1) {
            scratch[writePos] = (C.ZERO_INT + fracInt % 10).toByte()
        }

        return pos - offset
    }

    /**
     * Writes [value]'s ASCII digits into [scratch], extracting right-to-left via base-100
     * modulo and pre-computed ones/tens lookup tables, then block-copying into place.
     * @return The next write index in [scratch].
     */
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
            val quotient = localValue / C.BASE_HUNDRED
            val remainder = (localValue - (quotient * C.BASE_HUNDRED)).toInt()
            localValue = quotient
            scratch[--end] = GhostFormatUtils.DIGIT_ONES[remainder]
            scratch[--end] = GhostFormatUtils.DIGIT_TENS[remainder]
        }
        if (localValue >= C.BASE_TEN) {
            val remainder = localValue.toInt()
            scratch[--end] = GhostFormatUtils.DIGIT_ONES[remainder]
            scratch[--end] = GhostFormatUtils.DIGIT_TENS[remainder]
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

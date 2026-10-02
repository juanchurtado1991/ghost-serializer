package com.ghost.serialization.writer.common

import com.ghost.serialization.parser.common.GhostFormatUtils
import com.ghost.serialization.parser.common.json.finalizeParsedDouble
import com.ghost.serialization.parser.common.json.finalizeParsedFloat
import kotlin.math.roundToInt
import com.ghost.serialization.parser.common.constants.GhostJsonNumericLimits as NUM
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants as WR

/**
 * Zero-allocation ASCII formatter for [Double]/[Float], writing directly into a pre-allocated
 * [ByteArray] to bypass `Double.toString()` GC overhead. Handles up to [MAX_DECIMALS] decimal
 * places for values in `[1e-9, 1e9]`; anything outside that, non-finite, or too small returns
 * [FALLBACK_REQUIRED] so the caller falls back to a platform `toString()`.
 *
 * A fixed decimal count isn't inherently round-trip-safe (e.g. `0.1 + 0.2` prints as `"0.3"` at
 * 9 places, which reads back to a different Double). [writeDoubleDirect]/[writeFloatDirect] verify
 * the digits against [finalizeParsedDouble]/[finalizeParsedFloat] before committing, falling back
 * on mismatch.
 */
internal object GhostDoubleFormatter {

    /** `10^n` for `n` in `0..9`, used to fold the written int+fraction digits back into one mantissa. */
    private val POW10_LONG = longArrayOf(
        1L, 10L, 100L, 1_000L, 10_000L, 100_000L,
        1_000_000L, 10_000_000L, 100_000_000L, 1_000_000_000L,
    )

    private const val SMALL_WHOLE_THRESHOLD = 1_000_000_000.0
    private const val PRECISION_MULTIPLIER = 1_000_000_000.0
    private const val MICROSCOPIC_DOUBLE_THRESHOLD = 1e-9
    private const val MASSIVE_DOUBLE_THRESHOLD = 1e9
    private const val FRAC_LIMIT = 1_000_000_000L
    private const val MAX_DECIMALS = 9

    /** Float mirrors of [PRECISION_MULTIPLIER]/[FRAC_LIMIT]/[MAX_DECIMALS]: 7 decimals (10^7). */
    private const val FLOAT_PRECISION_MULTIPLIER = 10_000_000.0
    private const val FLOAT_FRAC_LIMIT = 10_000_000L
    private const val FLOAT_MAX_DECIMALS = 7

    /** Scratch span for [writeLongDirect], within FAST_BUF_SCRATCH_ZONE. */
    private const val LONG_DIRECT_SCRATCH_SPAN = 32

    private const val DIGIT_PAIR_LUT_STRIDE = 2
    private const val DIGIT_PAIR_WIDTH = 2

    /** Negative so call sites can keep `bytesWritten > 0` as the success check. */
    const val FALLBACK_REQUIRED = -1

    private fun doubleRoundTrips(intPart: Long, fracInt: Int, decimalsToPrint: Int, expected: Double): Boolean {
        // If the reader would truncate this many total digits, don't trust an idealized
        // reconstruction that skips that cap — bail out (a real reader's Double/Float.toString()
        // fallback never needs more than DOUBLE_PRECISION_LIMIT/FLOAT_PRECISION_LIMIT significant
        // digits to round-trip, so this only rejects cases the fixed-decimal fast path shouldn't
        // have tried to force into a fixed digit count anyway).
        if (significantIntDigits(intPart = intPart) + decimalsToPrint > NUM.DOUBLE_PRECISION_LIMIT) return false
        val mantissa = intPart * POW10_LONG[decimalsToPrint] + fracInt
        val reconstructed = finalizeParsedDouble(
            mantissa = mantissa,
            exponent = -decimalsToPrint,
            isNegative = false
        ) { error(it) }
        return reconstructed.toRawBits() == expected.toRawBits()
    }

    /** Same check as [doubleRoundTrips], against [finalizeParsedFloat] for the `Float` overload. */
    private fun floatRoundTrips(intPart: Long, fracInt: Int, decimalsToPrint: Int, expected: Float): Boolean {
        if (significantIntDigits(intPart = intPart) + decimalsToPrint > NUM.FLOAT_PRECISION_LIMIT) return false
        val mantissa = intPart * POW10_LONG[decimalsToPrint] + fracInt
        val reconstructed = finalizeParsedFloat(
            mantissa = mantissa,
            exponent = -decimalsToPrint,
            isNegative = false
        ) { error(it) }
        return reconstructed.toRawBits() == expected.toRawBits()
    }

    /** Digits of `intPart` counting toward a reader's `precisionLimit` (a sole "0" counts as 0). */
    private fun significantIntDigits(intPart: Long): Int {
        if (intPart == 0L) return 0
        var n = intPart
        var count = 0
        while (n > 0) {
            count++
            n /= 10
        }
        return count
    }

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
            scratch[position++] = TOK.MINUS
            localValue = -localValue
        }

        // Fast path for small whole numbers
        // (very common in metrics/coordinates)
        val isSmallWholeDouble = localValue <= SMALL_WHOLE_THRESHOLD &&
            localValue % NUM.WHOLE_NUMBER_CHECK == NUM.ZERO_DOUBLE
        if (isSmallWholeDouble) {
            return writeLongDirect(
                value = localValue.toLong(),
                scratch = scratch,
                offset = position,
                scratchEnd = position + LONG_DIRECT_SCRATCH_SPAN,
                writeDecimalZero = true
            ) - offset
        }

        // If number is massive or microscopic, delegate to native system
        val isOutOfFastRange = localValue > MASSIVE_DOUBLE_THRESHOLD ||
            (localValue > 0.0 && localValue < MICROSCOPIC_DOUBLE_THRESHOLD)
        if (isOutOfFastRange) {
            return FALLBACK_REQUIRED
        }

        val intPart = localValue.toLong()
        val fracPart = localValue - intPart

        // roundToInt avoids the Double intermediate that round() returns
        var fracInt = (fracPart * PRECISION_MULTIPLIER).roundToInt()

        if (fracInt >= FRAC_LIMIT) {
            return writeLongDirect(
                value = intPart + 1,
                scratch = scratch,
                offset = position,
                scratchEnd = position + LONG_DIRECT_SCRATCH_SPAN,
                writeDecimalZero = true
            ) - offset
        }

        position = writeLongDirect(
            value = intPart,
            scratch = scratch,
            offset = position,
            scratchEnd = position + LONG_DIRECT_SCRATCH_SPAN,
            writeDecimalZero = false
        )

        scratch[position++] = TOK.DOT

        if (fracInt == 0) {
            scratch[position++] = TOK.ZERO
            return position - offset
        }

        var decimalsToPrint = MAX_DECIMALS
        // Trim trailing zeros: % instead of multiply-subtract
        while (decimalsToPrint > 1 && fracInt % 10 == 0) {
            fracInt /= 10
            decimalsToPrint--
        }

        if (!doubleRoundTrips(
            intPart = intPart,
            fracInt = fracInt,
            decimalsToPrint = decimalsToPrint,
            expected = localValue
        )) {
            return FALLBACK_REQUIRED
        }

        position += decimalsToPrint
        var writePos = position - 1

        while (decimalsToPrint >= 2) {
            val quotient = fracInt / WR.BASE_HUNDRED
            val lutOffset = (fracInt - (quotient * WR.BASE_HUNDRED)) * DIGIT_PAIR_LUT_STRIDE
            WR.DOUBLE_DIGIT_LUT.copyInto(
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
            scratch[writePos] = (TOK.ZERO_INT + fracInt % 10).toByte()
        }

        return position - offset
    }

    /** Same as [writeDoubleDirect], with 7 decimal places (Float's precision). */
    fun writeFloatDirect(
        value: Float,
        scratch: ByteArray,
        offset: Int,
    ): Int {
        if (!value.isFinite()) return FALLBACK_REQUIRED

        var pos = offset
        var localValue = value

        if (value.toRawBits() < 0) {
            scratch[pos++] = TOK.MINUS
            localValue = -localValue
        }

        val doubleVal = localValue.toDouble()
        // Fast path for small whole numbers
        val isSmallWholeFloat = doubleVal <= SMALL_WHOLE_THRESHOLD &&
            doubleVal % NUM.WHOLE_NUMBER_CHECK == NUM.ZERO_DOUBLE
        if (isSmallWholeFloat) {
            return writeLongDirect(
                value = doubleVal.toLong(),
                scratch = scratch,
                offset = pos,
                scratchEnd = pos + LONG_DIRECT_SCRATCH_SPAN,
                writeDecimalZero = true
            ) - offset
        }

        // If number is massive or microscopic, delegate to native system
        val isOutOfFastRangeFloat = doubleVal > MASSIVE_DOUBLE_THRESHOLD ||
            (localValue > 0.0f && doubleVal < MICROSCOPIC_DOUBLE_THRESHOLD)
        if (isOutOfFastRangeFloat) {
            return FALLBACK_REQUIRED
        }

        val intPart = doubleVal.toLong()
        val fracPart = doubleVal - intPart

        var fracInt = (fracPart * FLOAT_PRECISION_MULTIPLIER).roundToInt()

        if (fracInt >= FLOAT_FRAC_LIMIT) {
            return writeLongDirect(
                value = intPart + 1,
                scratch = scratch,
                offset = pos,
                scratchEnd = pos + LONG_DIRECT_SCRATCH_SPAN,
                writeDecimalZero = true
            ) - offset
        }

        pos = writeLongDirect(
            value = intPart,
            scratch = scratch,
            offset = pos,
            scratchEnd = pos + LONG_DIRECT_SCRATCH_SPAN,
            writeDecimalZero = false
        )

        scratch[pos++] = TOK.DOT

        if (fracInt == 0) {
            scratch[pos++] = TOK.ZERO
            return pos - offset
        }

        var decimalsToPrint = FLOAT_MAX_DECIMALS
        while (decimalsToPrint > 1 && fracInt % 10 == 0) {
            fracInt /= 10
            decimalsToPrint--
        }

        if (!floatRoundTrips(
            intPart = intPart,
            fracInt = fracInt,
            decimalsToPrint = decimalsToPrint,
            expected = localValue
        )) {
            return FALLBACK_REQUIRED
        }

        pos += decimalsToPrint
        var writePos = pos - 1

        while (decimalsToPrint >= 2) {
            val quotient = fracInt / WR.BASE_HUNDRED
            val lutOffset = (fracInt - (quotient * WR.BASE_HUNDRED)) * DIGIT_PAIR_LUT_STRIDE
            WR.DOUBLE_DIGIT_LUT.copyInto(
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
            scratch[writePos] = (TOK.ZERO_INT + fracInt % 10).toByte()
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
            scratch[offset] = TOK.ZERO
            if (writeDecimalZero) {
                scratch[offset + 1] = TOK.DOT
                scratch[offset + 2] = TOK.ZERO
                return offset + 3
            }
            return offset + 1
        }

        var localValue = value
        // Write digits backward into the scratch zone at the end of our reserved area.
        // scratchEnd is always offset + 32, safely within FAST_BUF_SCRATCH_ZONE.
        var end = scratchEnd

        while (localValue >= WR.BASE_HUNDRED) {
            val quotient = localValue / WR.BASE_HUNDRED
            val remainder = (localValue - (quotient * WR.BASE_HUNDRED)).toInt()
            localValue = quotient
            scratch[--end] = GhostFormatUtils.DIGIT_ONES[remainder]
            scratch[--end] = GhostFormatUtils.DIGIT_TENS[remainder]
        }
        if (localValue >= WR.BASE_TEN) {
            val remainder = localValue.toInt()
            scratch[--end] = GhostFormatUtils.DIGIT_ONES[remainder]
            scratch[--end] = GhostFormatUtils.DIGIT_TENS[remainder]
        } else {
            scratch[--end] = (localValue.toInt() + WR.ASCII_OFFSET).toByte()
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
            scratch[nextOffset++] = TOK.DOT
            scratch[nextOffset++] = TOK.ZERO
        }

        return nextOffset
    }
}

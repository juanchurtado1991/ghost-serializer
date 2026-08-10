package com.ghost.serialization.writer.common

import com.ghost.serialization.parser.common.GhostJsonConstants.DOUBLE_DIGIT_LUT
import com.ghost.serialization.parser.common.GhostJsonConstants.DOUBLE_DIGIT_LUT_CHARS
import com.ghost.serialization.parser.common.GhostJsonConstants.HUNDRED_LONG
import com.ghost.serialization.parser.common.GhostJsonConstants.LONG_SCRATCH_SIZE
import com.ghost.serialization.parser.common.GhostJsonConstants.MINUS
import com.ghost.serialization.parser.common.GhostJsonConstants.MINUS_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.TEN_LONG
import com.ghost.serialization.parser.common.GhostJsonConstants.ZERO_INT

/**
 * Shared decimal digit emission for writer long values.
 *
 * Writes digits backward into a scratch buffer and returns the start index of the
 * written span. Callers handle [Long.MIN_VALUE] and sink-specific flush.
 */
internal object GhostWriterLongDigits {

    /**
     * Emits decimal digits of non-negative [absoluteValue] into [scratch] ending at
     * [scratchEnd]. When [negative] is true, prefixes `'-'`.
     *
     * @return start index such that `scratch[start until scratchEnd]` is the decimal text
     */
    fun writeDigitsBytes(
        absoluteValue: Long,
        negative: Boolean,
        scratch: ByteArray,
        scratchEnd: Int = LONG_SCRATCH_SIZE,
    ): Int {
        var pos = scratchEnd
        var localValue = absoluteValue

        while (localValue >= HUNDRED_LONG) {
            val rem = (localValue % HUNDRED_LONG).toInt() * 2
            scratch[--pos] = DOUBLE_DIGIT_LUT[rem + 1] // ones
            scratch[--pos] = DOUBLE_DIGIT_LUT[rem]     // tens
            localValue /= HUNDRED_LONG
        }

        if (localValue < TEN_LONG) {
            scratch[--pos] = (ZERO_INT + localValue.toInt()).toByte()
        } else {
            val rem = localValue.toInt() * 2
            scratch[--pos] = DOUBLE_DIGIT_LUT[rem + 1] // ones
            scratch[--pos] = DOUBLE_DIGIT_LUT[rem]     // tens
        }

        if (negative) {
            scratch[--pos] = MINUS
        }

        return pos
    }

    /**
     * Char-array twin of [writeDigitsBytes] for the string JSON writer.
     */
    fun writeDigitsChars(
        absoluteValue: Long,
        negative: Boolean,
        scratch: CharArray,
        scratchEnd: Int = LONG_SCRATCH_SIZE,
    ): Int {
        var pos = scratchEnd
        var localValue = absoluteValue

        while (localValue >= HUNDRED_LONG) {
            val lutIndex = (localValue % HUNDRED_LONG).toInt() * 2
            scratch[--pos] = DOUBLE_DIGIT_LUT_CHARS[lutIndex + 1]
            scratch[--pos] = DOUBLE_DIGIT_LUT_CHARS[lutIndex]
            localValue /= HUNDRED_LONG
        }

        if (localValue < TEN_LONG) {
            scratch[--pos] = (ZERO_INT + localValue.toInt()).toChar()
        } else {
            val lutIndex = localValue.toInt() * 2
            scratch[--pos] = DOUBLE_DIGIT_LUT_CHARS[lutIndex + 1]
            scratch[--pos] = DOUBLE_DIGIT_LUT_CHARS[lutIndex]
        }

        if (negative) {
            scratch[--pos] = MINUS_INT.toChar()
        }

        return pos
    }

    /**
     * Emits digits of a strictly positive [absoluteValue] one digit at a time into
     * [scratch] ending at [scratch.size]. Used by YAML writers (sign written separately).
     *
     * @return start index such that `scratch[start until scratch.size]` is the decimal text
     */
    fun writePositiveDigitsBytes(
        absoluteValue: Long,
        scratch: ByteArray,
    ): Int {
        var remaining = absoluteValue
        var pos = scratch.size
        while (remaining > 0L) {
            val digit = (remaining % TEN_LONG).toInt()
            scratch[--pos] = (ZERO_INT + digit).toByte()
            remaining /= TEN_LONG
        }
        return pos
    }
}

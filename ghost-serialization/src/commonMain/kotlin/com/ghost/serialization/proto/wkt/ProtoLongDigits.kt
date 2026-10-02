package com.ghost.serialization.proto.wkt

import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants as WR
import com.ghost.serialization.proto.GhostProtoConstants as PC

/**
 * Decimal formatting of [Long] for the proto3 WKT serializers (quoted int64, Duration seconds).
 *
 * Operates in negative space throughout (never negates the full magnitude) so that
 * `Long.MIN_VALUE` round-trips correctly: `-Long.MIN_VALUE` overflows back to `Long.MIN_VALUE`
 * in two's complement, which previously corrupted the output to "-0" for that boundary value.
 * [writeLongToBytes] is `inline` so [formatLong] and `formatDuration` each keep the digit loops in
 * their own body, as when the algorithm was copied into both.
 */
internal fun formatLong(
    value: Long
): String {
    val outputBuffer = ByteArray(size = PC.LONG_BUFFER_SIZE)
    val endOffset = writeLongToBytes(
        buffer = outputBuffer,
        startOffset = 0,
        value = value
    )
    return outputBuffer.decodeToString(
        startIndex = 0,
        endIndex = endOffset
    )
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun writeLongToBytes(
    buffer: ByteArray,
    startOffset: Int,
    value: Long
): Int {
    var position = startOffset
    val isNegative = value < 0
    if (isNegative) {
        buffer[position++] = TOK.CHAR_HYPHEN.code.toByte()
    }
    var tempValue = if (isNegative) value else -value
    var digitCount = 1
    while (tempValue <= -WR.BASE_TEN) {
        digitCount++
        tempValue /= WR.BASE_TEN
    }
    var divisor = 1L
    var digitIndex = digitCount - 1
    while (digitIndex > 0) {
        divisor *= WR.BASE_TEN
        digitIndex--
    }
    tempValue = if (isNegative) value else -value
    while (divisor > 0) {
        val digit = (-(tempValue / divisor)).toInt()
        buffer[position++] = (digit + TOK.ZERO_INT).toByte()
        tempValue %= divisor
        divisor /= WR.BASE_TEN
    }
    return position
}

@file:OptIn(InternalGhostApi::class)
@file:Suppress("NOTHING_TO_INLINE")

package com.ghost.protobuf.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.GhostJsonFlatReader
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.GhostJsonStringReader
import com.ghost.serialization.parser.nextString
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.GhostJsonStringWriter
import com.ghost.serialization.writer.GhostJsonWriter
import com.ghost.serialization.parser.GhostJsonConstants as C



/**
 * A Duration represents a signed, fixed-length span of time represented
 * as a count of seconds and fractions of seconds at nanosecond
 * resolution. It is independent of any calendar and concepts like "day"
 * or "month".
 */
data class ProtoDuration(val seconds: Long, val nanos: Int) {
    init {
        if ((seconds > 0 && nanos < 0) || (seconds < 0 && nanos > 0)) {
            throw IllegalArgumentException(C.ERR_DURATION_SIGN)
        }
    }
}

object ProtoDurationSerializer : GhostSerializer<ProtoDuration> {
    override val typeName: String get() = C.WKT_DURATION_TYPE

    override fun serialize(writer: GhostJsonWriter, value: ProtoDuration) {
        writer.value(formatDuration(value))
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoDuration) {
        writer.value(formatDuration(value))
    }

    override fun serialize(writer: GhostJsonStringWriter, value: ProtoDuration) {
        writer.value(formatDuration(value))
    }

    override fun deserialize(reader: GhostJsonReader): ProtoDuration {
        return parseDuration(reader.nextString())
    }

    override fun deserialize(reader: GhostJsonFlatReader): ProtoDuration {
        return parseDuration(reader.nextString())
    }

    override fun deserialize(reader: GhostJsonStringReader): ProtoDuration {
        return parseDuration(reader.nextString())
    }
}

internal fun parseDuration(durationString: String): ProtoDuration {
    if (durationString.length < 2 || durationString[durationString.length - 1] != C.CHAR_S) throw IllegalArgumentException(
        C.ERR_DURATION_SUFFIX
    )
    val dotIndex = durationString.indexOf(C.CHAR_DOT)
    val limitIndex = durationString.length - 1
    if (dotIndex == -1) {
        var seconds = 0L
        var isNegative = false
        var start = 0
        if (durationString[0] == C.CHAR_HYPHEN) {
            isNegative = true
            start = 1
        }
        var index = start
        while (index < limitIndex) {
            seconds = seconds * C.BASE_TEN + (durationString[index].code - C.ZERO_INT)
            index++
        }
        return ProtoDuration(if (isNegative) -seconds else seconds, 0)
    } else {
        var seconds = 0L
        var isNegative = false
        var start = 0
        if (durationString[0] == C.CHAR_HYPHEN) {
            isNegative = true
            start = 1
        }
        var index = start
        while (index < dotIndex) {
            seconds = seconds * C.BASE_TEN + (durationString[index].code - C.ZERO_INT)
            index++
        }
        val fracDigits = limitIndex - (dotIndex + 1)
        var fractionValue = 0
        var fractionIndex = dotIndex + 1
        while (fractionIndex < limitIndex) {
            fractionValue =
                fractionValue * C.BASE_TEN + (durationString[fractionIndex].code - C.ZERO_INT)
            fractionIndex++
        }
        var multiplier = 1
        var multiplierIndex = 0
        while (multiplierIndex < C.NANOS_DIGITS - fracDigits) {
            multiplier *= C.BASE_TEN
            multiplierIndex++
        }
        val nanos = fractionValue * multiplier
        return ProtoDuration(
            if (isNegative) -seconds else seconds,
            if (isNegative) -nanos else nanos
        )
    }
}

// Operates in negative space throughout (never negates the full magnitude) so that
// Long.MIN_VALUE round-trips correctly: -Long.MIN_VALUE overflows back to Long.MIN_VALUE
// in two's complement, which previously corrupted the output to "-0" for that boundary value.
private fun writeLongToBytes(buffer: ByteArray, startOffset: Int, value: Long): Int {
    var position = startOffset
    val isNegative = value < 0
    if (isNegative) {
        buffer[position++] = C.CHAR_HYPHEN.code.toByte()
    }
    // Count digits
    var tempValue = if (isNegative) value else -value
    var digitCount = 1
    while (tempValue <= -C.BASE_TEN) {
        digitCount++
        tempValue /= C.BASE_TEN
    }
    // Write digits in reverse
    var divisor = 1L
    var digitIndex = digitCount - 1
    while (digitIndex > 0) {
        divisor *= C.BASE_TEN
        digitIndex--
    }
    tempValue = if (isNegative) value else -value
    while (divisor > 0) {
        val digit = (-(tempValue / divisor)).toInt()
        buffer[position++] = (digit + C.ZERO_INT).toByte()
        tempValue %= divisor
        divisor /= C.BASE_TEN
    }
    return position
}

internal fun formatDuration(duration: ProtoDuration): String {
    val seconds = duration.seconds
    val nanoseconds = kotlin.math.abs(duration.nanos)
    val bytes = ByteArray(C.DUR_BUFFER_SIZE)
    var pos = 0
    // writeLongToBytes(0) has no sign of its own to carry — for a duration under one second
    // (seconds == 0), the sign lives entirely in nanos and must be written explicitly, or it's
    // lost: ProtoDuration(0, -1) would otherwise format as "0.000000001s" instead of
    // "-0.000000001s".
    if (seconds == 0L && duration.nanos < 0) {
        bytes[pos++] = C.CHAR_HYPHEN.code.toByte()
    }
    pos = writeLongToBytes(bytes, pos, seconds)
    if (nanoseconds != 0) {
        bytes[pos++] = C.CHAR_DOT.code.toByte()
        pos = writeNanosFraction(bytes, pos, nanoseconds)
    }
    bytes[pos++] = C.CHAR_S.code.toByte()
    return bytes.decodeToString(0, pos)
}

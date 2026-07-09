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
 * A Timestamp represents a point in time independent of any time zone or local
 * calendar, encoded as a count of seconds and fractions of seconds at
 * nanosecond resolution.
 */
data class ProtoTimestamp(val seconds: Long, val nanos: Int)

/**
 * Serializer for [ProtoTimestamp].
 */
object ProtoTimestampSerializer : GhostSerializer<ProtoTimestamp> {
    override val typeName: String get() = C.WKT_TIMESTAMP_TYPE

    override fun serialize(writer: GhostJsonWriter, value: ProtoTimestamp) {
        writer.value(formatTimestamp(value))
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoTimestamp) {
        writer.value(formatTimestamp(value))
    }

    override fun serialize(writer: GhostJsonStringWriter, value: ProtoTimestamp) {
        writer.value(formatTimestamp(value))
    }

    override fun deserialize(reader: GhostJsonReader): ProtoTimestamp {
        return parseTimestamp(reader.nextString())
    }

    override fun deserialize(reader: GhostJsonFlatReader): ProtoTimestamp {
        return parseTimestamp(reader.nextString())
    }

    override fun deserialize(reader: GhostJsonStringReader): ProtoTimestamp {
        return parseTimestamp(reader.nextString())
    }
}

internal inline fun String.parseDecimalAt(start: Int, end: Int): Int {
    var result = 0
    var index = start
    while (index < end) {
        val code = this[index].code
        if ((code - C.ZERO_INT) !in 0..9) throw IllegalArgumentException(C.ERR_MALFORMED_DIGIT)
        result = result * C.BASE_TEN + (code - C.ZERO_INT)
        index++
    }
    return result
}

// Writes `value` zero-padded to `width` digits directly into the ByteArray.
internal fun writePaddedInt(buffer: ByteArray, startOffset: Int, value: Int, width: Int): Int {
    var position = startOffset
    var digitCount = 1
    var threshold = C.BASE_TEN
    while (threshold <= value && digitCount < width) {
        digitCount++
        threshold *= C.BASE_TEN
    }
    var remainingValue = value
    var actualDigits = digitCount
    while (threshold <= remainingValue) {
        actualDigits++
        threshold *= C.BASE_TEN
    }
    var paddingCount = width - actualDigits
    while (paddingCount > 0) {
        buffer[position++] = C.CHAR_ZERO.code.toByte()
        paddingCount--
    }
    var divisor = 1
    var digitIndex = actualDigits - 1
    while (digitIndex > 0) {
        divisor *= C.BASE_TEN
        digitIndex--
    }
    remainingValue = value
    while (divisor > 0) {
        val digit = remainingValue / divisor
        buffer[position++] = (digit + C.ZERO_INT).toByte()
        remainingValue %= divisor
        divisor /= C.BASE_TEN
    }
    return position
}

// Appends nanos as fractional digits. Proto3 JSON mandates exactly 0, 3, 6, or 9 fractional
// digits (never an arbitrary trim) — e.g. 450_000_000 ns must render as ".450", not ".45".
internal fun writeNanosFraction(buffer: ByteArray, startOffset: Int, nanos: Int): Int {
    val width: Int
    val scale: Int
    if (nanos % C.NANOS_PER_MILLI == 0) {
        width = 3
        scale = C.NANOS_PER_MILLI
    } else if (nanos % C.NANOS_PER_MICRO == 0) {
        width = 6
        scale = C.NANOS_PER_MICRO
    } else {
        width = 9
        scale = 1
    }
    return writePaddedInt(buffer, startOffset, nanos / scale, width)
}

// Zero-allocation calendar converter using Hatcher/Richards algorithm
// Ranges validated: 0001-01-01T00:00:00Z to 9999-12-31T23:59:59Z
internal fun parseTimestamp(timestampString: String): ProtoTimestamp {
    if (timestampString.length < C.TS_MIN_LENGTH) {
        throw IllegalArgumentException(C.ERR_TIMESTAMP_SHORT)
    }
    val year = timestampString.parseDecimalAt(C.TS_YEAR_START, C.TS_YEAR_END)
    if (timestampString[C.TS_YEAR_END] != C.CHAR_HYPHEN) {
        throw IllegalArgumentException(C.ERR_TIMESTAMP_YEAR_HYPHEN)
    }
    val month = timestampString.parseDecimalAt(C.TS_MONTH_START, C.TS_MONTH_END)
    if (timestampString[C.TS_MONTH_END] != C.CHAR_HYPHEN) {
        throw IllegalArgumentException(C.ERR_TIMESTAMP_MONTH_HYPHEN)
    }
    val day = timestampString.parseDecimalAt(C.TS_DAY_START, C.TS_DAY_END)
    if (timestampString[C.TS_DAY_END] != C.CHAR_T_UPPER && timestampString[C.TS_DAY_END] != C.CHAR_T) {
        throw IllegalArgumentException(C.ERR_TIMESTAMP_T)
    }
    val hour = timestampString.parseDecimalAt(C.TS_HOUR_START, C.TS_HOUR_END)
    if (timestampString[C.TS_HOUR_END] != C.CHAR_COLON) {
        throw IllegalArgumentException(C.ERR_TIMESTAMP_HOUR_COLON)
    }
    val minute = timestampString.parseDecimalAt(C.TS_MIN_START, C.TS_MIN_END)
    if (timestampString[C.TS_MIN_END] != C.CHAR_COLON) {
        throw IllegalArgumentException(C.ERR_TIMESTAMP_MINUTE_COLON)
    }
    val second = timestampString.parseDecimalAt(C.TS_SEC_START, C.TS_SEC_END)

    var nanos = 0
    var nextIndex = C.TS_SEC_END
    if (timestampString[C.TS_SEC_END] == C.CHAR_DOT) {
        var endIndex = C.TS_SEC_END + 1
        val len = timestampString.length
        while (endIndex < len) {
            val code = timestampString[endIndex].code
            if ((code - C.ZERO_INT) !in 0..9) {
                break
            }
            endIndex++
        }
        val fracDigits = endIndex - (C.TS_SEC_END + 1)
        var fractionValue = 0
        var fractionIndex = C.TS_SEC_END + 1
        while (fractionIndex < endIndex) {
            fractionValue =
                fractionValue * C.BASE_TEN + (timestampString[fractionIndex].code - C.ZERO_INT)
            fractionIndex++
        }
        var multiplier = 1
        var multiplierIndex = 0
        while (multiplierIndex < C.NANOS_DIGITS - fracDigits) {
            multiplier *= C.BASE_TEN
            multiplierIndex++
        }
        nanos = fractionValue * multiplier
        nextIndex = endIndex
    }

    if (nextIndex >= timestampString.length) {
        throw IllegalArgumentException(C.ERR_TIMESTAMP_TZ)
    }

    var offsetSec = 0
    if (timestampString[nextIndex] != C.CHAR_Z_UPPER && timestampString[nextIndex] != C.CHAR_Z_LOWER) {
        if (
            nextIndex + C.TS_TZ_OFFSET_LEN != timestampString.length ||
            (timestampString[nextIndex] != C.CHAR_PLUS && timestampString[nextIndex] != C.CHAR_HYPHEN)
        ) {
            throw IllegalArgumentException(C.ERR_TIMESTAMP_TZ_SUPPORT)
        }
        val tzSign = if (timestampString[nextIndex] == C.CHAR_HYPHEN) -1 else 1
        val tzHour = timestampString.parseDecimalAt(nextIndex + 1, nextIndex + 3)
        val tzMin = timestampString.parseDecimalAt(nextIndex + 4, nextIndex + 6)
        offsetSec = tzSign * (tzHour * 3600 + tzMin * 60)
    }

    val epochSeconds = dateToEpochSeconds(year, month, day, hour, minute, second) - offsetSec
    return ProtoTimestamp(epochSeconds, nanos)
}

// Convert YYYY-MM-DD HH:MM:SS to Epoch Seconds (UTC)
internal fun dateToEpochSeconds(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
    second: Int
): Long {
    val yearAdjustment = (if (month <= 2) year - 1 else year).toLong()
    val monthAdjustment = (if (month <= 2) month + 12 else month).toLong()
    val era =
        (if (yearAdjustment >= 0) yearAdjustment else yearAdjustment - (C.HINNANT_ERA_YEARS - 1)) / C.HINNANT_ERA_YEARS
    val yearOfEra = yearAdjustment - era * C.HINNANT_ERA_YEARS
    val dayOfYear = (153 * (monthAdjustment - 3) + 2) / 5 + day - 1
    val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
    val days = era * C.HINNANT_DAYS_PER_ERA + dayOfEra - C.HINNANT_EPOCH_OFFSET
    return days * C.SECONDS_PER_DAY + hour * C.SECONDS_PER_HOUR + minute * C.SECONDS_PER_MINUTE + second
}

internal fun formatTimestamp(timestamp: ProtoTimestamp): String {
    val seconds = timestamp.seconds
    var days = seconds / C.SECONDS_PER_DAY
    var remSeconds = (seconds % C.SECONDS_PER_DAY).toInt()
    if (remSeconds < 0) {
        days -= 1
        remSeconds += C.SECONDS_PER_DAY.toInt()
    }

    val hour = remSeconds / C.SECONDS_PER_HOUR.toInt()
    val remMinutes = remSeconds % C.SECONDS_PER_HOUR.toInt()
    val minute = remMinutes / C.SECONDS_PER_MINUTE.toInt()
    val second = remMinutes % C.SECONDS_PER_MINUTE.toInt()

    val zeroDay = days + C.HINNANT_EPOCH_OFFSET
    val era =
        (if (zeroDay >= 0) zeroDay else zeroDay - C.HINNANT_DAYS_CYCLE_ERA) / C.HINNANT_DAYS_PER_ERA
    val dayOfEra = (zeroDay - era * C.HINNANT_DAYS_PER_ERA).toInt()
    val yearOfEra =
        (dayOfEra - dayOfEra / C.HINNANT_DAYS_CYCLE_4 + dayOfEra / C.HINNANT_DAYS_CYCLE_100 - dayOfEra / C.HINNANT_DAYS_CYCLE_ERA) / 365
    val y = yearOfEra + era * C.HINNANT_ERA_YEARS
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val monthPosition = (5 * dayOfYear + 2) / 153
    val day = dayOfYear - (153 * monthPosition + 2) / 5 + 1
    val month = if (monthPosition < 10) monthPosition + 3 else monthPosition - 9
    val year = (if (month <= 2) y + 1 else y).toInt()

    val bytes = ByteArray(C.TS_BUFFER_SIZE)
    var pos = 0
    pos = writePaddedInt(bytes, pos, year, 4)
    bytes[pos++] = C.CHAR_HYPHEN.code.toByte()
    pos = writePaddedInt(bytes, pos, month, 2)
    bytes[pos++] = C.CHAR_HYPHEN.code.toByte()
    pos = writePaddedInt(bytes, pos, day, 2)
    bytes[pos++] = C.CHAR_T_UPPER.code.toByte()
    pos = writePaddedInt(bytes, pos, hour, 2)
    bytes[pos++] = C.CHAR_COLON.code.toByte()
    pos = writePaddedInt(bytes, pos, minute, 2)
    bytes[pos++] = C.CHAR_COLON.code.toByte()
    pos = writePaddedInt(bytes, pos, second, 2)

    if (timestamp.nanos > 0) {
        bytes[pos++] = C.CHAR_DOT.code.toByte()
        pos = writeNanosFraction(bytes, pos, timestamp.nanos)
    }
    bytes[pos++] = C.CHAR_Z_UPPER.code.toByte()
    return bytes.decodeToString(0, pos)
}

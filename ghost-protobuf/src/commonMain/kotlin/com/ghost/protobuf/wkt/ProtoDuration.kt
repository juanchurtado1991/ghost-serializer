@file:OptIn(InternalGhostApi::class)

package com.ghost.protobuf.wkt

import com.ghost.serialization.parser.GhostProtoJsonFlatReader
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

// --- Timestamp ---

data class ProtoTimestamp(val seconds: Long, val nanos: Int)

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

private inline fun String.parseDecimalAt(start: Int, end: Int): Int {
    var result = 0
    var i = start
    while (i < end) {
        val code = this[i].code
        if ((code - C.ZERO_INT) !in 0..9) throw IllegalArgumentException(C.ERR_MALFORMED_DIGIT)
        result = result * C.BASE_TEN + (code - C.ZERO_INT)
        i++
    }
    return result
}

// Writes `value` zero-padded to `width` digits directly into the ByteArray.
private fun writePaddedInt(buf: ByteArray, startOffset: Int, value: Int, width: Int): Int {
    var pos = startOffset
    var digitCount = 1
    var threshold = C.BASE_TEN
    while (threshold <= value && digitCount < width) {
        digitCount++
        threshold *= C.BASE_TEN
    }
    var v = value
    var actualDigits = digitCount
    while (threshold <= v) {
        actualDigits++
        threshold *= C.BASE_TEN
    }
    var pad = width - actualDigits
    while (pad > 0) {
        buf[pos++] = C.CHAR_ZERO.code.toByte()
        pad--
    }
    var divisor = 1
    var d = actualDigits - 1
    while (d > 0) {
        divisor *= C.BASE_TEN
        d--
    }
    v = value
    while (divisor > 0) {
        val digit = v / divisor
        buf[pos++] = (digit + C.ZERO_INT).toByte()
        v %= divisor
        divisor /= C.BASE_TEN
    }
    return pos
}

// Appends nanos as fractional digits. Proto3 JSON mandates exactly 0, 3, 6, or 9 fractional
// digits (never an arbitrary trim) — e.g. 450_000_000 ns must render as ".450", not ".45".
private fun writeNanosFraction(buf: ByteArray, startOffset: Int, nanos: Int): Int {
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
    return writePaddedInt(buf, startOffset, nanos / scale, width)
}

// Zero-allocation calendar converter using Hatcher/Richards algorithm
// Ranges validated: 0001-01-01T00:00:00Z to 9999-12-31T23:59:59Z
internal fun parseTimestamp(str: String): ProtoTimestamp {
    if (str.length < C.TS_MIN_LENGTH) throw IllegalArgumentException(C.ERR_TIMESTAMP_SHORT)
    val year = str.parseDecimalAt(C.TS_YEAR_START, C.TS_YEAR_END)
    if (str[C.TS_YEAR_END] != C.CHAR_HYPHEN) throw IllegalArgumentException(C.ERR_TIMESTAMP_YEAR_HYPHEN)
    val month = str.parseDecimalAt(C.TS_MONTH_START, C.TS_MONTH_END)
    if (str[C.TS_MONTH_END] != C.CHAR_HYPHEN) throw IllegalArgumentException(C.ERR_TIMESTAMP_MONTH_HYPHEN)
    val day = str.parseDecimalAt(C.TS_DAY_START, C.TS_DAY_END)
    if (str[C.TS_DAY_END] != C.CHAR_T_UPPER && str[C.TS_DAY_END] != C.CHAR_T) throw IllegalArgumentException(C.ERR_TIMESTAMP_T)
    val hour = str.parseDecimalAt(C.TS_HOUR_START, C.TS_HOUR_END)
    if (str[C.TS_HOUR_END] != C.CHAR_COLON) throw IllegalArgumentException(C.ERR_TIMESTAMP_HOUR_COLON)
    val minute = str.parseDecimalAt(C.TS_MIN_START, C.TS_MIN_END)
    if (str[C.TS_MIN_END] != C.CHAR_COLON) throw IllegalArgumentException(C.ERR_TIMESTAMP_MINUTE_COLON)
    val second = str.parseDecimalAt(C.TS_SEC_START, C.TS_SEC_END)

    var nanos = 0
    var nextIdx = C.TS_SEC_END
    if (str[C.TS_SEC_END] == C.CHAR_DOT) {
        var endIdx = C.TS_SEC_END + 1
        val len = str.length
        while (endIdx < len) {
            val code = str[endIdx].code
            if ((code - C.ZERO_INT) !in 0..9) {
                break
            }
            endIdx++
        }
        val fracDigits = endIdx - (C.TS_SEC_END + 1)
        var fracVal = 0
        var fi = C.TS_SEC_END + 1
        while (fi < endIdx) {
            fracVal = fracVal * C.BASE_TEN + (str[fi].code - C.ZERO_INT)
            fi++
        }
        var multiplier = 1
        var mi = 0
        while (mi < C.NANOS_DIGITS - fracDigits) {
            multiplier *= C.BASE_TEN
            mi++
        }
        nanos = fracVal * multiplier
        nextIdx = endIdx
    }

    if (nextIdx >= str.length) throw IllegalArgumentException(C.ERR_TIMESTAMP_TZ)

    var offsetSec = 0
    if (str[nextIdx] != C.CHAR_Z_UPPER && str[nextIdx] != C.CHAR_Z_LOWER) {
        if (nextIdx + C.TS_TZ_OFFSET_LEN != str.length || (str[nextIdx] != '+' && str[nextIdx] != C.CHAR_HYPHEN)) {
            throw IllegalArgumentException(C.ERR_TIMESTAMP_TZ_SUPPORT)
        }
        val tzSign = if (str[nextIdx] == C.CHAR_HYPHEN) -1 else 1
        val tzHour = str.parseDecimalAt(nextIdx + 1, nextIdx + 3)
        val tzMin = str.parseDecimalAt(nextIdx + 4, nextIdx + 6)
        offsetSec = tzSign * (tzHour * 3600 + tzMin * 60)
    }

    val epochSeconds = dateToEpochSeconds(year, month, day, hour, minute, second) - offsetSec
    return ProtoTimestamp(epochSeconds, nanos)
}

// Convert YYYY-MM-DD HH:MM:SS to Epoch Seconds (UTC)
private fun dateToEpochSeconds(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long {
    val y = (if (month <= 2) year - 1 else year).toLong()
    val m = (if (month <= 2) month + 12 else month).toLong()
    val era = (if (y >= 0) y else y - (C.HINNANT_ERA_YEARS - 1)) / C.HINNANT_ERA_YEARS
    val yoe = y - era * C.HINNANT_ERA_YEARS
    val doy = (153 * (m - 3) + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    val days = era * C.HINNANT_DAYS_PER_ERA + doe - C.HINNANT_EPOCH_OFFSET
    return days * C.SECONDS_PER_DAY + hour * C.SECONDS_PER_HOUR + minute * C.SECONDS_PER_MINUTE + second
}

internal fun formatTimestamp(t: ProtoTimestamp): String {
    val seconds = t.seconds
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

    val z = days + C.HINNANT_EPOCH_OFFSET
    val era = (if (z >= 0) z else z - C.HINNANT_DAYS_CYCLE_ERA) / C.HINNANT_DAYS_PER_ERA
    val doe = (z - era * C.HINNANT_DAYS_PER_ERA).toInt()
    val yoe = (doe - doe / C.HINNANT_DAYS_CYCLE_4 + doe / C.HINNANT_DAYS_CYCLE_100 - doe / C.HINNANT_DAYS_CYCLE_ERA) / 365
    val y = yoe + era * C.HINNANT_ERA_YEARS
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = doy - (153 * mp + 2) / 5 + 1
    val month = if (mp < 10) mp + 3 else mp - 9
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

    if (t.nanos > 0) {
        bytes[pos++] = C.CHAR_DOT.code.toByte()
        pos = writeNanosFraction(bytes, pos, t.nanos)
    }
    bytes[pos++] = C.CHAR_Z_UPPER.code.toByte()
    return bytes.decodeToString(0, pos)
}

// --- Duration ---

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

internal fun parseDuration(str: String): ProtoDuration {
    if (str.length < 2 || str[str.length - 1] != C.CHAR_S) throw IllegalArgumentException(C.ERR_DURATION_SUFFIX)
    val dotIdx = str.indexOf(C.CHAR_DOT)
    val limitIdx = str.length - 1
    if (dotIdx == -1) {
        var seconds = 0L
        var isNeg = false
        var start = 0
        if (str[0] == C.CHAR_HYPHEN) {
            isNeg = true
            start = 1
        }
        var i = start
        while (i < limitIdx) {
            seconds = seconds * C.BASE_TEN + (str[i].code - C.ZERO_INT)
            i++
        }
        return ProtoDuration(if (isNeg) -seconds else seconds, 0)
    } else {
        var seconds = 0L
        var isNeg = false
        var start = 0
        if (str[0] == C.CHAR_HYPHEN) {
            isNeg = true
            start = 1
        }
        var i = start
        while (i < dotIdx) {
            seconds = seconds * C.BASE_TEN + (str[i].code - C.ZERO_INT)
            i++
        }
        val fracDigits = limitIdx - (dotIdx + 1)
        var fracVal = 0
        var fi = dotIdx + 1
        while (fi < limitIdx) {
            fracVal = fracVal * C.BASE_TEN + (str[fi].code - C.ZERO_INT)
            fi++
        }
        var multiplier = 1
        var mi = 0
        while (mi < C.NANOS_DIGITS - fracDigits) {
            multiplier *= C.BASE_TEN
            mi++
        }
        val nanos = fracVal * multiplier
        return ProtoDuration(if (isNeg) -seconds else seconds, if (isNeg) -nanos else nanos)
    }
}

// Operates in negative space throughout (never negates the full magnitude) so that
// Long.MIN_VALUE round-trips correctly: -Long.MIN_VALUE overflows back to Long.MIN_VALUE
// in two's complement, which previously corrupted the output to "-0" for that boundary value.
private fun writeLongToBytes(buf: ByteArray, startOffset: Int, value: Long): Int {
    var pos = startOffset
    val isNeg = value < 0
    if (isNeg) {
        buf[pos++] = C.CHAR_HYPHEN.code.toByte()
    }
    // Count digits
    var temp = if (isNeg) value else -value
    var digitCount = 1
    while (temp <= -C.BASE_TEN) {
        digitCount++
        temp /= C.BASE_TEN
    }
    // Write digits in reverse
    var divisor = 1L
    var d = digitCount - 1
    while (d > 0) {
        divisor *= C.BASE_TEN
        d--
    }
    temp = if (isNeg) value else -value
    while (divisor > 0) {
        val digit = (-(temp / divisor)).toInt()
        buf[pos++] = (digit + C.ZERO_INT).toByte()
        temp %= divisor
        divisor /= C.BASE_TEN
    }
    return pos
}

internal fun formatDuration(d: ProtoDuration): String {
    val sec = d.seconds
    val ns = kotlin.math.abs(d.nanos)
    val bytes = ByteArray(C.DUR_BUFFER_SIZE)
    var pos = 0
    pos = writeLongToBytes(bytes, pos, sec)
    if (ns != 0) {
        bytes[pos++] = C.CHAR_DOT.code.toByte()
        pos = writeNanosFraction(bytes, pos, ns)
    }
    bytes[pos++] = C.CHAR_S.code.toByte()
    return bytes.decodeToString(0, pos)
}

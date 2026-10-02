@file:Suppress("NOTHING_TO_INLINE")

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants as WR
import com.ghost.serialization.proto.GhostProtoConstants as PC

/**
 * Parses/formats [ProtoTimestamp]'s RFC 3339 JSON string form. The calendar math is Howard
 * Hinnant's `days_from_civil`/`civil_from_days` (constant names are `HINNANT_*`), valid over
 * 0001-01-01T00:00:00Z .. 9999-12-31T23:59:59Z, chosen for correctness across the proleptic
 * Gregorian range rather than the usual libc epoch-only tricks.
 */

/** Converts a UTC civil date/time (`year`-`month`-`day` `hour`:`minute`:`second`) to epoch seconds. */
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
    val eraBaseYear = if (yearAdjustment >= 0) yearAdjustment else yearAdjustment - (PC.HINNANT_ERA_YEARS - 1)
    val era = eraBaseYear / PC.HINNANT_ERA_YEARS
    val yearOfEra = yearAdjustment - era * PC.HINNANT_ERA_YEARS
    val dayOfYear = (PC.HINNANT_MONTH_COEFF * (monthAdjustment - 3) + 2) / 5 + day - 1
    val dayOfEra = yearOfEra * PC.DAYS_PER_YEAR + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
    val days = era * PC.HINNANT_DAYS_PER_ERA + dayOfEra - PC.HINNANT_EPOCH_OFFSET
    return days * PC.SECONDS_PER_DAY + hour * PC.SECONDS_PER_HOUR + minute * PC.SECONDS_PER_MINUTE + second
}

internal fun formatTimestamp(
    timestamp: ProtoTimestamp
): String {
    val seconds = timestamp.seconds
    var days = seconds / PC.SECONDS_PER_DAY
    var remSeconds = (seconds % PC.SECONDS_PER_DAY).toInt()
    if (remSeconds < 0) {
        days -= 1
        remSeconds += PC.SECONDS_PER_DAY.toInt()
    }

    val hour = remSeconds / PC.SECONDS_PER_HOUR.toInt()
    val remMinutes = remSeconds % PC.SECONDS_PER_HOUR.toInt()
    val minute = remMinutes / PC.SECONDS_PER_MINUTE.toInt()
    val second = remMinutes % PC.SECONDS_PER_MINUTE.toInt()

    val zeroDay = days + PC.HINNANT_EPOCH_OFFSET
    val eraBaseDay = if (zeroDay >= 0) zeroDay else zeroDay - PC.HINNANT_DAYS_CYCLE_ERA
    val era = eraBaseDay / PC.HINNANT_DAYS_PER_ERA
    val dayOfEra = (zeroDay - era * PC.HINNANT_DAYS_PER_ERA).toInt()
    val yearOfEraNumerator = dayOfEra - dayOfEra / PC.HINNANT_DAYS_CYCLE_4 +
        dayOfEra / PC.HINNANT_DAYS_CYCLE_100 - dayOfEra / PC.HINNANT_DAYS_CYCLE_ERA
    val yearOfEra = yearOfEraNumerator / PC.DAYS_PER_YEAR
    val y = yearOfEra + era * PC.HINNANT_ERA_YEARS
    val dayOfYear = dayOfEra - (PC.DAYS_PER_YEAR * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val monthPosition = (5 * dayOfYear + 2) / PC.HINNANT_MONTH_COEFF
    val day = dayOfYear - (PC.HINNANT_MONTH_COEFF * monthPosition + 2) / 5 + 1
    val month = if (monthPosition < 10) monthPosition + 3 else monthPosition - 9
    val year = (if (month <= 2) y + 1 else y).toInt()

    val bytes = ByteArray(size = PC.TS_BUFFER_SIZE)
    var pos = 0
    pos = writePaddedInt(buffer = bytes, startOffset = pos, value = year, width = 4)
    bytes[pos++] = TOK.CHAR_HYPHEN.code.toByte()
    pos = writePaddedInt(buffer = bytes, startOffset = pos, value = month, width = 2)
    bytes[pos++] = TOK.CHAR_HYPHEN.code.toByte()
    pos = writePaddedInt(buffer = bytes, startOffset = pos, value = day, width = 2)
    bytes[pos++] = TOK.CHAR_T_UPPER.code.toByte()
    pos = writePaddedInt(buffer = bytes, startOffset = pos, value = hour, width = 2)
    bytes[pos++] = TOK.CHAR_COLON.code.toByte()
    pos = writePaddedInt(buffer = bytes, startOffset = pos, value = minute, width = 2)
    bytes[pos++] = TOK.CHAR_COLON.code.toByte()
    pos = writePaddedInt(buffer = bytes, startOffset = pos, value = second, width = 2)

    if (timestamp.nanos > 0) {
        bytes[pos++] = TOK.CHAR_DOT.code.toByte()
        pos = writeNanosFraction(buffer = bytes, startOffset = pos, nanos = timestamp.nanos)
    }
    bytes[pos++] = TOK.CHAR_Z_UPPER.code.toByte()
    return bytes.decodeToString(
        startIndex = 0,
        endIndex = pos
    )
}

internal fun String.parseDecimalAt(start: Int, end: Int): Int {
    var result = 0
    var index = start
    while (index < end) {
        val code = this[index].code
        if ((code - TOK.ZERO_INT) !in 0..9) {
            throw IllegalArgumentException(PC.ERR_MALFORMED_DIGIT)
        }
        result = result * WR.BASE_TEN + (code - TOK.ZERO_INT)
        index++
    }
    return result
}

internal fun parseTimestamp(timestampString: String): ProtoTimestamp {
    if (timestampString.length < PC.TS_MIN_LENGTH) {
        throw IllegalArgumentException(PC.ERR_TIMESTAMP_SHORT)
    }
    val year = timestampString.parseDecimalAt(start = PC.TS_YEAR_START, end = PC.TS_YEAR_END)
    if (timestampString[PC.TS_YEAR_END] != TOK.CHAR_HYPHEN) {
        throw IllegalArgumentException(PC.ERR_TIMESTAMP_YEAR_HYPHEN)
    }
    val month = timestampString.parseDecimalAt(start = PC.TS_MONTH_START, end = PC.TS_MONTH_END)
    if (timestampString[PC.TS_MONTH_END] != TOK.CHAR_HYPHEN) {
        throw IllegalArgumentException(PC.ERR_TIMESTAMP_MONTH_HYPHEN)
    }
    val day = timestampString.parseDecimalAt(start = PC.TS_DAY_START, end = PC.TS_DAY_END)
    if (timestampString[PC.TS_DAY_END] != TOK.CHAR_T_UPPER && timestampString[PC.TS_DAY_END] != TOK.CHAR_T) {
        throw IllegalArgumentException(PC.ERR_TIMESTAMP_T)
    }
    val hour = timestampString.parseDecimalAt(start = PC.TS_HOUR_START, end = PC.TS_HOUR_END)
    if (timestampString[PC.TS_HOUR_END] != TOK.CHAR_COLON) {
        throw IllegalArgumentException(PC.ERR_TIMESTAMP_HOUR_COLON)
    }
    val minute = timestampString.parseDecimalAt(start = PC.TS_MIN_START, end = PC.TS_MIN_END)
    if (timestampString[PC.TS_MIN_END] != TOK.CHAR_COLON) {
        throw IllegalArgumentException(PC.ERR_TIMESTAMP_MINUTE_COLON)
    }
    val second = timestampString.parseDecimalAt(start = PC.TS_SEC_START, end = PC.TS_SEC_END)

    var nanos = 0
    var nextIndex = PC.TS_SEC_END
    if (timestampString[PC.TS_SEC_END] == TOK.CHAR_DOT) {
        var endIndex = PC.TS_SEC_END + 1
        val len = timestampString.length
        while (endIndex < len) {
            val code = timestampString[endIndex].code
            if ((code - TOK.ZERO_INT) !in 0..9) {
                break
            }
            endIndex++
        }
        val fracDigits = endIndex - (PC.TS_SEC_END + 1)
        var fractionValue = 0
        var fractionIndex = PC.TS_SEC_END + 1
        while (fractionIndex < endIndex) {
            fractionValue =
                fractionValue * WR.BASE_TEN + (timestampString[fractionIndex].code - TOK.ZERO_INT)
            fractionIndex++
        }
        var multiplier = 1
        var multiplierIndex = 0
        while (multiplierIndex < PC.NANOS_DIGITS - fracDigits) {
            multiplier *= WR.BASE_TEN
            multiplierIndex++
        }
        nanos = fractionValue * multiplier
        nextIndex = endIndex
    }

    if (nextIndex >= timestampString.length) {
        throw IllegalArgumentException(PC.ERR_TIMESTAMP_TZ)
    }

    var offsetSec = 0
    if (timestampString[nextIndex] != TOK.CHAR_Z_UPPER && timestampString[nextIndex] != TOK.CHAR_Z_LOWER) {
        val isMalformedTimezoneOffset = nextIndex + PC.TS_TZ_OFFSET_LEN != timestampString.length ||
            (timestampString[nextIndex] != TOK.CHAR_PLUS && timestampString[nextIndex] != TOK.CHAR_HYPHEN)
        if (isMalformedTimezoneOffset) {
            throw IllegalArgumentException(PC.ERR_TIMESTAMP_TZ_SUPPORT)
        }
        val tzSign = if (timestampString[nextIndex] == TOK.CHAR_HYPHEN) -1 else 1
        val tzHour = timestampString.parseDecimalAt(start = nextIndex + 1, end = nextIndex + 3)
        val tzMin = timestampString.parseDecimalAt(start = nextIndex + 4, end = nextIndex + 6)
        offsetSec = tzSign * (
            tzHour * PC.SECONDS_PER_HOUR.toInt() + tzMin * PC.SECONDS_PER_MINUTE.toInt()
        )
    }

    val epochSeconds = dateToEpochSeconds(
        year = year,
        month = month,
        day = day,
        hour = hour,
        minute = minute,
        second = second
    ) - offsetSec
    return ProtoTimestamp(seconds = epochSeconds, nanos = nanos)
}

/**
 * Proto3 JSON mandates exactly 0, 3, 6, or 9 fractional digits, never an arbitrary trim
 * (e.g. 450_000_000 ns must render as ".450", not ".45").
 */
internal fun writeNanosFraction(
    buffer: ByteArray,
    startOffset: Int,
    nanos: Int
): Int {
    val width: Int
    val scale: Int
    if (nanos % PC.NANOS_PER_MILLI == 0) {
        width = 3
        scale = PC.NANOS_PER_MILLI
    } else if (nanos % PC.NANOS_PER_MICRO == 0) {
        width = 6
        scale = PC.NANOS_PER_MICRO
    } else {
        width = 9
        scale = 1
    }
    return writePaddedInt(
        buffer = buffer,
        startOffset = startOffset,
        value = nanos / scale,
        width = width
    )
}

internal fun writePaddedInt(
    buffer: ByteArray,
    startOffset: Int,
    value: Int,
    width: Int
): Int {
    var position = startOffset
    var digitCount = 1
    var threshold = WR.BASE_TEN
    while (threshold <= value && digitCount < width) {
        digitCount++
        threshold *= WR.BASE_TEN
    }
    var remainingValue = value
    var actualDigits = digitCount
    while (threshold <= remainingValue) {
        actualDigits++
        threshold *= WR.BASE_TEN
    }
    var paddingCount = width - actualDigits
    while (paddingCount > 0) {
        buffer[position++] = TOK.CHAR_ZERO.code.toByte()
        paddingCount--
    }
    var divisor = 1
    var digitIndex = actualDigits - 1
    while (digitIndex > 0) {
        divisor *= WR.BASE_TEN
        digitIndex--
    }
    remainingValue = value
    while (divisor > 0) {
        val digit = remainingValue / divisor
        buffer[position++] = (digit + TOK.ZERO_INT).toByte()
        remainingValue %= divisor
        divisor /= WR.BASE_TEN
    }
    return position
}

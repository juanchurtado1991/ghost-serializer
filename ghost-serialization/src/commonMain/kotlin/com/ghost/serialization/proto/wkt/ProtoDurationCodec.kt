package com.ghost.serialization.proto.wkt

import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants as WR
import com.ghost.serialization.proto.GhostProtoConstants as PC

/** Parses/formats [ProtoDuration]'s `"<seconds>[.<fraction>]s"` JSON string form. */

internal fun formatDuration(duration: ProtoDuration): String {
    val seconds = duration.seconds
    val nanoseconds = kotlin.math.abs(n = duration.nanos)
    val bytes = ByteArray(size = PC.DUR_BUFFER_SIZE)
    var pos = 0
    // writeLongToBytes(0) has no sign of its own to carry — for a duration under one second
    // (seconds == 0), the sign lives entirely in nanos and must be written explicitly, or it's
    // lost: ProtoDuration(0, -1) would otherwise format as "0.000000001s" instead of
    // "-0.000000001s".
    if (seconds == 0L && duration.nanos < 0) {
        bytes[pos++] = TOK.CHAR_HYPHEN.code.toByte()
    }
    pos = writeLongToBytes(buffer = bytes, startOffset = pos, value = seconds)
    if (nanoseconds != 0) {
        bytes[pos++] = TOK.CHAR_DOT.code.toByte()
        pos = writeNanosFraction(buffer = bytes, startOffset = pos, nanos = nanoseconds)
    }
    bytes[pos++] = TOK.CHAR_S.code.toByte()
    return bytes.decodeToString(
        startIndex = 0,
        endIndex = pos
    )
}

internal fun parseDuration(durationString: String): ProtoDuration {
    val hasValidSuffix = durationString.length >= PC.DUR_MIN_LENGTH &&
        durationString[durationString.length - 1] == TOK.CHAR_S
    if (!hasValidSuffix) {
        throw IllegalArgumentException(PC.ERR_DURATION_SUFFIX)
    }
    val limitIndex = durationString.length - 1
    val isNegative = durationString[0] == TOK.CHAR_HYPHEN
    var index = if (isNegative || durationString[0] == TOK.CHAR_PLUS) 1 else 0
    val secondsStart = index
    var seconds = 0L
    while (index < limitIndex && isAsciiDigit(char = durationString[index])) {
        seconds = seconds * WR.BASE_TEN + (durationString[index].code - TOK.ZERO_INT)
        if (seconds > PC.DUR_MAX_SECONDS) {
            throw IllegalArgumentException(PC.ERR_DURATION_RANGE)
        }
        index++
    }
    if (index == secondsStart) {
        throw IllegalArgumentException(PC.ERR_DURATION_FORMAT)
    }
    var nanos = 0
    if (index < limitIndex) {
        if (durationString[index] != TOK.CHAR_DOT) {
            throw IllegalArgumentException(PC.ERR_DURATION_FORMAT)
        }
        val fractionStart = index + 1
        val fractionDigits = limitIndex - fractionStart
        if (fractionDigits > PC.NANOS_DIGITS) {
            throw IllegalArgumentException(PC.ERR_FRACTION_DIGITS)
        }
        nanos = durationString.parseDecimalAt(start = fractionStart, end = limitIndex) *
            nanosScale(fractionDigits = fractionDigits)
    }
    return ProtoDuration(
        seconds = if (isNegative) -seconds else seconds,
        nanos = if (isNegative) -nanos else nanos
    )
}

private fun isAsciiDigit(
    char: Char
): Boolean = char.code - TOK.ZERO_INT in 0 until WR.BASE_TEN

internal fun nanosScale(
    fractionDigits: Int
): Int {
    var multiplier = 1
    repeat(PC.NANOS_DIGITS - fractionDigits) { multiplier *= WR.BASE_TEN }
    return multiplier
}

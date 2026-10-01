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
    val dotIndex = durationString.indexOf(char = TOK.CHAR_DOT)
    val limitIndex = durationString.length - 1
    if (dotIndex == -1) {
        var seconds = 0L
        var isNegative = false
        var start = 0
        if (durationString[0] == TOK.CHAR_HYPHEN) {
            isNegative = true
            start = 1
        }
        var index = start
        while (index < limitIndex) {
            seconds = seconds * WR.BASE_TEN + (durationString[index].code - TOK.ZERO_INT)
            index++
        }
        return ProtoDuration(
            seconds = if (isNegative) -seconds else seconds,
            nanos = 0
        )
    } else {
        var seconds = 0L
        var isNegative = false
        var start = 0
        if (durationString[0] == TOK.CHAR_HYPHEN) {
            isNegative = true
            start = 1
        }
        var index = start
        while (index < dotIndex) {
            seconds = seconds * WR.BASE_TEN + (durationString[index].code - TOK.ZERO_INT)
            index++
        }
        val fracDigits = limitIndex - (dotIndex + 1)
        var fractionValue = 0
        var fractionIndex = dotIndex + 1
        while (fractionIndex < limitIndex) {
            fractionValue =
                fractionValue * WR.BASE_TEN + (durationString[fractionIndex].code - TOK.ZERO_INT)
            fractionIndex++
        }
        var multiplier = 1
        var multiplierIndex = 0
        while (multiplierIndex < PC.NANOS_DIGITS - fracDigits) {
            multiplier *= WR.BASE_TEN
            multiplierIndex++
        }
        val nanos = fractionValue * multiplier
        return ProtoDuration(
            seconds = if (isNegative) -seconds else seconds,
            nanos = if (isNegative) -nanos else nanos
        )
    }
}

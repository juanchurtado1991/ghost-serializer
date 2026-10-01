    @file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser.proto

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.bytes.extensions.nextDoubleExtension
import com.ghost.serialization.parser.bytes.extensions.nextFloatExtension
import com.ghost.serialization.parser.bytes.extensions.nextLongExtension
import com.ghost.serialization.parser.common.json.JsonReaderOptions
import com.ghost.serialization.parser.common.constants.GhostJsonErrorMessages as EM
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants as SCN
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK
import com.ghost.serialization.proto.GhostProtoConstants as PC

internal fun GhostProtoJsonFlatReader.nextProtoDouble(): Double = readProtoFloatingValue(
    nan = Double.NaN,
    positiveInfinity = Double.POSITIVE_INFINITY,
    negativeInfinity = Double.NEGATIVE_INFINITY,
    parse = { nextDoubleExtension() }
)

internal fun GhostProtoJsonFlatReader.nextProtoFloat(): Float = readProtoFloatingValue(
    nan = Float.NaN,
    positiveInfinity = Float.POSITIVE_INFINITY,
    negativeInfinity = Float.NEGATIVE_INFINITY,
    parse = { nextFloatExtension() }
)

internal fun GhostProtoJsonFlatReader.nextProtoInt64(): Long {
    val token = peekNextToken()
    return if (token == TOK.QUOTE_INT) {
        withStringCoercion { nextLongExtension() }
    } else {
        nextDoubleExtension().toLong()
    }
}

internal fun GhostProtoJsonFlatReader.readProtoEnum(options: JsonReaderOptions): Int {
    val token = peekNextToken()
    if (token == TOK.QUOTE_INT) {
        val index = selectString(options = options)
        if (index != SCN.MATCH_NONE) {
            return index
        }
        throwError(EM.ERR_UNKNOWN_ENUM)
    } else {
        return nextInt()
    }
}

internal fun GhostProtoJsonFlatReader.readProtoUInt32(): Long {
    val value = nextProtoInt64()
    if (value < 0L || value > PC.PROTO_UINT32_MAX) {
        throwError(PC.ERR_PROTO_UINT32_OVERFLOW)
    }
    return value
}

/**
 * Full `uint64` range read. The canonical proto3 JSON form is always a quoted decimal string
 * (parsed directly as [ULong], no range limit); a bare JSON number falls back to the existing
 * int64 path, which is only safe for values within [Long.MAX_VALUE].
 */
internal fun GhostProtoJsonFlatReader.readProtoUInt64(): ULong {
    val token = peekNextToken()
    return if (token == TOK.QUOTE_INT) {
        nextString().toULong()
    } else {
        nextProtoInt64().toULong()
    }
}

/**
 * Shared quoted-NaN/Infinity/-Infinity handling for [nextProtoFloat] and [nextProtoDouble] —
 * canonical proto3 JSON quotes these three special values. Falls through to [parse] (with
 * string coercion enabled while quoted) for every other numeric form.
 */
private inline fun <T> GhostProtoJsonFlatReader.readProtoFloatingValue(
    nan: T,
    positiveInfinity: T,
    negativeInfinity: T,
    parse: () -> T
): T {
    val token = peekNextToken()
    if (token == TOK.QUOTE_INT) {
        val start = position + 1
        if (start + 3 <= limit) {
            val b0 = getByte(start)
            val b1 = getByte(start + 1)
            val b2 = getByte(start + 2)

            val isQuotedNaN = start + PC.NAN_QUOTED_LEN - 1 <= limit &&
                getByte(start + PC.NAN_QUOTED_LEN - 2) == TOK.QUOTE_INT &&
                b0 == PC.N_UPPER_BYTE_INT && b1 == PC.A_LOWER_BYTE_INT && b2 == PC.N_UPPER_BYTE_INT
            if (isQuotedNaN) {
                position = start + PC.NAN_QUOTED_LEN - 1
                nextTokenByte = SCN.RESET_TOKEN_BYTE
                return nan
            }

            val isQuotedInfinity = start + PC.INFINITY_QUOTED_LEN - 1 <= limit &&
                getByte(start + PC.INFINITY_QUOTED_LEN - 2) == TOK.QUOTE_INT &&
                b0 == PC.I_BYTE_INT && matchInfinityBytes(start = start)
            if (isQuotedInfinity) {
                position = start + PC.INFINITY_QUOTED_LEN - 1
                nextTokenByte = SCN.RESET_TOKEN_BYTE
                return positiveInfinity
            }

            val isQuotedNegativeInfinity = start + PC.NEG_INFINITY_QUOTED_LEN - 1 <= limit &&
                getByte(start + PC.NEG_INFINITY_QUOTED_LEN - 2) == TOK.QUOTE_INT &&
                b0 == TOK.MINUS_INT && getByte(start + 1) == PC.I_BYTE_INT && matchInfinityBytes(start = start + 1)
            if (isQuotedNegativeInfinity) {
                position = start + PC.NEG_INFINITY_QUOTED_LEN - 1
                nextTokenByte = SCN.RESET_TOKEN_BYTE
                return negativeInfinity
            }
        }
        return withStringCoercion(parse = parse)
    }
    return parse()
}

/** Caller already checked 'I'; verifies the remaining 7 bytes of "Infinity". */
private fun GhostProtoJsonFlatReader.matchInfinityBytes(start: Int): Boolean {
    return getByte(start + 1) == PC.N_LOWER_BYTE_INT &&
            getByte(start + 2) == PC.F_LOWER_BYTE_INT &&
            getByte(start + 3) == PC.I_LOWER_BYTE_INT &&
            getByte(start + 4) == PC.N_LOWER_BYTE_INT &&
            getByte(start + 5) == PC.I_LOWER_BYTE_INT &&
            getByte(start + 6) == PC.T_LOWER_BYTE_INT &&
            getByte(start + 7) == PC.Y_LOWER_BYTE_INT
}

/** Runs [parse] with `coerceStringsToNumbers` temporarily forced on, then restores it. */
private inline fun <T> GhostProtoJsonFlatReader.withStringCoercion(parse: () -> T): T {
    val prev = coerceStringsToNumbers
    coerceStringsToNumbers = true
    try {
        return parse()
    } finally {
        coerceStringsToNumbers = prev
    }
}

@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser.proto

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.bytes.extensions.nextDoubleExtension
import com.ghost.serialization.parser.bytes.extensions.nextIntExtension
import com.ghost.serialization.parser.bytes.extensions.nextLongExtension
import com.ghost.serialization.parser.bytes.extensions.nextULongExtension
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK
import com.ghost.serialization.proto.GhostProtoConstants as PC

/**
 * proto3 JSON `int32`/`int64`/`uint64` reads, quoted or bare. Per spec a zero fraction is accepted ("1.0"
 * parses as 1) and a nonzero one throws ("1.5"): a value containing `.` goes through the double
 * parser and must convert exactly, while every other value takes the exact integer path (digits
 * and exponents, overflow throws). The `.` scan looks ahead without allocating.
 */
internal fun GhostProtoJsonFlatReader.nextProtoInt32(): Int = readProtoIntegral(
    parseIntegral = { nextIntExtension() },
    narrowExactDouble = { value -> value.toInt().takeIf { it.toDouble() == value } }
)

internal fun GhostProtoJsonFlatReader.nextProtoInt64(): Long = readProtoIntegral(
    parseIntegral = { nextLongExtension() },
    narrowExactDouble = { value -> value.toLong().takeIf { it.toDouble() == value } }
)

internal fun GhostProtoJsonFlatReader.readProtoUInt64(): ULong = readProtoIntegral(
    parseIntegral = { nextULongExtension() },
    narrowExactDouble = { value -> value.toULong().takeIf { value >= 0.0 && it.toDouble() == value } }
)

private fun GhostProtoJsonFlatReader.hasFractionDotAhead(
    isQuoted: Boolean
): Boolean {
    var scanPos = position
    if (isQuoted) scanPos++
    if (scanPos < limit && getByte(scanPos) == TOK.MINUS_INT) {
        scanPos++
    }
    while (scanPos < limit) {
        val tokenByte = getByte(scanPos)
        if (tokenByte == TOK.DOT_INT) {
            return true
        }
        val isValueTerminator = tokenByte == TOK.QUOTE_INT ||
            tokenByte == TOK.COMMA_INT ||
            tokenByte == TOK.CLOSE_OBJ_INT ||
            tokenByte == TOK.CLOSE_ARR_INT ||
            tokenByte <= TOK.SPACE_INT
        if (isValueTerminator) {
            return false
        }
        scanPos++
    }
    return false
}

private inline fun <T> GhostProtoJsonFlatReader.readProtoIntegral(
    parseIntegral: () -> T,
    narrowExactDouble: (Double) -> T?
): T {
    val isQuoted = peekNextToken() == TOK.QUOTE_INT
    val hasDot = hasFractionDotAhead(isQuoted = isQuoted)

    val previousCoercion = coerceStringsToNumbers
    if (isQuoted) {
        coerceStringsToNumbers = true
    }
    try {
        if (hasDot) {
            return narrowExactDouble(nextDoubleExtension()) ?: throwError(PC.ERR_PROTO_FRACTIONAL_INT)
        }
        return parseIntegral()
    } finally {
        coerceStringsToNumbers = previousCoercion
    }
}

@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser.proto

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.bytes.extensions.nextDoubleExtension
import com.ghost.serialization.parser.bytes.extensions.nextIntExtension
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK
import com.ghost.serialization.proto.GhostProtoConstants as PC

/**
 * Reads a proto3 JSON `int32` field. Per spec, a nonzero fractional portion is invalid
 * ("1.0" parses as 1, "1.5" throws) — scans ahead for a `.` without allocating to decide
 * whether to parse as a double (and validate the fraction) or go straight through the int
 * fast path.
 */
internal fun GhostProtoJsonFlatReader.nextProtoInt32(): Int {
    val token = peekNextToken()
    val isQuoted = token == TOK.QUOTE_INT

    var hasDot = false
    var scanPos = position
    if (isQuoted) scanPos++
    if (scanPos < limit && getByte(scanPos) == TOK.MINUS_INT) {
        scanPos++
    }
    while (scanPos < limit) {
        val tokenByte = getByte(scanPos)
        if (tokenByte == TOK.DOT_INT) {
            hasDot = true
            break
        }
        val isValueTerminator = tokenByte == TOK.QUOTE_INT ||
            tokenByte == TOK.COMMA_INT ||
            tokenByte == TOK.CLOSE_OBJ_INT ||
            tokenByte == TOK.CLOSE_ARR_INT ||
            tokenByte <= TOK.SPACE_INT
        if (isValueTerminator) {
            break
        }
        scanPos++
    }

    val prev = coerceStringsToNumbers
    if (isQuoted) {
        coerceStringsToNumbers = true
    }
    try {
        if (hasDot) {
            val doubleValue = nextDoubleExtension()
            val intValue = doubleValue.toInt()
            if (doubleValue != intValue.toDouble()) {
                throwError(PC.ERR_PROTO_FRACTIONAL_INT)
            }
            return intValue
        }
        return nextIntExtension()
    } finally {
        coerceStringsToNumbers = prev
    }
}

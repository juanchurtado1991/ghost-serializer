@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser.proto

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.bytes.nextDoubleExtension
import com.ghost.serialization.parser.bytes.nextIntExtension
import com.ghost.serialization.parser.common.GhostJsonConstants as C


internal fun GhostProtoJsonFlatReader.nextProtoInt32(): Int {
    // Spec: "Values with nonzero fractional portions are not allowed"
    // E.g. "1.0" ok, "1.5" error.
    // Peek to see if there is a dot.
    val token = peekNextToken()
    val isQuoted = token == C.QUOTE_INT

    // We parse via double if there's a dot, otherwise standard nextInt()
    // To check if there's a dot without allocations:
    var hasDot = false
    var scanPos = position
    if (isQuoted) scanPos++
    // Skip optional minus
    if (scanPos < limit && getByte(scanPos) == C.MINUS_INT) {
        scanPos++
    }
    while (scanPos < limit) {
        val tokenByte = getByte(scanPos)
        if (tokenByte == C.DOT_INT) {
            hasDot = true
            break
        }
        if (
            tokenByte == C.QUOTE_INT ||
            tokenByte == C.COMMA_INT ||
            tokenByte == C.CLOSE_OBJ_INT ||
            tokenByte == C.CLOSE_ARR_INT ||
            tokenByte <= C.SPACE_INT
        ) {
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
                throwError(C.ERR_PROTO_FRACTIONAL_INT)
            }
            return intValue
        }
        return nextIntExtension()
    } finally {
        coerceStringsToNumbers = prev
    }
}

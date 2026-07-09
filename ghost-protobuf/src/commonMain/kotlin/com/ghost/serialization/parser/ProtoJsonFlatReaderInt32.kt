@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.GhostJsonConstants as C

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
        val b = getByte(scanPos)
        if (b == C.DOT_INT) {
            hasDot = true
            break
        }
        if (
            b == C.QUOTE_INT ||
            b == C.COMMA_INT ||
            b == C.CLOSE_OBJ_INT ||
            b == C.CLOSE_ARR_INT ||
            b <= C.SPACE_INT
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
            val d = this.nextDoubleExtension()
            val i = d.toInt()
            if (d != i.toDouble()) {
                throwError(C.ERR_PROTO_FRACTIONAL_INT)
            }
            return i
        }
        return this.nextIntExtension()
    } finally {
        coerceStringsToNumbers = prev
    }
}

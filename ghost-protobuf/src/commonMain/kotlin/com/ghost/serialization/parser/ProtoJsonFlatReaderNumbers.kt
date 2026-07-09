@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.JsonReaderOptions
import com.ghost.serialization.parser.nextKey
import com.ghost.serialization.parser.nextString
import com.ghost.serialization.parser.skipValue
import com.ghost.serialization.parser.GhostJsonConstants as C

internal fun GhostProtoJsonFlatReader.nextProtoFloat(): Float {
    val token = peekNextToken()
    if (token == C.QUOTE_INT) {
        val start = position + 1
        if (start + 3 <= limit) {
            val b0 = getByte(start)
            val b1 = getByte(start + 1)
            val b2 = getByte(start + 2)
            // check closing quote
            if (start + C.NAN_QUOTED_LEN - 1 <= limit && getByte(start + C.NAN_QUOTED_LEN - 2) == C.QUOTE_INT) {
                if (b0 == C.N_UPPER_BYTE_INT && b1 == C.A_LOWER_BYTE_INT && b2 == C.N_UPPER_BYTE_INT) { // "NaN"
                    position = start + C.NAN_QUOTED_LEN - 1
                    nextTokenByte = C.RESET_TOKEN_BYTE
                    return Float.NaN
                }
            }
            if (start + C.INFINITY_QUOTED_LEN - 1 <= limit && getByte(start + C.INFINITY_QUOTED_LEN - 2) == C.QUOTE_INT) {
                if (b0 == C.I_BYTE_INT && matchInfinityBytes(start)) { // "Infinity"
                    position = start + C.INFINITY_QUOTED_LEN - 1
                    nextTokenByte = C.RESET_TOKEN_BYTE
                    return Float.POSITIVE_INFINITY
                }
            }
            if (start + C.NEG_INFINITY_QUOTED_LEN - 1 <= limit && getByte(start + C.NEG_INFINITY_QUOTED_LEN - 2) == C.QUOTE_INT) {
                if (b0 == C.MINUS_INT && getByte(start + 1) == C.I_BYTE_INT && matchInfinityBytes(start + 1)) { // "-Infinity"
                    position = start + C.NEG_INFINITY_QUOTED_LEN - 1
                    nextTokenByte = C.RESET_TOKEN_BYTE
                    return Float.NEGATIVE_INFINITY
                }
            }
        }
        val prev = coerceStringsToNumbers
        coerceStringsToNumbers = true
        try {
            return this.nextFloatExtension()
        } finally {
            coerceStringsToNumbers = prev
        }
    }
    return this.nextFloatExtension()
}

internal fun GhostProtoJsonFlatReader.nextProtoDouble(): Double {
    val token = peekNextToken()
    if (token == C.QUOTE_INT) {
        val start = position + 1
        if (start + 3 <= limit) {
            val b0 = getByte(start)
            val b1 = getByte(start + 1)
            val b2 = getByte(start + 2)
            if (start + C.NAN_QUOTED_LEN - 1 <= limit && getByte(start + C.NAN_QUOTED_LEN - 2) == C.QUOTE_INT) {
                if (b0 == C.N_UPPER_BYTE_INT && b1 == C.A_LOWER_BYTE_INT && b2 == C.N_UPPER_BYTE_INT) { // "NaN"
                    position = start + C.NAN_QUOTED_LEN - 1
                    nextTokenByte = C.RESET_TOKEN_BYTE
                    return Double.NaN
                }
            }
            if (start + C.INFINITY_QUOTED_LEN - 1 <= limit && getByte(start + C.INFINITY_QUOTED_LEN - 2) == C.QUOTE_INT) {
                if (b0 == C.I_BYTE_INT && matchInfinityBytes(start)) { // "Infinity"
                    position = start + C.INFINITY_QUOTED_LEN - 1
                    nextTokenByte = C.RESET_TOKEN_BYTE
                    return Double.POSITIVE_INFINITY
                }
            }
            if (start + C.NEG_INFINITY_QUOTED_LEN - 1 <= limit && getByte(start + C.NEG_INFINITY_QUOTED_LEN - 2) == C.QUOTE_INT) {
                if (b0 == C.MINUS_INT && getByte(start + 1) == C.I_BYTE_INT && matchInfinityBytes(start + 1)) { // "-Infinity"
                    position = start + C.NEG_INFINITY_QUOTED_LEN - 1
                    nextTokenByte = C.RESET_TOKEN_BYTE
                    return Double.NEGATIVE_INFINITY
                }
            }
        }
        val prev = coerceStringsToNumbers
        coerceStringsToNumbers = true
        try {
            return this.nextDoubleExtension()
        } finally {
            coerceStringsToNumbers = prev
        }
    }
    return this.nextDoubleExtension()
}

private fun GhostProtoJsonFlatReader.matchInfinityBytes(start: Int): Boolean {
    // Infinity has 8 characters: I, n, f, i, n, i, t, y
    // We already checked 'I' (73) in the caller, verify remaining 7 bytes
    return getByte(start + 1) == C.N_LOWER_BYTE_INT &&
           getByte(start + 2) == C.F_LOWER_BYTE_INT &&
           getByte(start + 3) == C.I_LOWER_BYTE_INT &&
           getByte(start + 4) == C.N_LOWER_BYTE_INT &&
           getByte(start + 5) == C.I_LOWER_BYTE_INT &&
           getByte(start + 6) == C.T_LOWER_BYTE_INT &&
           getByte(start + 7) == C.Y_LOWER_BYTE_INT
}

internal fun GhostProtoJsonFlatReader.nextProtoInt64(): Long {
    val token = peekNextToken()
    return if (token == C.QUOTE_INT) {
        val prev = coerceStringsToNumbers
        coerceStringsToNumbers = true
        try {
            this.nextLongExtension()
        } finally {
            coerceStringsToNumbers = prev
        }
    } else {
        this.nextDoubleExtension().toLong()
    }
}

internal fun GhostProtoJsonFlatReader.readProtoUInt32(): Long {
    val value = nextProtoInt64()
    if (value < 0L || value > C.PROTO_UINT32_MAX) {
        throwError(C.ERR_PROTO_UINT32_OVERFLOW)
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
    return if (token == C.QUOTE_INT) {
        nextString().toULong()
    } else {
        nextProtoInt64().toULong()
    }
}

internal fun GhostProtoJsonFlatReader.readProtoEnum(options: JsonReaderOptions): Int {
    val token = peekNextToken()
    if (token == C.QUOTE_INT) {
        val index = selectString(options)
        if (index != C.MATCH_NONE) {
            return index
        }
        throwError(C.ERR_UNKNOWN_ENUM)
    } else {
        // Read as integer value index
        return nextInt()
    }
}

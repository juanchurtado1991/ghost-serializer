@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.GhostJsonConstants as C

/**
 * Captures the next complete JSON value as a raw [ByteArray] without decoding.
 *
 * Same semantics as [GhostJsonFlatReader.captureRawJsonBytes] but operates on the
 * [GhostJsonReader] (non-flat) byte-array source. The reader must be positioned at
 * (or before) the first non-whitespace byte of the value.
 */
fun GhostJsonReader.captureRawJsonBytes(): ByteArray {
    skipWhitespace()
    val start = position
    captureReaderValueBytes()
    nextTokenByte = -1
    return rawData.copyOfRange(start, position)
}

private fun GhostJsonReader.captureReaderValueBytes() {
    val data = rawData
    val localLimit = limit
    val first = data[position++].toInt() and C.BYTE_MASK
    when (first) {
        C.OPEN_OBJ_INT, C.OPEN_ARR_INT -> {
            var depth = 1
            while (position < localLimit && depth > 0) {
                when (data[position++].toInt() and C.BYTE_MASK) {
                    C.QUOTE_INT -> captureReaderSkipStringBytes(data, localLimit)
                    C.OPEN_OBJ_INT, C.OPEN_ARR_INT -> depth++
                    C.CLOSE_OBJ_INT, C.CLOSE_ARR_INT -> depth--
                }
            }
        }
        C.QUOTE_INT -> captureReaderSkipStringBytes(data, localLimit)
        C.TRUE_CHAR_INT -> position += 3
        C.FALSE_CHAR_INT -> position += 4
        C.NULL_CHAR_INT -> position += 3
        else -> {
            while (position < localLimit) {
                val b = data[position].toInt() and C.BYTE_MASK
                if (b == C.COMMA_INT || b == C.CLOSE_OBJ_INT || b == C.CLOSE_ARR_INT || b <= C.SPACE_INT) break
                position++
            }
        }
    }
}

private fun GhostJsonReader.captureReaderSkipStringBytes(data: ByteArray, localLimit: Int) {
    while (position < localLimit) {
        when (data[position++].toInt() and C.BYTE_MASK) {
            C.QUOTE_INT -> return
            C.BACKSLASH_INT -> if (position < localLimit) position++
        }
    }
}

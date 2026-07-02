@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.GhostJsonConstants as C
import com.ghost.serialization.types.RawJson

/**
 * Captures the next complete JSON value as a [RawJson] view into this reader's buffer.
 */
fun GhostJsonReader.captureRawJson(): RawJson {
    skipWhitespace()
    val start = position
    captureReaderValueBytes()
    nextTokenByte = -1
    val length = position - start
    return if (isStreaming) {
        RawJson.fromUtf8Bytes(captureReaderRangeBytes(start, length))
    } else {
        RawJson.fromBufferSlice(rawData, start, length)
    }
}

/**
 * Captures the next complete JSON value as a raw [ByteArray] without decoding.
 *
 * Same semantics as [GhostJsonFlatReader.captureRawJsonBytes] but operates on the
 * [GhostJsonReader] (non-flat) byte-array source. The reader must be positioned at
 * (or before) the first non-whitespace byte of the value.
 */
fun GhostJsonReader.captureRawJsonBytes(): ByteArray = captureRawJson().bytes

private fun GhostJsonReader.captureReaderValueBytes() {
    val localLimit = limit
    val first = getByte(position++)
    when (first) {
        C.OPEN_OBJ_INT, C.OPEN_ARR_INT -> {
            var depth = 1
            while (position < localLimit && depth > 0) {
                when (getByte(position++)) {
                    C.QUOTE_INT -> captureReaderSkipStringBytes(localLimit)
                    C.OPEN_OBJ_INT, C.OPEN_ARR_INT -> depth++
                    C.CLOSE_OBJ_INT, C.CLOSE_ARR_INT -> depth--
                }
            }
        }
        C.QUOTE_INT -> captureReaderSkipStringBytes(localLimit)
        C.TRUE_CHAR_INT -> position += 3
        C.FALSE_CHAR_INT -> position += 4
        C.NULL_CHAR_INT -> position += 3
        else -> {
            while (position < localLimit) {
                val b = getByte(position)
                if (b == C.COMMA_INT || b == C.CLOSE_OBJ_INT || b == C.CLOSE_ARR_INT || b <= C.SPACE_INT) break
                position++
            }
        }
    }
}

private fun GhostJsonReader.captureReaderSkipStringBytes(localLimit: Int) {
    while (position < localLimit) {
        when (getByte(position++)) {
            C.QUOTE_INT -> return
            C.BACKSLASH_INT -> if (position < localLimit) position++
        }
    }
}

private fun GhostJsonReader.captureReaderRangeBytes(start: Int, length: Int): ByteArray {
    val result = ByteArray(length)
    var index = 0
    while (index < length) {
        result[index] = getByte(start + index).toByte()
        index++
    }
    return result
}

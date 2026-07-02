@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.GhostJsonConstants as C
import com.ghost.serialization.types.RawJson

/**
 * Captures the next complete JSON value (object, array, string, number, boolean, null)
 * as a [RawJson] view into this reader's buffer without copying UTF-8 bytes.
 */
fun GhostJsonFlatReader.captureRawJson(): RawJson {
    skipWhitespace()
    val start = position
    captureJsonValueBytes()
    nextTokenByte = C.RESET_TOKEN_BYTE
    return RawJson.fromBufferSlice(rawData, start, position - start)
}

/**
 * Captures the next complete JSON value (object, array, string, number, boolean, null)
 * as a raw [ByteArray] without decoding any content. The returned bytes are a verbatim
 * copy of the UTF-8 payload that represents the value in the input, including surrounding
 * brackets or quotes.
 *
 * Designed for deferred parsing: capture bytes here, then pass them later to
 * [com.ghost.serialization.Ghost.deserialize] which will create a new flat reader
 * over the captured slice — no intermediate String allocation, no UTF-8→UTF-16 round-trip.
 *
 * Contrast with [com.ghost.serialization.parser.GhostJsonFlatReader.skipValue], which
 * uses the stateful depth/comma machine. This function is a pure byte-level scan that
 * does not touch depth, needsCommaMask, or commaConsumedMask.
 */
fun GhostJsonFlatReader.captureRawJsonBytes(): ByteArray = captureRawJson().bytes

private fun GhostJsonFlatReader.captureJsonValueBytes() {
    val data = rawData
    val localLimit = limit
    val first = data[position++].toInt() and C.BYTE_MASK
    when (first) {
        C.OPEN_OBJ_INT, C.OPEN_ARR_INT -> {
            // Scan past the entire object/array. A single depth counter works for
            // well-formed JSON: every { or [ increments, every } or ] decrements.
            // Quoted strings are skipped in full to avoid counting brackets inside them.
            var depth = 1
            while (position < localLimit && depth > 0) {
                when (data[position++].toInt() and C.BYTE_MASK) {
                    C.QUOTE_INT -> captureSkipStringBytes(data, localLimit)
                    C.OPEN_OBJ_INT, C.OPEN_ARR_INT -> depth++
                    C.CLOSE_OBJ_INT, C.CLOSE_ARR_INT -> depth--
                }
            }
        }
        C.QUOTE_INT -> captureSkipStringBytes(data, localLimit)
        C.TRUE_CHAR_INT -> position += 3   // "rue" (the 't' was already consumed)
        C.FALSE_CHAR_INT -> position += 4  // "alse"
        C.NULL_CHAR_INT -> position += 3   // "ull"
        else -> {
            // Number: advance until a JSON structural delimiter or whitespace
            while (position < localLimit) {
                val b = data[position].toInt() and C.BYTE_MASK
                if (b == C.COMMA_INT || b == C.CLOSE_OBJ_INT || b == C.CLOSE_ARR_INT || b <= C.SPACE_INT) break
                position++
            }
        }
    }
}

/**
 * Advances [position] past the content of a quoted JSON string whose opening '"' has
 * already been consumed. Handles backslash escapes by skipping the escaped character.
 */
private fun GhostJsonFlatReader.captureSkipStringBytes(data: ByteArray, localLimit: Int) {
    while (position < localLimit) {
        when (data[position++].toInt() and C.BYTE_MASK) {
            C.QUOTE_INT -> return
            C.BACKSLASH_INT -> if (position < localLimit) position++
        }
    }
}

package com.ghost.serialization.parser

import com.ghost.serialization.parser.GhostJsonConstants as C

/**
 * Captures the next complete JSON value as a raw [ByteArray] without decoding.
 *
 * Since [GhostJsonStringReader] operates on a UTF-16 [String] (and its [CharArray] cache),
 * the captured range is re-encoded to UTF-8 via [String.encodeToByteArray]. This is the
 * slower path compared to [GhostJsonFlatReader.captureRawJsonBytes] — prefer the flat
 * reader when starting from a [ByteArray] source.
 */
fun GhostJsonStringReader.captureRawJsonBytes(): ByteArray {
    skipWhitespace()
    val start = position
    captureStringReaderValueBytes()
    nextTokenByte = C.RESET_TOKEN_BYTE
    return rawData.substring(start, position).encodeToByteArray()
}

private fun GhostJsonStringReader.captureStringReaderValueBytes() {
    val chars = rawChars
    val localLimit = limit
    val first = chars[position++].code
    when (first) {
        C.OPEN_OBJ_INT, C.OPEN_ARR_INT -> {
            var depth = 1
            while (position < localLimit && depth > 0) {
                when (chars[position++].code) {
                    C.QUOTE_INT -> captureStringReaderSkipString(chars, localLimit)
                    C.OPEN_OBJ_INT, C.OPEN_ARR_INT -> depth++
                    C.CLOSE_OBJ_INT, C.CLOSE_ARR_INT -> depth--
                }
            }
        }
        C.QUOTE_INT -> captureStringReaderSkipString(chars, localLimit)
        C.TRUE_CHAR_INT -> position += 3
        C.FALSE_CHAR_INT -> position += 4
        C.NULL_CHAR_INT -> position += 3
        else -> {
            while (position < localLimit) {
                val b = chars[position].code
                if (b == C.COMMA_INT || b == C.CLOSE_OBJ_INT || b == C.CLOSE_ARR_INT || b <= C.SPACE_INT) break
                position++
            }
        }
    }
}

private fun GhostJsonStringReader.captureStringReaderSkipString(chars: CharArray, localLimit: Int) {
    while (position < localLimit) {
        when (chars[position++].code) {
            C.QUOTE_INT -> return
            C.BACKSLASH_INT -> if (position < localLimit) position++
        }
    }
}

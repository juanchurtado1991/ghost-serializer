package com.ghost.serialization.parser.strings

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.types.RawJson
import com.ghost.serialization.parser.common.GhostJsonConstants as C


/**
 * Captures the next complete JSON value as owned [RawJson] (UTF-16 source requires encoding).
 */
fun GhostJsonStringReader.captureRawJson(): RawJson =
    RawJson.fromUtf8Bytes(captureRawJsonBytes())

/**
 * Captures the next complete JSON value as a raw [ByteArray] without decoding the value tree.
 *
 * Since [GhostJsonStringReader] operates on a UTF-16 [String], the captured char range is
 * converted to UTF-8 via [GhostJsonStringReader.sliceUtf8Bytes]: a range copy of the cached
 * UTF-8 view when present, otherwise an encode of that range only. Prefer
 * `parser.bytes.captureRawJsonBytes` when starting from a [ByteArray] source.
 */
@OptIn(InternalGhostApi::class)
fun GhostJsonStringReader.captureRawJsonBytes(): ByteArray {
    skipWhitespace()
    val start = position
    captureStringReaderValueBytes()
    nextTokenByte = C.RESET_TOKEN_BYTE
    return sliceUtf8Bytes(start, position)
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

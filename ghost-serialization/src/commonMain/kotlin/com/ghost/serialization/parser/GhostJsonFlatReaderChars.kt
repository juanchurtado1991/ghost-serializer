@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.GhostJsonConstants as C

/**
 * Reads a JSON string value that must contain exactly one UTF-16 [Char].
 *
 * Fast path: single unescaped ASCII/Latin-1 byte between quotes — no [String] allocation.
 */
fun GhostJsonFlatReader.nextChar(): Char {
    if (nextNonWhitespace() != C.QUOTE_INT) {
        throwError(C.ERR_EXPECTED_QUOTE)
    }

    val start = position
    val localData = rawData
    val scanResult = scanStringImpl(start, limit) { localData[it].toInt() and C.BYTE_MASK }

    if (scanResult != -1L) {
        val length = ((scanResult and C.SCAN_LENGTH_MASK) ushr C.SCAN_LENGTH_SHIFT).toInt()
        val only7Bit = (scanResult and C.SCAN_7BIT_BIT) != 0L
        val end = start + length
        if (length == C.SINGLE_CHAR_JSON_LENGTH && only7Bit) {
            position = end + 1
            nextTokenByte = C.RESET_TOKEN_BYTE
            return (localData[start].toInt() and C.BYTE_MASK).toChar()
        }
        if (length == 0) {
            position = end + 1
            nextTokenByte = C.RESET_TOKEN_BYTE
            throwError(C.ERR_EXPECTED_SINGLE_CHAR_STRING)
        }
    }

    val decoded = readQuotedStringFromContentStart(start)
    if (decoded.length != C.SINGLE_CHAR_JSON_LENGTH) {
        throwError(C.ERR_SINGLE_CHAR_STRING_WRONG_LENGTH + decoded.length)
    }
    return decoded[0]
}

private fun GhostJsonFlatReader.readQuotedStringFromContentStart(contentStart: Int): String {
    position = contentStart - 1
    return readQuotedString()
}

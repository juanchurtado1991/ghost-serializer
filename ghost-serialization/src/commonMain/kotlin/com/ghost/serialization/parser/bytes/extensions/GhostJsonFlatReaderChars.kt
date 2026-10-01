@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser.bytes.extensions

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.common.scanStringImpl
import com.ghost.serialization.parser.common.constants.GhostJsonErrorMessages as EM
import com.ghost.serialization.parser.common.constants.GhostJsonNumericLimits as NUM
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants as SCN
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

/**
 * Reads a JSON string value that must contain exactly one UTF-16 [Char].
 *
 * Fast path: single unescaped ASCII/Latin-1 byte between quotes — no [String] allocation.
 */
fun GhostJsonFlatReader.nextChar(): Char {
    if (nextNonWhitespace() != TOK.QUOTE_INT) {
        throwError(message = EM.ERR_EXPECTED_QUOTE)
    }

    val start = position
    val localData = rawData
    val scanResult = scanStringImpl(
        start = start,
        limit = limit
    ) { localData[it].toInt() and TOK.BYTE_MASK }

    if (scanResult != -1L) {
        val length = ((scanResult and SCN.SCAN_LENGTH_MASK) ushr SCN.SCAN_LENGTH_SHIFT).toInt()
        val only7Bit = (scanResult and SCN.SCAN_7BIT_BIT) != 0L
        val end = start + length
        if (length == NUM.SINGLE_CHAR_JSON_LENGTH && only7Bit) {
            position = end + 1
            nextTokenByte = SCN.RESET_TOKEN_BYTE
            pathTracker.finishScalarValue()
            return (localData[start].toInt() and TOK.BYTE_MASK).toChar()
        }
        if (length == 0) {
            position = end + 1
            nextTokenByte = SCN.RESET_TOKEN_BYTE
            throwError(message = EM.ERR_EXPECTED_SINGLE_CHAR_STRING)
        }
    }

    position = start - 1
    val decoded = readQuotedString()
    if (decoded.length != NUM.SINGLE_CHAR_JSON_LENGTH) {
        throwError(message = EM.ERR_SINGLE_CHAR_STRING_WRONG_LENGTH + decoded.length)
    }
    pathTracker.finishScalarValue()
    return decoded[0]
}

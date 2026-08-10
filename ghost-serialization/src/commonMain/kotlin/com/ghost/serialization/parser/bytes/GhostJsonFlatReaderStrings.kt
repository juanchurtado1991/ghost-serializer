@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser.bytes

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.GhostHeuristics
import com.ghost.serialization.parser.common.contentEqualsStringImpl
import com.ghost.serialization.parser.common.findClosingQuoteImpl
import com.ghost.serialization.parser.common.growBuffer
import com.ghost.serialization.parser.common.readQuotedStringSlowCore
import com.ghost.serialization.parser.common.rollingHashImpl
import com.ghost.serialization.parser.common.scanStringSwarNoHash
import com.ghost.serialization.parser.common.GhostJsonConstants as C


/**
 * Reads a double-quoted JSON string from the raw byte array, parsing escape sequences
 * and caching string instances in the stringPool when appropriate to save memory.
 *
 * @return The decoded string value.
 * @throws com.ghost.serialization.exception.GhostJsonException
 * if the string is malformed, unescaped control character is found,
 * or it is unterminated.
 */
fun GhostJsonFlatReader.readQuotedString(): String {
    if (nextNonWhitespace() != C.QUOTE_INT) {
        throwError(C.ERR_EXPECTED_QUOTE)
    }

    val start = position
    val localData = rawData
    // SWAR scan without the pool hash; long values (the bulk of the byte volume) are never
    // pooled, so hashing them during the scan is wasted. The hash is recomputed below only for
    // short, pool-eligible values.
    val scanResult = scanStringSwarNoHash(localData, start, limit)

    if (scanResult != C.MATCH_END.toLong()) {
        val length = ((scanResult and C.SCAN_LENGTH_MASK) ushr C.SCAN_LENGTH_SHIFT).toInt()
        val only7Bit = (scanResult and C.SCAN_7BIT_BIT) != 0L
        lastScanContentWas7BitOnly = only7Bit
        val end = start + length
        if (length <= 0) {
            position = end + 1
            nextTokenByte = C.RESET_TOKEN_BYTE
            return ""
        }
        if (length > GhostHeuristics.maxStringPoolLength) {
            val result = source.decodeJsonStringRange(start, end, only7Bit)
            position = end + 1
            nextTokenByte = C.RESET_TOKEN_BYTE
            return result
        }

        val rollingHash = rollingHashImpl(localData, start, length)
        val poolBucketIndex = rollingHash and (C.STR_POOL_SIZE - 1)
        if (stringPoolHashes[poolBucketIndex] == rollingHash) {
            val cachedString = stringPool[poolBucketIndex]
            if (only7Bit && cachedString != null && contentEqualsStringImpl(
                    start,
                    length,
                    cachedString
                ) { localData[it].toInt() and C.BYTE_MASK }
            ) {
                position = end + 1
                nextTokenByte = C.RESET_TOKEN_BYTE
                return cachedString
            }
        }

        val decodedString = source.decodeJsonStringRange(start, end, only7Bit)
        if (only7Bit) {
            stringPool[poolBucketIndex] = decodedString
            stringPoolHashes[poolBucketIndex] = rollingHash
        }
        position = end + 1
        nextTokenByte = C.RESET_TOKEN_BYTE
        return decodedString
    }

    return readQuotedStringSlow(start)
}

private fun GhostJsonFlatReader.readQuotedStringSlow(start: Int): String =
    readQuotedStringSlowCore(
        start = start,
        limit = limit,
        getByte = { getByte(it) },
        setPosition = { position = it },
        setNextTokenByte = { nextTokenByte = it },
        parseUnicodeHex = { parseUnicodeHex(it) },
        grow = { buf, outPos -> growBuffer(buf, outPos) },
        throwError = { throwError(it) },
    )

/**
 * Skips a double-quoted JSON string in the raw byte array without decoding its content.
 *
 * @throws com.ghost.serialization.exception.GhostJsonException
 * if the string is malformed or unterminated.
 */
fun GhostJsonFlatReader.skipQuotedString() {
    if (nextNonWhitespace() != C.QUOTE_INT) {
        throwError(C.ERR_EXPECTED_QUOTE)
    }

    val start = position
    val localData = rawData
    val end = findClosingQuoteImpl(start, limit) { localData[it].toInt() and C.BYTE_MASK }
    if (end != -1) {
        position = end + 1
        return
    }

    var pos = start
    while (pos < limit) {
        val byteValue = getByte(pos++)
        if (byteValue == C.QUOTE_INT) {
            position = pos
            nextTokenByte = C.RESET_TOKEN_BYTE
            return
        }

        if (byteValue == C.BACKSLASH_INT) {
            if (pos >= limit) {
                position = pos
                throwError(C.UNTERMINATED_ESCAPE_ERROR)
            }
            val escaped = getByte(pos++)

            if (escaped == C.UNICODE_PREFIX_U_INT) {
                if (pos + C.UNICODE_HEX_LENGTH > limit) {
                    position = pos
                    throwError(C.UNTERMINATED_UNICODE_ERROR)
                }
                parseUnicodeHex(pos)
                pos += C.UNICODE_HEX_LENGTH
            }
        } else if (byteValue < C.SPACE_INT) {
            position = pos
            throwError(C.UNESCAPED_CONTROL_CHAR_ERROR)
        }
    }
    position = pos
    throwError(C.UNTERMINATED_STRING_ERROR)
}

/**
 * Parses 4 hex digits from the byte array at the given position and returns the resulting code point.
 */
private fun GhostJsonFlatReader.parseUnicodeHex(currentPosition: Int): Int {
    val hexByte0 = getByte(currentPosition)
    val hexByte1 = getByte(currentPosition + 1)
    val hexByte2 = getByte(currentPosition + 2)
    val hexByte3 = getByte(currentPosition + 3)

    val hexLookupTable = C.HEX_LUT
    val digitValue0 = hexLookupTable[hexByte0]
    val digitValue1 = hexLookupTable[hexByte1]
    val digitValue2 = hexLookupTable[hexByte2]
    val digitValue3 = hexLookupTable[hexByte3]

    if ((digitValue0 or digitValue1 or digitValue2 or digitValue3) < 0) {
        throwError(C.ERR_INVALID_UNICODE_AT + currentPosition)
    }

    return (digitValue0 shl C.SHIFT_12) or
            (digitValue1 shl C.SHIFT_8) or
            (digitValue2 shl C.SHIFT_4) or
            digitValue3
}

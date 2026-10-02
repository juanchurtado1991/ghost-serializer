@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser.bytes.extensions

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.common.GhostHeuristics
import com.ghost.serialization.parser.common.contentEqualsStringImpl
import com.ghost.serialization.parser.common.findClosingQuoteImpl
import com.ghost.serialization.parser.common.growBuffer
import com.ghost.serialization.parser.common.json.readQuotedStringSlowCore
import com.ghost.serialization.parser.common.rollingHashImpl
import com.ghost.serialization.parser.common.scanStringSwarNoHash
import com.ghost.serialization.parser.common.constants.GhostJsonErrorMessages as EM
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants as SCN
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

/** Reads a double-quoted JSON string, decoding escapes and caching short instances in the string pool. */
fun GhostJsonFlatReader.readQuotedString(): String {
    if (nextNonWhitespace() != TOK.QUOTE_INT) {
        throwError(EM.ERR_EXPECTED_QUOTE)
    }

    val start = position
    val localData = rawData
    // SWAR scan without the pool hash; long values (the bulk of the byte volume) are never
    // pooled, so hashing them during the scan is wasted. The hash is recomputed below only for
    // short, pool-eligible values.
    val scanResult = scanStringSwarNoHash(data = localData, start = start, limit = limit)

    if (scanResult != SCN.MATCH_END.toLong()) {
        val length = ((scanResult and SCN.SCAN_LENGTH_MASK) ushr SCN.SCAN_LENGTH_SHIFT).toInt()
        val only7Bit = (scanResult and SCN.SCAN_7BIT_BIT) != 0L
        lastScanContentWas7BitOnly = only7Bit
        val end = start + length
        if (length <= 0) {
            position = end + 1
            nextTokenByte = SCN.RESET_TOKEN_BYTE
            return ""
        }
        if (length > GhostHeuristics.maxStringPoolLength) {
            val result = source.decodeJsonStringRange(start = start, end = end, isKnown7BitContent = only7Bit)
            position = end + 1
            nextTokenByte = SCN.RESET_TOKEN_BYTE
            return result
        }

        val rollingHash = rollingHashImpl(data = localData, start = start, length = length)
        val poolBucketIndex = rollingHash and (SCN.STR_POOL_SIZE - 1)
        if (stringPoolHashes[poolBucketIndex] == rollingHash) {
            val cachedString = stringPool[poolBucketIndex]
            val canReuseCachedString = only7Bit &&
                cachedString != null &&
                contentEqualsStringImpl(start = start, length = length, targetString = cachedString) { localData[it].toInt() and TOK.BYTE_MASK }
            if (canReuseCachedString) {
                position = end + 1
                nextTokenByte = SCN.RESET_TOKEN_BYTE
                return cachedString
            }
        }

        val decodedString = source.decodeJsonStringRange(start = start, end = end, isKnown7BitContent = only7Bit)
        if (only7Bit) {
            stringPool[poolBucketIndex] = decodedString
            stringPoolHashes[poolBucketIndex] = rollingHash
        }
        position = end + 1
        nextTokenByte = SCN.RESET_TOKEN_BYTE
        return decodedString
    }

    return readQuotedStringSlow(start = start)
}

private fun GhostJsonFlatReader.readQuotedStringSlow(
    start: Int
): String = readQuotedStringSlowCore(
    start = start,
    limit = limit,
    getByte = { getByte(it) },
    setPosition = { position = it },
    setNextTokenByte = { nextTokenByte = it },
    parseUnicodeHex = { parseUnicodeHex(it) },
    grow = { buf, outPos -> growBuffer(outBuffer = buf, outPos = outPos) },
    throwError = { throwError(it) },
)

/** Skips a double-quoted JSON string without decoding its content. */
fun GhostJsonFlatReader.skipQuotedString() {
    if (nextNonWhitespace() != TOK.QUOTE_INT) {
        throwError(EM.ERR_EXPECTED_QUOTE)
    }

    val start = position
    val localData = rawData
    val end = findClosingQuoteImpl(position = start, limit = limit) { localData[it].toInt() and TOK.BYTE_MASK }
    if (end != -1) {
        position = end + 1
        return
    }

    var pos = start
    while (pos < limit) {
        val byteValue = getByte(pos++)
        if (byteValue == TOK.QUOTE_INT) {
            position = pos
            nextTokenByte = SCN.RESET_TOKEN_BYTE
            return
        }

        if (byteValue == TOK.BACKSLASH_INT) {
            if (pos >= limit) {
                position = pos
                throwError(EM.UNTERMINATED_ESCAPE_ERROR)
            }
            val escaped = getByte(pos++)

            if (escaped == TOK.UNICODE_PREFIX_U_INT) {
                if (pos + TOK.UNICODE_HEX_LENGTH > limit) {
                    position = pos
                    throwError(EM.UNTERMINATED_UNICODE_ERROR)
                }
                parseUnicodeHex(pos)
                pos += TOK.UNICODE_HEX_LENGTH
            }
        } else if (byteValue < TOK.SPACE_INT) {
            position = pos
            throwError(EM.UNESCAPED_CONTROL_CHAR_ERROR)
        }
    }
    position = pos
    throwError(EM.UNTERMINATED_STRING_ERROR)
}

private fun GhostJsonFlatReader.parseUnicodeHex(currentPosition: Int): Int {
    val hexByte0 = getByte(currentPosition)
    val hexByte1 = getByte(currentPosition + 1)
    val hexByte2 = getByte(currentPosition + 2)
    val hexByte3 = getByte(currentPosition + 3)

    val hexLookupTable = TOK.HEX_LUT
    val digitValue0 = hexLookupTable[hexByte0]
    val digitValue1 = hexLookupTable[hexByte1]
    val digitValue2 = hexLookupTable[hexByte2]
    val digitValue3 = hexLookupTable[hexByte3]

    if ((digitValue0 or digitValue1 or digitValue2 or digitValue3) < 0) {
        throwError(EM.ERR_INVALID_UNICODE_AT + currentPosition)
    }

    return (digitValue0 shl SCN.SHIFT_12) or
            (digitValue1 shl SCN.SHIFT_8) or
            (digitValue2 shl SCN.SHIFT_4) or
            digitValue3
}

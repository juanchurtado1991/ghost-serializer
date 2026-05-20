@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.releaseScratchBuffer
import com.ghost.serialization.parser.GhostJsonConstants as C

/**
 * Reads a double-quoted JSON string from the raw byte array, parsing escape sequences
 * and caching string instances in the [stringPool] when appropriate to save memory.
 *
 * @return The decoded string value.
 * @throws GhostJsonException if the string is malformed, unescaped control character is found,
 * or it is unterminated.
 */
fun GhostJsonFlatReader.readQuotedString(): String {
    if (nextNonWhitespace() != C.QUOTE_INT) {
        throwError(C.ERR_EXPECTED_QUOTE)
    }

    val start = position
    val scanResult = source.scanString(start, limit)

    if (scanResult != -1L) {
        val length = ((scanResult and C.SCAN_LENGTH_MASK) ushr C.SCAN_LENGTH_SHIFT).toInt()
        val rollingHash = scanResult.toInt()
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

        val poolBucketIndex = rollingHash and (C.STR_POOL_SIZE - 1)
        val cachedString = stringPool[poolBucketIndex]

        if (cachedString != null && source.contentEqualsString(start, length, cachedString)) {
            position = end + 1
            nextTokenByte = C.RESET_TOKEN_BYTE
            return cachedString
        }

        val decodedString = source.decodeJsonStringRange(start, end, only7Bit)
        stringPool[poolBucketIndex] = decodedString
        position = end + 1
        nextTokenByte = C.RESET_TOKEN_BYTE
        return decodedString
    }

    // Slow path: manual string building for escapes (Bitwise & Zero-Allocation approach)
    var outBuffer = acquireScratchBuffer(C.TIER_SMALL_INT)
    var outPos = 0

    try {
        var pos = start
        while (pos < limit) {
            val byteValue = getByte(pos++)
            if (byteValue == C.QUOTE_INT) {
                position = pos
                nextTokenByte = C.RESET_TOKEN_BYTE
                return outBuffer.decodeToString(0, outPos)
            }

            if (byteValue in C.CONTROL_CHAR_START_INT..C.CONTROL_CHAR_LIMIT_INT) {
                position = pos
                throwError(C.UNESCAPED_CONTROL_CHAR_ERROR)
            }

            if (byteValue == C.BACKSLASH_INT) {
                if (pos >= limit) {
                    position = pos
                    throwError(C.UNTERMINATED_ESCAPE_ERROR)
                }
                val escaped = getByte(pos++)
                when (escaped) {
                    C.UNICODE_PREFIX_U_INT -> {
                        if (pos + C.UNICODE_HEX_LENGTH > limit) {
                            position = pos
                            throwError(C.UNTERMINATED_UNICODE_ERROR)
                        }

                        var code = parseUnicodeHex(pos)
                        pos += C.UNICODE_HEX_LENGTH

                        if (code in C.HIGH_SURROGATE_START..C.HIGH_SURROGATE_END) {
                            if (pos + C.SURROGATE_OFFSET > limit ||
                                getByte(pos) == C.BACKSLASH_INT &&
                                getByte(pos + C.SINGLE_CHAR_SIZE) == C.UNICODE_PREFIX_U_INT
                            ) {
                                // Valid surrogate pair check
                                pos += C.UNICODE_ESCAPE_PREFIX_SIZE
                                val lowCode = parseUnicodeHex(pos)
                                if (lowCode in C.LOW_SURROGATE_START..C.LOW_SURROGATE_END) {
                                    pos += C.UNICODE_HEX_LENGTH
                                    code = C.UNICODE_BASE +
                                            ((code - C.HIGH_SURROGATE_START) shl C.SHIFT_10) +
                                            (lowCode - C.LOW_SURROGATE_START)
                                } else {
                                    position = pos
                                    throwError(C.ERR_HIGH_SURROGATE)
                                }
                            } else {
                                position = pos
                                throwError(C.ERR_HIGH_SURROGATE)
                            }
                        }

                        // Encode code point to UTF-8 bytes in outBuffer
                        if (code <= C.UTF8_1BYTE_MAX) {
                            if (outPos + 1 > outBuffer.size) {
                                outBuffer = growBuffer(outBuffer, outPos)
                            }
                            outBuffer[outPos++] = code.toByte()
                        } else if (code <= C.UTF8_2BYTE_MAX) {
                            if (outPos + 2 > outBuffer.size) {
                                outBuffer = growBuffer(outBuffer, outPos)
                            }
                            outBuffer[outPos++] = (C.UTF8_2BYTE_PREFIX or (code shr C.UTF8_SHIFT_6)).toByte()
                            outBuffer[outPos++] = (C.UTF8_CONT_PREFIX or (code and C.UTF8_CONT_MASK)).toByte()
                        } else if (code <= C.BMP_LIMIT) {
                            if (outPos + 3 > outBuffer.size) {
                                outBuffer = growBuffer(outBuffer, outPos)
                            }
                            outBuffer[outPos++] = (C.UTF8_3BYTE_PREFIX or (code shr C.UTF8_SHIFT_12)).toByte()
                            outBuffer[outPos++] = (C.UTF8_CONT_PREFIX or ((code shr C.UTF8_SHIFT_6) and C.UTF8_CONT_MASK)).toByte()
                            outBuffer[outPos++] = (C.UTF8_CONT_PREFIX or (code and C.UTF8_CONT_MASK)).toByte()
                        } else {
                            if (outPos + 4 > outBuffer.size) {
                                outBuffer = growBuffer(outBuffer, outPos)
                            }
                            outBuffer[outPos++] = (C.UTF8_4BYTE_PREFIX or (code shr C.UTF8_SHIFT_18)).toByte()
                            outBuffer[outPos++] = (C.UTF8_CONT_PREFIX or ((code shr C.UTF8_SHIFT_12) and C.UTF8_CONT_MASK)).toByte()
                            outBuffer[outPos++] = (C.UTF8_CONT_PREFIX or ((code shr C.UTF8_SHIFT_6) and C.UTF8_CONT_MASK)).toByte()
                            outBuffer[outPos++] = (C.UTF8_CONT_PREFIX or (code and C.UTF8_CONT_MASK)).toByte()
                        }
                    }

                    C.N_BYTE_INT -> {
                        if (outPos + 1 > outBuffer.size) {
                            outBuffer = growBuffer(outBuffer, outPos)
                        }
                        outBuffer[outPos++] = C.LF_INT.toByte()
                    }

                    C.R_BYTE_INT -> {
                        if (outPos + 1 > outBuffer.size) {
                            outBuffer = growBuffer(outBuffer, outPos)
                        }
                        outBuffer[outPos++] = C.CR_INT.toByte()
                    }

                    C.T_BYTE_INT -> {
                        if (outPos + 1 > outBuffer.size) {
                            outBuffer = growBuffer(outBuffer, outPos)
                        }
                        outBuffer[outPos++] = C.TAB_INT.toByte()
                    }

                    C.B_BYTE_INT -> {
                        if (outPos + 1 > outBuffer.size) {
                            outBuffer = growBuffer(outBuffer, outPos)
                        }
                        outBuffer[outPos++] = C.BS_INT.toByte()
                    }

                    C.F_BYTE_INT -> {
                        if (outPos + 1 > outBuffer.size) {
                            outBuffer = growBuffer(outBuffer, outPos)
                        }
                        outBuffer[outPos++] = C.FF_INT.toByte()
                    }

                    else -> {
                        if (outPos + 1 > outBuffer.size) {
                            outBuffer = growBuffer(outBuffer, outPos)
                        }
                        outBuffer[outPos++] = escaped.toByte()
                    }
                }
            } else {
                if (outPos + 1 > outBuffer.size) {
                    outBuffer = growBuffer(outBuffer, outPos)
                }
                outBuffer[outPos++] = byteValue.toByte()
            }
        }
        position = pos
    } finally {
        releaseScratchBuffer(outBuffer)
    }
    throwError(C.UNTERMINATED_STRING_ERROR)
}

/**
 * Skips a double-quoted JSON string in the raw byte array without decoding its content.
 *
 * @throws GhostJsonException if the string is malformed or unterminated.
 */
fun GhostJsonFlatReader.skipQuotedString() {
    if (nextNonWhitespace() != C.QUOTE_INT) {
        throwError(C.ERR_EXPECTED_QUOTE)
    }

    val start = position
    val end = source.findClosingQuote(start, limit)
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

        if (byteValue in C.CONTROL_CHAR_START_INT..C.CONTROL_CHAR_LIMIT_INT) {
            position = pos
            throwError(C.UNESCAPED_CONTROL_CHAR_ERROR)
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
        throwError("Invalid unicode escape at $currentPosition")
    }

    return (digitValue0 shl C.SHIFT_12) or
            (digitValue1 shl C.SHIFT_8) or
            (digitValue2 shl C.SHIFT_4) or
            digitValue3
}

/**
 * Utility helper to grow a temporary byte array buffer.
 */
private fun growBuffer(outBuffer: ByteArray, outPos: Int): ByteArray {
    val newBuffer = acquireScratchBuffer(outBuffer.size * C.BUFFER_SCALE_FACTOR)
    outBuffer.copyInto(newBuffer, 0, 0, outPos)
    releaseScratchBuffer(outBuffer)
    return newBuffer
}

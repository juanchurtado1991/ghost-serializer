@file:OptIn(InternalGhostApi::class)
@file:Suppress("NOTHING_TO_INLINE")

package com.ghost.serialization.parser.common

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.releaseScratchBuffer
import com.ghost.serialization.parser.common.GhostJsonConstants as C

/**
 * Shared UTF-8 slow path for quoted JSON strings containing escapes. Used by flat
 * ([com.ghost.serialization.parser.bytes.GhostJsonFlatReader]) and streaming
 * ([com.ghost.serialization.parser.streaming.GhostJsonReader]) byte readers; the
 * [CharArray] string reader keeps its own Char-based path. Reader state is supplied via
 * inlined adapters so each call site stays monomorphic after inlining.
 */
internal inline fun readQuotedStringSlowCore(
    start: Int,
    limit: Int,
    getByte: (Int) -> Int,
    setPosition: (Int) -> Unit,
    setNextTokenByte: (Int) -> Unit,
    parseUnicodeHex: (Int) -> Int,
    grow: (ByteArray, Int) -> ByteArray,
    throwError: (String) -> Nothing,
): String {
    var outBuffer = acquireScratchBuffer(C.TIER_SMALL_INT)
    var outPos = 0

    try {
        var pos = start
        while (pos < limit) {
            val byteValue = getByte(pos++)
            if (byteValue == C.QUOTE_INT) {
                setPosition(pos)
                setNextTokenByte(C.RESET_TOKEN_BYTE)
                return outBuffer.decodeToString(0, outPos)
            }

            if (byteValue == C.BACKSLASH_INT) {
                if (pos >= limit) {
                    setPosition(pos)
                    throwError(C.UNTERMINATED_ESCAPE_ERROR)
                }
                when (val escaped = getByte(pos++)) {
                    C.UNICODE_PREFIX_U_INT -> {
                        if (pos + C.UNICODE_HEX_LENGTH > limit) {
                            setPosition(pos)
                            throwError(C.UNTERMINATED_UNICODE_ERROR)
                        }

                        var code = parseUnicodeHex(pos)
                        pos += C.UNICODE_HEX_LENGTH

                        if (code in C.HIGH_SURROGATE_START..C.HIGH_SURROGATE_END) {
                            if (pos + C.SURROGATE_OFFSET <= limit &&
                                getByte(pos) == C.BACKSLASH_INT &&
                                getByte(pos + C.SINGLE_CHAR_SIZE) == C.UNICODE_PREFIX_U_INT
                            ) {
                                pos += C.UNICODE_ESCAPE_PREFIX_SIZE
                                val lowCode = parseUnicodeHex(pos)
                                if (lowCode in C.LOW_SURROGATE_START..C.LOW_SURROGATE_END) {
                                    pos += C.UNICODE_HEX_LENGTH
                                    code = C.UNICODE_BASE +
                                            ((code - C.HIGH_SURROGATE_START) shl C.SHIFT_10) +
                                            (lowCode - C.LOW_SURROGATE_START)
                                } else {
                                    setPosition(pos)
                                    throwError(C.ERR_HIGH_SURROGATE)
                                }
                            } else {
                                setPosition(pos)
                                throwError(C.ERR_HIGH_SURROGATE)
                            }
                        }

                        if (code <= C.UTF8_1BYTE_MAX) {
                            if (outPos + 1 > outBuffer.size) {
                                outBuffer = grow(outBuffer, outPos)
                            }
                            outBuffer[outPos++] = code.toByte()
                        } else if (code <= C.UTF8_2BYTE_MAX) {
                            if (outPos + 2 > outBuffer.size) {
                                outBuffer = grow(outBuffer, outPos)
                            }
                            outBuffer[outPos++] =
                                (C.UTF8_2BYTE_PREFIX or (code shr C.UTF8_SHIFT_6)).toByte()
                            outBuffer[outPos++] =
                                (C.UTF8_CONT_PREFIX or (code and C.UTF8_CONT_MASK)).toByte()
                        } else if (code <= C.BMP_LIMIT) {
                            if (outPos + 3 > outBuffer.size) {
                                outBuffer = grow(outBuffer, outPos)
                            }
                            outBuffer[outPos++] =
                                (C.UTF8_3BYTE_PREFIX or (code shr C.UTF8_SHIFT_12)).toByte()
                            outBuffer[outPos++] =
                                (C.UTF8_CONT_PREFIX or ((code shr C.UTF8_SHIFT_6) and C.UTF8_CONT_MASK)).toByte()
                            outBuffer[outPos++] =
                                (C.UTF8_CONT_PREFIX or (code and C.UTF8_CONT_MASK)).toByte()
                        } else {
                            if (outPos + 4 > outBuffer.size) {
                                outBuffer = grow(outBuffer, outPos)
                            }
                            outBuffer[outPos++] =
                                (C.UTF8_4BYTE_PREFIX or (code shr C.UTF8_SHIFT_18)).toByte()
                            outBuffer[outPos++] =
                                (C.UTF8_CONT_PREFIX or ((code shr C.UTF8_SHIFT_12) and C.UTF8_CONT_MASK)).toByte()
                            outBuffer[outPos++] =
                                (C.UTF8_CONT_PREFIX or ((code shr C.UTF8_SHIFT_6) and C.UTF8_CONT_MASK)).toByte()
                            outBuffer[outPos++] =
                                (C.UTF8_CONT_PREFIX or (code and C.UTF8_CONT_MASK)).toByte()
                        }
                    }

                    C.N_BYTE_INT -> {
                        if (outPos + 1 > outBuffer.size) {
                            outBuffer = grow(outBuffer, outPos)
                        }
                        outBuffer[outPos++] = C.LF_INT.toByte()
                    }

                    C.R_BYTE_INT -> {
                        if (outPos + 1 > outBuffer.size) {
                            outBuffer = grow(outBuffer, outPos)
                        }
                        outBuffer[outPos++] = C.CR_INT.toByte()
                    }

                    C.T_BYTE_INT -> {
                        if (outPos + 1 > outBuffer.size) {
                            outBuffer = grow(outBuffer, outPos)
                        }
                        outBuffer[outPos++] = C.TAB_INT.toByte()
                    }

                    C.B_BYTE_INT -> {
                        if (outPos + 1 > outBuffer.size) {
                            outBuffer = grow(outBuffer, outPos)
                        }
                        outBuffer[outPos++] = C.BS_INT.toByte()
                    }

                    C.F_BYTE_INT -> {
                        if (outPos + 1 > outBuffer.size) {
                            outBuffer = grow(outBuffer, outPos)
                        }
                        outBuffer[outPos++] = C.FF_INT.toByte()
                    }

                    else -> {
                        if (outPos + 1 > outBuffer.size) {
                            outBuffer = grow(outBuffer, outPos)
                        }
                        outBuffer[outPos++] = escaped.toByte()
                    }
                }
            } else if (byteValue < C.SPACE_INT) {
                setPosition(pos)
                throwError(C.UNESCAPED_CONTROL_CHAR_ERROR)
            } else {
                if (outPos + 1 > outBuffer.size) {
                    outBuffer = grow(outBuffer, outPos)
                }
                outBuffer[outPos++] = byteValue.toByte()
            }
        }
        setPosition(pos)
    } finally {
        releaseScratchBuffer(outBuffer)
    }
    throwError(C.UNTERMINATED_STRING_ERROR)
}

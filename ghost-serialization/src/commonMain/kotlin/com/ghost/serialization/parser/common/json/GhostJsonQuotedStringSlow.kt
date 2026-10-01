@file:OptIn(InternalGhostApi::class)
@file:Suppress("NOTHING_TO_INLINE")

package com.ghost.serialization.parser.common.json

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.releaseScratchBuffer
import com.ghost.serialization.parser.common.constants.GhostJsonErrorMessages as EM
import com.ghost.serialization.parser.common.constants.GhostJsonNumericLimits as NUM
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants as SCN
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants as WR

/**
 * Shared UTF-8 slow path for quoted JSON strings containing escapes. Used by flat
 * ([com.ghost.serialization.parser.bytes.GhostJsonFlatReader]) and streaming
 * ([com.ghost.serialization.parser.streaming.GhostJsonReader]) byte readers; the
 * [CharArray] string reader keeps its own Char-based path. Reader state is supplied via
 * inlined adapters so each call site stays monomorphic after inlining.
 *
 * [resolveUnicodeEscape] packs its `(position, codePoint)` result into one [Long] (high 32
 * bits = position after the escape, low 32 bits = code point) to avoid allocating a `Pair`.
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
    var outBuffer = acquireScratchBuffer(minSize = WR.TIER_SMALL_INT)
    var outPos = 0

    try {
        var pos = start
        while (pos < limit) {
            val byteValue = getByte(pos++)
            if (byteValue == TOK.QUOTE_INT) {
                setPosition(pos)
                setNextTokenByte(SCN.RESET_TOKEN_BYTE)
                return outBuffer.decodeToString(0, outPos)
            }

            if (byteValue == TOK.BACKSLASH_INT) {
                if (pos >= limit) {
                    setPosition(pos)
                    throwError(EM.UNTERMINATED_ESCAPE_ERROR)
                }
                when (val escaped = getByte(pos++)) {
                    TOK.UNICODE_PREFIX_U_INT -> {
                        if (pos + TOK.UNICODE_HEX_LENGTH > limit) {
                            setPosition(pos)
                            throwError(EM.UNTERMINATED_UNICODE_ERROR)
                        }
                        val resolved = resolveUnicodeEscape(
                            position = pos,
                            limit = limit,
                            getByte = getByte,
                            parseUnicodeHex = parseUnicodeHex,
                            setPosition = setPosition,
                            throwError = throwError
                        )
                        pos = (resolved ushr PACKED_POSITION_SHIFT).toInt()
                        val code = (resolved and PACKED_CODE_POINT_MASK).toInt()
                        outPos = appendUtf8CodePoint(
                            code = code,
                            outPos = outPos,
                            outBuffer = outBuffer,
                            grow = grow,
                            setBuffer = { outBuffer = it }
                        )
                    }

                    TOK.N_BYTE_INT, TOK.R_BYTE_INT, TOK.T_BYTE_INT, TOK.B_BYTE_INT, TOK.F_BYTE_INT -> {
                        outBuffer = growIfNeeded(
                            outBuffer = outBuffer,
                            outPos = outPos,
                            additionalBytes = 1,
                            grow = grow
                        )
                        outBuffer[outPos++] = simpleEscapeByte(escaped = escaped).toByte()
                    }

                    else -> {
                        outBuffer = growIfNeeded(
                            outBuffer = outBuffer,
                            outPos = outPos,
                            additionalBytes = 1,
                            grow = grow
                        )
                        outBuffer[outPos++] = escaped.toByte()
                    }
                }
            } else if (byteValue < TOK.SPACE_INT) {
                setPosition(pos)
                throwError(EM.UNESCAPED_CONTROL_CHAR_ERROR)
            } else {
                outBuffer = growIfNeeded(
                    outBuffer = outBuffer,
                    outPos = outPos,
                    additionalBytes = 1,
                    grow = grow
                )
                outBuffer[outPos++] = byteValue.toByte()
            }
        }
        setPosition(pos)
    } finally {
        releaseScratchBuffer(buffer = outBuffer)
    }
    throwError(EM.UNTERMINATED_STRING_ERROR)
}

/** Encodes [code] as 1-4 UTF-8 bytes, growing [outBuffer] first if needed. Returns the new `outPos`. */
private inline fun appendUtf8CodePoint(
    code: Int,
    outPos: Int,
    outBuffer: ByteArray,
    grow: (ByteArray, Int) -> ByteArray,
    setBuffer: (ByteArray) -> Unit,
): Int {
    var buffer = outBuffer
    var pos = outPos
    when {
        code <= WR.UTF8_1BYTE_MAX -> {
            buffer = growIfNeeded(outBuffer = buffer, outPos = pos, additionalBytes = 1, grow = grow)
            buffer[pos++] = code.toByte()
        }
        code <= WR.UTF8_2BYTE_MAX -> {
            buffer = growIfNeeded(outBuffer = buffer, outPos = pos, additionalBytes = 2, grow = grow)
            buffer[pos++] = (WR.UTF8_2BYTE_PREFIX or (code shr WR.UTF8_SHIFT_6)).toByte()
            buffer[pos++] = (WR.UTF8_CONT_PREFIX or (code and WR.UTF8_CONT_MASK)).toByte()
        }
        code <= TOK.BMP_LIMIT -> {
            buffer = growIfNeeded(outBuffer = buffer, outPos = pos, additionalBytes = 3, grow = grow)
            buffer[pos++] = (WR.UTF8_3BYTE_PREFIX or (code shr WR.UTF8_SHIFT_12)).toByte()
            buffer[pos++] = (WR.UTF8_CONT_PREFIX or ((code shr WR.UTF8_SHIFT_6) and WR.UTF8_CONT_MASK)).toByte()
            buffer[pos++] = (WR.UTF8_CONT_PREFIX or (code and WR.UTF8_CONT_MASK)).toByte()
        }
        else -> {
            buffer = growIfNeeded(outBuffer = buffer, outPos = pos, additionalBytes = 4, grow = grow)
            buffer[pos++] = (WR.UTF8_4BYTE_PREFIX or (code shr WR.UTF8_SHIFT_18)).toByte()
            buffer[pos++] = (WR.UTF8_CONT_PREFIX or ((code shr WR.UTF8_SHIFT_12) and WR.UTF8_CONT_MASK)).toByte()
            buffer[pos++] = (WR.UTF8_CONT_PREFIX or ((code shr WR.UTF8_SHIFT_6) and WR.UTF8_CONT_MASK)).toByte()
            buffer[pos++] = (WR.UTF8_CONT_PREFIX or (code and WR.UTF8_CONT_MASK)).toByte()
        }
    }
    setBuffer(buffer)
    return pos
}

/** Grows [outBuffer] when fewer than [additionalBytes] remain past `outPos`; otherwise returns it unchanged. */
private inline fun growIfNeeded(
    outBuffer: ByteArray,
    outPos: Int,
    additionalBytes: Int,
    grow: (ByteArray, Int) -> ByteArray,
): ByteArray = if (outPos + additionalBytes > outBuffer.size) grow(outBuffer, outPos) else outBuffer

/**
 * Resolves a `\uXXXX` escape at [position] (right after the `\u` prefix) into a code point,
 * combining it with a following `\uYYYY` low surrogate when [position] starts a high surrogate.
 * Packed into a [Long] as `(newPosition shl 32) or codePoint` — see [readQuotedStringSlowCore].
 */
private inline fun resolveUnicodeEscape(
    position: Int,
    limit: Int,
    getByte: (Int) -> Int,
    parseUnicodeHex: (Int) -> Int,
    setPosition: (Int) -> Unit,
    throwError: (String) -> Nothing,
): Long {
    var pos = position
    var code = parseUnicodeHex(pos)
    pos += TOK.UNICODE_HEX_LENGTH

    if (code in TOK.HIGH_SURROGATE_START..TOK.HIGH_SURROGATE_END) {
        val hasLowSurrogateEscape = pos + TOK.SURROGATE_OFFSET <= limit &&
            getByte(pos) == TOK.BACKSLASH_INT &&
            getByte(pos + NUM.SINGLE_CHAR_SIZE) == TOK.UNICODE_PREFIX_U_INT
        if (!hasLowSurrogateEscape) {
            setPosition(pos)
            throwError(EM.ERR_HIGH_SURROGATE)
        }
        pos += NUM.UNICODE_ESCAPE_PREFIX_SIZE
        val lowCode = parseUnicodeHex(pos)
        if (lowCode !in TOK.LOW_SURROGATE_START..TOK.LOW_SURROGATE_END) {
            setPosition(pos)
            throwError(EM.ERR_HIGH_SURROGATE)
        }
        pos += TOK.UNICODE_HEX_LENGTH
        code = TOK.UNICODE_BASE +
            ((code - TOK.HIGH_SURROGATE_START) shl TOK.SHIFT_10) +
            (lowCode - TOK.LOW_SURROGATE_START)
    }

    return (pos.toLong() shl PACKED_POSITION_SHIFT) or (code.toLong() and PACKED_CODE_POINT_MASK)
}

/** Maps a `\n`/`\r`/`\t`/`\b`/`\f` escape byte to its raw control-character byte. */
private inline fun simpleEscapeByte(escaped: Int): Int = when (escaped) {
    TOK.N_BYTE_INT -> TOK.LF_INT
    TOK.R_BYTE_INT -> TOK.CR_INT
    TOK.T_BYTE_INT -> TOK.TAB_INT
    TOK.B_BYTE_INT -> TOK.BS_INT
    else -> TOK.FF_INT
}

private const val PACKED_POSITION_SHIFT = 32
private const val PACKED_CODE_POINT_MASK = 0xFFFFFFFFL

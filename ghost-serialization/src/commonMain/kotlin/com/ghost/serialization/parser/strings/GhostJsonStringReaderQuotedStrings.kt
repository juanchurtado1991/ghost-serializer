@file:Suppress("NOTHING_TO_INLINE")

package com.ghost.serialization.parser.strings

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.GhostHeuristics
import com.ghost.serialization.parser.common.constants.GhostJsonErrorMessages as EM
import com.ghost.serialization.parser.common.constants.GhostJsonNumericLimits as NUM
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants as SCN
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

/** Reads a JSON string, decoding escapes only when [findClosingQuote] can't find an unescaped run. */
@OptIn(InternalGhostApi::class)
fun GhostJsonStringReader.readQuotedString(): String {
    if (nextNonWhitespace() != TOK.QUOTE_INT) {
        throwError(EM.ERR_EXPECTED_QUOTE)
    }

    val start = position
    val end = findClosingQuote(start, limit)

    if (end != SCN.MATCH_END) {
        val length = end - start
        position = end + NUM.SINGLE_CHAR_SIZE
        nextTokenByte = SCN.RESET_TOKEN_BYTE
        if (length <= 0) return ""
        // Fast path: no escape sequences — try string pool before allocating a new substring.
        if (length <= GhostHeuristics.maxStringPoolLength) {
            val hash = computeStringPoolHash(start = start, length = length)
            // XOR the length into bucket selection so equal-hash values of different lengths
            // still spread across buckets.
            val poolKey = hash xor (length * SCN.STR_POOL_HASH_MULTIPLIER)
            val bucketIndex = poolKey and (SCN.STR_POOL_SIZE - 1)
            if (stringPoolHashes[bucketIndex] == poolKey) {
                val cached = stringPool[bucketIndex]
                if (cached != null && poolContentEquals(start = start, length = length, cached = cached)) {
                    return cached // zero allocation — reuse existing String object
                }
            }
            val newString = rawData.substring(start, start + length)
            stringPool[bucketIndex] = newString
            stringPoolHashes[bucketIndex] = poolKey
            return newString
        }
        return rawData.substring(start, start + length)
    }

    return readQuotedStringSlow(start = start)
}

/** Skips a JSON string without materializing it — same scan as [readQuotedString], no output buffer. */
fun GhostJsonStringReader.skipQuotedString() {
    if (nextNonWhitespace() != TOK.QUOTE_INT) {
        throwError(EM.ERR_EXPECTED_QUOTE)
    }

    val start = position
    val end = findClosingQuote(start, limit)
    if (end != -1) {
        position = end + 1
        return
    }

    var scanPosition = start
    val chars = rawChars

    val localQuoteInt = TOK.QUOTE_INT
    val localControlCharStartInt = TOK.CONTROL_CHAR_START_INT
    val localControlCharLimitInt = TOK.CONTROL_CHAR_LIMIT_INT
    val localBackslashInt = TOK.BACKSLASH_INT
    val localUnicodePrefixUInt = TOK.UNICODE_PREFIX_U_INT
    val localUnicodeHexLength = TOK.UNICODE_HEX_LENGTH

    while (scanPosition < limit) {
        val byteValue = chars[scanPosition++].code
        if (byteValue == localQuoteInt) {
            position = scanPosition
            nextTokenByte = SCN.RESET_TOKEN_BYTE
            return
        }

        if (byteValue in localControlCharStartInt..localControlCharLimitInt) {
            position = scanPosition
            throwError(EM.UNESCAPED_CONTROL_CHAR_ERROR)
        }

        if (byteValue == localBackslashInt) {
            if (scanPosition >= limit) {
                position = scanPosition
                throwError(EM.UNTERMINATED_ESCAPE_ERROR)
            }
            val escaped = chars[scanPosition++].code

            if (escaped == localUnicodePrefixUInt) {
                if (scanPosition + localUnicodeHexLength > limit) {
                    position = scanPosition
                    throwError(EM.UNTERMINATED_UNICODE_ERROR)
                }
                parseUnicodeHex(scanPosition)
                scanPosition += localUnicodeHexLength
            }
        }
    }
    position = scanPosition
    throwError(EM.UNTERMINATED_STRING_ERROR)
}

private fun GhostJsonStringReader.readQuotedStringSlow(start: Int): String {
    var outChars = slowPathChars
    var outPos = 0

    var startPosition = start
    val chars = rawChars

    val localQuoteInt = TOK.QUOTE_INT
    val localControlCharStartInt = TOK.CONTROL_CHAR_START_INT
    val localControlCharLimitInt = TOK.CONTROL_CHAR_LIMIT_INT
    val localBackslashInt = TOK.BACKSLASH_INT
    val localUnicodePrefixUInt = TOK.UNICODE_PREFIX_U_INT
    val localUnicodeHexLength = TOK.UNICODE_HEX_LENGTH
    val localHighSurrogateStart = TOK.HIGH_SURROGATE_START
    val localHighSurrogateEnd = TOK.HIGH_SURROGATE_END
    val localSurrogateOffset = TOK.SURROGATE_OFFSET
    val localUnicodeEscapePrefixSize = NUM.UNICODE_ESCAPE_PREFIX_SIZE
    val localLowSurrogateStart = TOK.LOW_SURROGATE_START
    val localLowSurrogateEnd = TOK.LOW_SURROGATE_END

    while (startPosition < limit) {
        val byteValue = chars[startPosition++].code
        if (byteValue == localQuoteInt) {
            position = startPosition
            nextTokenByte = SCN.RESET_TOKEN_BYTE
            return outChars.concatToString(0, outPos)
        }

        if (byteValue in localControlCharStartInt..localControlCharLimitInt) {
            position = startPosition
            throwError(EM.UNESCAPED_CONTROL_CHAR_ERROR)
        }

        if (byteValue == localBackslashInt) {
            if (startPosition >= limit) {
                position = startPosition
                throwError(EM.UNTERMINATED_ESCAPE_ERROR)
            }
            when (val escaped = chars[startPosition++].code) {
                localUnicodePrefixUInt -> {
                    if (startPosition + localUnicodeHexLength > limit) {
                        position = startPosition
                        throwError(EM.UNTERMINATED_UNICODE_ERROR)
                    }

                    val code = parseUnicodeHex(startPosition)
                    startPosition += localUnicodeHexLength

                    if (code in localHighSurrogateStart..localHighSurrogateEnd) {
                        val hasUnicodeEscapeAt = startPosition + localSurrogateOffset <= limit &&
                            chars[startPosition].code == localBackslashInt &&
                            chars[startPosition + NUM.SINGLE_CHAR_SIZE].code == localUnicodePrefixUInt
                        if (hasUnicodeEscapeAt) {
                            startPosition += localUnicodeEscapePrefixSize
                            val lowCode = parseUnicodeHex(startPosition)
                            if (lowCode in localLowSurrogateStart..localLowSurrogateEnd) {
                                startPosition += localUnicodeHexLength
                                outChars = ensureSlowPathCapacity(outChars = outChars, outPos = outPos, additional = 2)
                                outChars[outPos++] = code.toChar()
                                outChars[outPos++] = lowCode.toChar()
                            } else {
                                position = startPosition
                                throwError(EM.ERR_HIGH_SURROGATE)
                            }
                        } else {
                            position = startPosition
                            throwError(EM.ERR_HIGH_SURROGATE)
                        }
                    } else {
                        outChars = ensureSlowPathCapacity(outChars = outChars, outPos = outPos, additional = 1)
                        outChars[outPos++] = code.toChar()
                    }
                }

                TOK.N_BYTE_INT, TOK.R_BYTE_INT, TOK.T_BYTE_INT, TOK.B_BYTE_INT, TOK.F_BYTE_INT -> {
                    outChars = ensureSlowPathCapacity(outChars = outChars, outPos = outPos, additional = 1)
                    outChars[outPos++] = simpleEscapeChar(escaped = escaped)
                }

                else -> {
                    outChars = ensureSlowPathCapacity(outChars = outChars, outPos = outPos, additional = 1)
                    outChars[outPos++] = escaped.toChar()
                }
            }
        } else {
            outChars = ensureSlowPathCapacity(outChars = outChars, outPos = outPos, additional = 1)
            outChars[outPos++] = chars[startPosition - 1]
        }
    }
    position = startPosition
    throwError(EM.UNTERMINATED_STRING_ERROR)
}

private fun GhostJsonStringReader.parseUnicodeHex(currentPosition: Int): Int {
    val chars = rawChars
    val hexByte0 = chars[currentPosition].code
    val hexByte1 = chars[currentPosition + 1].code
    val hexByte2 = chars[currentPosition + 2].code
    val hexByte3 = chars[currentPosition + 3].code

    // Unlike byte readers (0..255), a Char's .code can be any UTF-16 unit up to 65535 — a
    // non-Latin-1 char after `\u` would index past HEX_LUT's 256 entries. Bounds-check
    // instead of indexing directly (found by fuzzing: raw high-code-point char threw
    // ArrayIndexOutOfBoundsException instead of the documented parse error).
    val hexLookupTable = TOK.HEX_LUT
    val lutSize = hexLookupTable.size
    val digitValue0 = if (hexByte0 < lutSize) hexLookupTable[hexByte0] else -1
    val digitValue1 = if (hexByte1 < lutSize) hexLookupTable[hexByte1] else -1
    val digitValue2 = if (hexByte2 < lutSize) hexLookupTable[hexByte2] else -1
    val digitValue3 = if (hexByte3 < lutSize) hexLookupTable[hexByte3] else -1

    if ((digitValue0 or digitValue1 or digitValue2 or digitValue3) < 0) {
        throwError(EM.ERR_INVALID_UNICODE_AT + currentPosition)
    }

    return (digitValue0 shl SCN.SHIFT_12) or
        (digitValue1 shl SCN.SHIFT_8) or
        (digitValue2 shl SCN.SHIFT_4) or
        digitValue3
}

/** Grows [outChars] only if fewer than [additional] slots remain past [outPos]. */
private inline fun GhostJsonStringReader.ensureSlowPathCapacity(
    outChars: CharArray,
    outPos: Int,
    additional: Int
): CharArray = if (outPos + additional > outChars.size) growSlowPathChars(
    current = outChars,
    requiredSize = outPos + additional
) else outChars

private fun GhostJsonStringReader.growSlowPathChars(current: CharArray, requiredSize: Int): CharArray {
    val newSize = (current.size * 2).coerceAtLeast(requiredSize)
    val newArray = CharArray(newSize)
    current.copyInto(newArray, 0, 0, current.size)
    slowPathChars = newArray
    return newArray
}

/** Maps a `\n`/`\r`/`\t`/`\b`/`\f` escape byte to its raw control character. */
private inline fun simpleEscapeChar(escaped: Int): Char = when (escaped) {
    TOK.N_BYTE_INT -> TOK.LF_CHAR
    TOK.R_BYTE_INT -> TOK.CR_CHAR
    TOK.T_BYTE_INT -> TOK.TAB_CHAR
    TOK.B_BYTE_INT -> TOK.BS_CHAR
    else -> TOK.FF_CHAR
}

/**
 * Hashes every char of a pool-eligible value (they are at most `maxStringPoolLength` long), so
 * values sharing a prefix — URLs, dates, ids — land in different buckets instead of evicting each
 * other. A bucket hit is still confirmed by [poolContentEquals].
 */
private fun GhostJsonStringReader.computeStringPoolHash(start: Int, length: Int): Int {
    val chars = rawChars
    var hash = 0
    var index = start
    val end = start + length
    while (index < end) {
        hash = hash * POOL_HASH_PRIME + chars[index].code
        index++
    }
    return hash
}

/**
 * Returns true if [GhostJsonStringReader.rawData]`[start, start+length)` equals [cached] char-by-char.
 * No 7-bit restriction needed: rawData chars are already UTF-16.
 */
private fun GhostJsonStringReader.poolContentEquals(start: Int, length: Int, cached: String): Boolean {
    if (cached.length != length) return false
    val chars = rawChars
    for (i in 0 until length) {
        if (cached[i] != chars[start + i]) return false
    }
    return true
}

private const val POOL_HASH_PRIME = 31

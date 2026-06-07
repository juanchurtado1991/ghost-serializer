@file:OptIn(InternalGhostApi::class)
@file:Suppress("FunctionName", "NOTHING_TO_INLINE")

package com.ghost.serialization.parser

import com.ghost.serialization.parser.GhostJsonConstants as C
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.writer.copyRangeToCharArray
import okio.ByteString

/**
 * High-performance JSON parser operating directly on Kotlin Strings.
 * Bypasses encodeToByteArray UTF-8 conversion overhead.
 */
class GhostJsonStringReader(
    var rawData: String,
    var maxDepth: Int = C.MAX_DEPTH,
    var strictMode: Boolean = false,
    var coerceStringsToNumbers: Boolean = false,
    var coerceBooleans: Boolean = false,
    var maxCollectionSize: Int = GhostHeuristics.maxCollectionSize
) {
    var limit: Int = rawData.length
    var position: Int = 0
    var nextTokenByte: Int = C.RESET_TOKEN_BYTE
    var lastScanContentWas7BitOnly: Boolean = false
    var depth: Int = 0
    var needsCommaMask: Long = 0L
    var commaConsumedMask: Long = 0L
    /** Cross-call string intern pool — same design as [GhostJsonFlatReader.stringPool]. */
    val stringPool: Array<String?> = arrayOfNulls(C.STR_POOL_SIZE)

    inline fun getByte(index: Int): Int {
        return rawData[index].code
    }

    fun throwError(message: String): Nothing {
        val errorPosition = position
        val errorEnd = if (errorPosition > limit) limit else errorPosition

        throw GhostJsonException(
            baseMessage = "$message at position $errorPosition",
            computeLineCol = {
                var columnNumber = 0
                var lineNumber = 0
                var byteIndex = 0
                while (byteIndex < errorEnd) {
                    if (rawData[byteIndex].code == C.NEWLINE_INT) {
                        lineNumber++
                        columnNumber = 0
                    } else {
                        columnNumber++
                    }
                    byteIndex++
                }
                intArrayOf(lineNumber, columnNumber)
            }
        )
    }

    fun internalSkip(n: Int) {
        position += n
        nextTokenByte = C.RESET_TOKEN_BYTE
    }

    fun skipWhitespace() {
        var pos = position
        val lim = limit
        val localData = rawData
        while (pos < lim) {
            val code = localData[pos].code
            if (code <= C.SPACE_INT && ((C.WHITESPACE_MASK shr code) and C.BYTE_SHIFT_UNIT) != C.RESULT_NONE) {
                pos++
            } else {
                position = pos
                nextTokenByte = code
                return
            }
        }
        position = lim
        nextTokenByte = C.MATCH_END
    }

    fun peekNextToken(): Int {
        val cached = nextTokenByte
        if (cached != -1) {
            return cached
        }
        skipWhitespace()
        return nextTokenByte
    }

    fun peekByte(): Byte = peekNextToken().toByte()

    fun nextNonWhitespace(): Int {
        val nextToken = peekNextToken()
        if (nextToken == -1) {
            throwError(C.ERR_UNEXPECTED_EOF)
        }
        internalSkip(1)
        return nextToken
    }

    fun skipAndValidateLiteral(expected: ByteString) {
        val size = expected.size
        if (position + size > limit) {
            throwError(C.ERR_EXPECTED_LITERAL + expected.utf8())
        }
        for (i in 0 until size) {
            if (rawData[position + i].code != (expected[i].toInt() and C.BYTE_MASK)) {
                throwError(C.ERR_EXPECTED_LITERAL + expected.utf8())
            }
        }
        position += size
        nextTokenByte = C.RESET_TOKEN_BYTE
    }

    fun reset(newData: String, newLimit: Int = newData.length) {
        this.rawData = newData
        this.position = 0
        this.limit = newLimit
        this.nextTokenByte = C.RESET_TOKEN_BYTE
        this.depth = 0
        this.needsCommaMask = 0L
        this.commaConsumedMask = 0L
        this.strictMode = false
        this.coerceStringsToNumbers = false
        this.coerceBooleans = false
        this.maxDepth = C.MAX_DEPTH
        this.maxCollectionSize = GhostHeuristics.maxCollectionSize
        this.lastScanContentWas7BitOnly = false
    }


    fun readQuotedString(): String {
        if (nextNonWhitespace() != C.QUOTE_INT) {
            throwError(C.ERR_EXPECTED_QUOTE)
        }

        val start = position
        val end = findClosingQuote(start, limit)

        if (end != C.MATCH_END) {
            val length = end - start
            position = end + C.SINGLE_CHAR_SIZE
            nextTokenByte = C.RESET_TOKEN_BYTE
            if (length <= 0) return ""
            // Fast path: no escape sequences — try string pool before allocating a new substring.
            if (length <= GhostHeuristics.maxStringPoolLength) {
                val hash = computeStringPoolHash(start, length)
                // XOR length into bucket selection to disambiguate strings that share
                // the same first-4-chars prefix but have different lengths.
                val bucketIndex = (hash xor (length * 31)) and (C.STR_POOL_SIZE - 1)
                val cached = stringPool[bucketIndex]
                if (cached != null && poolContentEquals(start, length, cached)) {
                    return cached  // zero allocation — reuse existing String object
                }
                val newString = rawData.substring(start, start + length)
                stringPool[bucketIndex] = newString
                return newString
            }
            return rawData.substring(start, start + length)
        }

        val stringBuilder = StringBuilder(if (end != C.MATCH_END) {
            end - start
        } else {
            C.DEFAULT_PRIMITIVE_COLLECTION_CAPACITY
        })

        var startPosition = start
        while (startPosition < limit) {
            val byteValue = rawData[startPosition++].code
            if (byteValue == C.QUOTE_INT) {
                position = startPosition
                nextTokenByte = C.RESET_TOKEN_BYTE
                return stringBuilder.toString()
            }

            if (byteValue in C.CONTROL_CHAR_START_INT..C.CONTROL_CHAR_LIMIT_INT) {
                position = startPosition
                throwError(C.UNESCAPED_CONTROL_CHAR_ERROR)
            }

            if (byteValue == C.BACKSLASH_INT) {
                if (startPosition >= limit) {
                    position = startPosition
                    throwError(C.UNTERMINATED_ESCAPE_ERROR)
                }
                when (val escaped = rawData[startPosition++].code) {
                    C.UNICODE_PREFIX_U_INT -> {
                        if (startPosition + C.UNICODE_HEX_LENGTH > limit) {
                            position = startPosition
                            throwError(C.UNTERMINATED_UNICODE_ERROR)
                        }

                        val code = parseUnicodeHex(startPosition)
                        startPosition += C.UNICODE_HEX_LENGTH

                        if (code in C.HIGH_SURROGATE_START..C.HIGH_SURROGATE_END) {
                            if (startPosition + C.SURROGATE_OFFSET <= limit &&
                                rawData[startPosition].code == C.BACKSLASH_INT &&
                                rawData[startPosition + C.SINGLE_CHAR_SIZE].code == C.UNICODE_PREFIX_U_INT
                            ) {
                                startPosition += C.UNICODE_ESCAPE_PREFIX_SIZE
                                val lowCode = parseUnicodeHex(startPosition)
                                if (lowCode in C.LOW_SURROGATE_START..C.LOW_SURROGATE_END) {
                                    startPosition += C.UNICODE_HEX_LENGTH
                                    stringBuilder.append(code.toChar())
                                    stringBuilder.append(lowCode.toChar())
                                } else {
                                    position = startPosition
                                    throwError(C.ERR_HIGH_SURROGATE)
                                }
                            } else {
                                position = startPosition
                                throwError(C.ERR_HIGH_SURROGATE)
                            }
                        } else {
                            stringBuilder.append(code.toChar())
                        }
                    }

                    C.N_BYTE_INT -> {
                        stringBuilder.append(C.LF_CHAR)
                    }

                    C.R_BYTE_INT -> {
                        stringBuilder.append(C.CR_CHAR)
                    }

                    C.T_BYTE_INT -> {
                        stringBuilder.append(C.TAB_CHAR)
                    }

                    C.B_BYTE_INT -> {
                        stringBuilder.append(C.BS_CHAR)
                    }

                    C.F_BYTE_INT -> {
                        stringBuilder.append(C.FF_CHAR)
                    }

                    else -> {
                        stringBuilder.append(escaped.toChar())
                    }
                }
            } else {
                stringBuilder.append(rawData[startPosition - 1])
            }
        }
        position = startPosition
        throwError(C.UNTERMINATED_STRING_ERROR)
    }

    fun skipQuotedString() {
        if (nextNonWhitespace() != C.QUOTE_INT) {
            throwError(C.ERR_EXPECTED_QUOTE)
        }

        val start = position
        val end = findClosingQuote(start, limit)
        if (end != -1) {
            position = end + 1
            return
        }

        var pos = start
        while (pos < limit) {
            val byteValue = rawData[pos++].code
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
                val escaped = rawData[pos++].code

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

    private fun parseUnicodeHex(currentPosition: Int): Int {
        val hexByte0 = rawData[currentPosition].code
        val hexByte1 = rawData[currentPosition + 1].code
        val hexByte2 = rawData[currentPosition + 2].code
        val hexByte3 = rawData[currentPosition + 3].code

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

    @InternalGhostApi
    inline fun <T> decodeResilient(crossinline block: () -> T): T? {
        val savedPos = position
        val savedToken = nextTokenByte
        val savedDepth = depth
        val savedNeedsCommaMask = needsCommaMask
        val savedCommaConsumedMask = commaConsumedMask
        try {
            return block()
        } catch (_: GhostJsonException) {
            position = savedPos
            nextTokenByte = savedToken
            depth = savedDepth
            needsCommaMask = savedNeedsCommaMask
            commaConsumedMask = savedCommaConsumedMask
            skipValue()
            return null
        }
    }

    /**
     * Computes a cheap hash from the first four char code-points of the string content.
     * Mirrors the hash used in [GhostJsonFlatReader] for byte sources.
     */
    private fun computeStringPoolHash(start: Int, length: Int): Int {
        val localData = rawData
        return if (length >= 4) {
            localData[start].code or
                (localData[start + 1].code shl C.SHIFT_8) or
                (localData[start + 2].code shl C.SHIFT_16) or
                (localData[start + 3].code shl C.SHIFT_24)
        } else {
            var key = 0
            if (length >= 1) key = key or localData[start].code
            if (length >= 2) key = key or (localData[start + 1].code shl C.SHIFT_8)
            if (length >= 3) key = key or (localData[start + 2].code shl C.SHIFT_16)
            key
        }
    }

    /**
     * Returns true if [rawData]`[start, start+length)` equals [cached] char-by-char.
     * No 7-bit restriction needed: rawData chars are already UTF-16.
     */
    private fun poolContentEquals(start: Int, length: Int, cached: String): Boolean {
        if (cached.length != length) return false
        val data = rawData
        for (i in 0 until length) {
            if (cached[i] != data[start + i]) return false
        }
        return true
    }

}

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

    /** Reused CharArray cache to bypass String.charAt overhead. */
    var rawChars: CharArray

    /** Cross-call string intern pool — same design as [GhostJsonFlatReader.stringPool]. */
    val stringPool: Array<String?> = arrayOfNulls(C.STR_POOL_SIZE)
    val stringPoolHashes = IntArray(C.STR_POOL_SIZE)

    // Reusable CharArray for decoding escapes in readQuotedString slow-path
    var slowPathChars: CharArray = CharArray(256)

    private fun growSlowPathChars(current: CharArray, requiredSize: Int): CharArray {
        val newSize = (current.size * 2).coerceAtLeast(requiredSize)
        val newArray = CharArray(newSize)
        current.copyInto(newArray, 0, 0, current.size)
        slowPathChars = newArray
        return newArray
    }

    init {
        val len = rawData.length
        val chars = CharArray(len)
        rawData.copyRangeToCharArray(chars, 0, 0, len)
        rawChars = chars
    }

    inline fun getByte(index: Int): Int {
        return rawChars[index].code
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
                val chars = rawChars
                while (byteIndex < errorEnd) {
                    if (chars[byteIndex].code == C.NEWLINE_INT) {
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
        var scanPosition = position
        val localLimit = limit
        val chars = rawChars
        val localSpaceInt = C.SPACE_INT
        val localWhitespaceMask = C.WHITESPACE_MASK
        val localByteShiftUnit = C.BYTE_SHIFT_UNIT
        val localResultNone = C.RESULT_NONE
        while (scanPosition < localLimit) {
            val code = chars[scanPosition].code
            if (
                code <= localSpaceInt &&
                ((localWhitespaceMask shr code) and localByteShiftUnit) !=
                localResultNone
            ) {
                scanPosition++
            } else {
                position = scanPosition
                nextTokenByte = code
                return
            }
        }
        position = localLimit
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
        val chars = rawChars
        for (i in 0 until size) {
            if (chars[position + i].code != (expected[i].toInt() and C.BYTE_MASK)) {
                throwError(C.ERR_EXPECTED_LITERAL + expected.utf8())
            }
        }
        position += size
        nextTokenByte = C.RESET_TOKEN_BYTE
    }

    @Suppress("StringReferentialEquality")
    fun reset(newData: String, newLimit: Int = newData.length) {
        val oldData = this.rawData
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

        if (newData !== oldData) {
            val len = newData.length
            var chars = rawChars
            if (chars.size < len) {
                chars = CharArray(len)
                rawChars = chars
            }
            newData.copyRangeToCharArray(chars, 0, 0, len)
        }
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
                val poolKey = hash xor (length * C.STR_POOL_HASH_MULTIPLIER)
                val bucketIndex = poolKey and (C.STR_POOL_SIZE - 1)
                if (stringPoolHashes[bucketIndex] == poolKey) {
                    val cached = stringPool[bucketIndex]
                    if (cached != null && poolContentEquals(start, length, cached)) {
                        return cached  // zero allocation — reuse existing String object
                    }
                }
                val newString = rawData.substring(start, start + length)
                stringPool[bucketIndex] = newString
                stringPoolHashes[bucketIndex] = poolKey
                return newString
            }
            return rawData.substring(start, start + length)
        }

        return readQuotedStringSlow(start)
    }

    private fun readQuotedStringSlow(start: Int): String {
        var outChars = slowPathChars
        var outPos = 0

        var startPosition = start
        val chars = rawChars

        val localQuoteInt = C.QUOTE_INT
        val localControlCharStartInt = C.CONTROL_CHAR_START_INT
        val localControlCharLimitInt = C.CONTROL_CHAR_LIMIT_INT
        val localBackslashInt = C.BACKSLASH_INT
        val localUnicodePrefixUInt = C.UNICODE_PREFIX_U_INT
        val localUnicodeHexLength = C.UNICODE_HEX_LENGTH
        val localHighSurrogateStart = C.HIGH_SURROGATE_START
        val localHighSurrogateEnd = C.HIGH_SURROGATE_END
        val localSurrogateOffset = C.SURROGATE_OFFSET
        val localUnicodeEscapePrefixSize = C.UNICODE_ESCAPE_PREFIX_SIZE
        val localLowSurrogateStart = C.LOW_SURROGATE_START
        val localLowSurrogateEnd = C.LOW_SURROGATE_END
        val localNByteInt = C.N_BYTE_INT
        val localRByteInt = C.R_BYTE_INT
        val localTByteInt = C.T_BYTE_INT
        val localBByteInt = C.B_BYTE_INT
        val localFByteInt = C.F_BYTE_INT

        while (startPosition < limit) {
            val byteValue = chars[startPosition++].code
            if (byteValue == localQuoteInt) {
                position = startPosition
                nextTokenByte = C.RESET_TOKEN_BYTE
                return outChars.concatToString(0, outPos)
            }

            if (byteValue in localControlCharStartInt..localControlCharLimitInt) {
                position = startPosition
                throwError(C.UNESCAPED_CONTROL_CHAR_ERROR)
            }

            if (byteValue == localBackslashInt) {
                if (startPosition >= limit) {
                    position = startPosition
                    throwError(C.UNTERMINATED_ESCAPE_ERROR)
                }
                when (val escaped = chars[startPosition++].code) {
                    localUnicodePrefixUInt -> {
                        if (startPosition + localUnicodeHexLength > limit) {
                            position = startPosition
                            throwError(C.UNTERMINATED_UNICODE_ERROR)
                        }

                        val code = parseUnicodeHex(startPosition)
                        startPosition += localUnicodeHexLength

                        if (code in localHighSurrogateStart..localHighSurrogateEnd) {
                            if (startPosition + localSurrogateOffset <= limit &&
                                chars[startPosition].code == localBackslashInt &&
                                chars[startPosition + C.SINGLE_CHAR_SIZE].code == localUnicodePrefixUInt
                            ) {
                                startPosition += localUnicodeEscapePrefixSize
                                val lowCode = parseUnicodeHex(startPosition)
                                if (lowCode in localLowSurrogateStart..localLowSurrogateEnd) {
                                    startPosition += localUnicodeHexLength
                                    if (outPos + 2 > outChars.size) {
                                        outChars = growSlowPathChars(outChars, outPos + 2)
                                    }
                                    outChars[outPos++] = code.toChar()
                                    outChars[outPos++] = lowCode.toChar()
                                } else {
                                    position = startPosition
                                    throwError(C.ERR_HIGH_SURROGATE)
                                }
                            } else {
                                position = startPosition
                                throwError(C.ERR_HIGH_SURROGATE)
                            }
                        } else {
                            if (outPos + 1 > outChars.size) {
                                outChars = growSlowPathChars(outChars, outPos + 1)
                            }
                            outChars[outPos++] = code.toChar()
                        }
                    }

                    localNByteInt -> {
                        if (outPos + 1 > outChars.size) {
                            outChars = growSlowPathChars(outChars, outPos + 1)
                        }
                        outChars[outPos++] = C.LF_CHAR
                    }

                    localRByteInt -> {
                        if (outPos + 1 > outChars.size) {
                            outChars = growSlowPathChars(outChars, outPos + 1)
                        }
                        outChars[outPos++] = C.CR_CHAR
                    }

                    localTByteInt -> {
                        if (outPos + 1 > outChars.size) {
                            outChars = growSlowPathChars(outChars, outPos + 1)
                        }
                        outChars[outPos++] = C.TAB_CHAR
                    }

                    localBByteInt -> {
                        if (outPos + 1 > outChars.size) {
                            outChars = growSlowPathChars(outChars, outPos + 1)
                        }
                        outChars[outPos++] = C.BS_CHAR
                    }

                    localFByteInt -> {
                        if (outPos + 1 > outChars.size) {
                            outChars = growSlowPathChars(outChars, outPos + 1)
                        }
                        outChars[outPos++] = C.FF_CHAR
                    }

                    else -> {
                        if (outPos + 1 > outChars.size) {
                            outChars = growSlowPathChars(outChars, outPos + 1)
                        }
                        outChars[outPos++] = escaped.toChar()
                    }
                }
            } else {
                if (outPos + 1 > outChars.size) {
                    outChars = growSlowPathChars(outChars, outPos + 1)
                }
                outChars[outPos++] = chars[startPosition - 1]
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

        var scanPosition = start
        val chars = rawChars

        val localQuoteInt = C.QUOTE_INT
        val localControlCharStartInt = C.CONTROL_CHAR_START_INT
        val localControlCharLimitInt = C.CONTROL_CHAR_LIMIT_INT
        val localBackslashInt = C.BACKSLASH_INT
        val localUnicodePrefixUInt = C.UNICODE_PREFIX_U_INT
        val localUnicodeHexLength = C.UNICODE_HEX_LENGTH

        while (scanPosition < limit) {
            val byteValue = chars[scanPosition++].code
            if (byteValue == localQuoteInt) {
                position = scanPosition
                nextTokenByte = C.RESET_TOKEN_BYTE
                return
            }

            if (byteValue in localControlCharStartInt..localControlCharLimitInt) {
                position = scanPosition
                throwError(C.UNESCAPED_CONTROL_CHAR_ERROR)
            }

            if (byteValue == localBackslashInt) {
                if (scanPosition >= limit) {
                    position = scanPosition
                    throwError(C.UNTERMINATED_ESCAPE_ERROR)
                }
                val escaped = chars[scanPosition++].code

                if (escaped == localUnicodePrefixUInt) {
                    if (scanPosition + localUnicodeHexLength > limit) {
                        position = scanPosition
                        throwError(C.UNTERMINATED_UNICODE_ERROR)
                    }
                    parseUnicodeHex(scanPosition)
                    scanPosition += localUnicodeHexLength
                }
            }
        }
        position = scanPosition
        throwError(C.UNTERMINATED_STRING_ERROR)
    }

    private fun parseUnicodeHex(currentPosition: Int): Int {
        val chars = rawChars
        val hexByte0 = chars[currentPosition].code
        val hexByte1 = chars[currentPosition + 1].code
        val hexByte2 = chars[currentPosition + 2].code
        val hexByte3 = chars[currentPosition + 3].code

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
        val chars = rawChars
        return if (length >= 4) {
            chars[start].code or
                    (chars[start + 1].code shl C.SHIFT_8) or
                    (chars[start + 2].code shl C.SHIFT_16) or
                    (chars[start + 3].code shl C.SHIFT_24)
        } else {
            var key = 0
            if (length >= 1) key = key or chars[start].code
            if (length >= 2) key = key or (chars[start + 1].code shl C.SHIFT_8)
            if (length >= 3) key = key or (chars[start + 2].code shl C.SHIFT_16)
            key
        }
    }

    /**
     * Returns true if [rawData]`[start, start+length)` equals [cached] char-by-char.
     * No 7-bit restriction needed: rawData chars are already UTF-16.
     */
    private fun poolContentEquals(start: Int, length: Int, cached: String): Boolean {
        if (cached.length != length) return false
        val chars = rawChars
        for (i in 0 until length) {
            if (cached[i] != chars[start + i]) return false
        }
        return true
    }

}

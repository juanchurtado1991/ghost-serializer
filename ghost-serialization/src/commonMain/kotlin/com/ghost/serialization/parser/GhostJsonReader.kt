package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.GhostJsonConstants.BYTE_MASK
import com.ghost.serialization.parser.GhostJsonConstants.QUOTE_INT
import com.ghost.serialization.parser.GhostJsonConstants.UNTERMINATED_STRING_ERROR
import okio.BufferedSource
import okio.ByteString

/**
 * High-performance, zero-allocation JSON reader designed for machine-generated code.
 *
 * This reader operates on an [Int]-based (0-255) data access pattern to eliminate
 * sign-extension overhead and JIT-deoptimizing conversions. It is the primary
 * interface used by Ghost-generated serializers (KSP).
 */
@InternalGhostApi
class GhostJsonReader(
    @PublishedApi internal var source: GhostSource,
    @PublishedApi internal var limit: Int = source.size,
    var maxDepth: Int = 255,
    var strictMode: Boolean = false,
    var coerceStringsToNumbers: Boolean = false,
    var maxCollectionSize: Int = GhostHeuristics.maxCollectionSize
) {
    /** Cached raw byte array for fast-path access. Eliminates interface dispatch. */
    @PublishedApi
    internal var rawData: ByteArray? = source.rawSourceData

    /**
     * Optimized byte access. Uses hardware-level zero-extension if rawData is available.
     */
    @PublishedApi
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun getByte(
        index: Int
    ): Int = if (rawData != null) {
        rawData!![index].toInt() and BYTE_MASK
    } else {
        source[index]
    }

    /** Branchless digit check using bitmask. */
    @PublishedApi
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun isDigit(byteCode: Int): Boolean {
        return (GhostJsonConstants.DIGIT_BITMASK shr byteCode) and 1L != 0L
    }

    /** Convenience constructor for ByteArray — used by KSP-generated serializers and tests. */
    constructor(
        bytes: ByteArray,
        maxDepth: Int = 255,
        strictMode: Boolean = false,
        coerceStringsToNumbers: Boolean = false,
        maxCollectionSize: Int = GhostHeuristics.maxCollectionSize
    ) : this(
        createByteArraySource(bytes),
        bytes.size,
        maxDepth,
        strictMode,
        coerceStringsToNumbers,
        maxCollectionSize
    )

    /** New Streaming Constructor for Okio Source */
    constructor(
        okioSource: BufferedSource,
        maxDepth: Int = 255,
        strictMode: Boolean = false,
        coerceStringsToNumbers: Boolean = false,
        maxCollectionSize: Int = GhostHeuristics.maxCollectionSize
    ) : this(
        createSourceBridge(okioSource),
        Int.MAX_VALUE, // Limit is unknown for streaming
        maxDepth,
        strictMode,
        coerceStringsToNumbers,
        maxCollectionSize
    )

    @PublishedApi
    internal var position: Int = 0

    @PublishedApi
    internal var nextTokenByte: Int = -1

    /** Current nesting depth (object/array). Incremented on begin*, decremented on end*. */
    var depth: Int = 0

    @PublishedApi
    internal val stringPool = arrayOfNulls<String>(GhostJsonConstants.STR_POOL_SIZE)

    fun throwError(message: String): Nothing {
        var line = 0
        var column = 0
        val endPosition = if (position > source.size) {
            source.size
        } else {
            position
        }
        var index = 0
        while (index < endPosition) {
            if (getByte(index) == GhostJsonConstants.NEWLINE_INT) {
                line++
                column = 0
            } else {
                column++
            }
            index++
        }
        throw GhostJsonException(
            "$message at position $position [line $line, col $column]",
            line,
            column
        )
    }

    /**
     * Consumes the next non-whitespace byte and validates it against [expected].
     * Primarily used for manual parsing and testing.
     */
    fun expectByte(expected: Int) {
        if (peekNextToken() != expected) {
            throwError(
                "Expected '${
                    expected.toChar()
                }' but found ${nextTokenByte.toChar()}"
            )
        }
        internalSkip(1)
    }

    fun internalSkip(n: Int) {
        position += n
        nextTokenByte = -1
    }

    fun skipWhitespace() {
        val nextPos = source.findNextNonWhitespace(position, limit)
        if (nextPos != -1) {
            position = nextPos
            nextTokenByte = getByte(position)
        } else {
            position = limit
            nextTokenByte = -1
        }
    }

    fun peekNextToken(): Int {
        if (nextTokenByte != -1) return nextTokenByte
        skipWhitespace()
        if (position >= limit) {
            nextTokenByte = GhostJsonConstants.MATCH_END
            return GhostJsonConstants.MATCH_END
        }
        nextTokenByte = getByte(position)
        return nextTokenByte
    }

    fun peekByte(): Byte = peekNextToken().toByte()

    fun nextNonWhitespace(): Int {
        val nextToken = peekNextToken()

        if (nextToken == -1) {
            throwError(GhostJsonConstants.ERR_UNEXPECTED_EOF)
        }

        internalSkip(1)
        return nextToken
    }

    @InternalGhostApi
    fun skipAndValidateLiteral(expected: ByteString) {
        if (!source.contentEquals(position, expected)) {
            throwError("Expected literal ${expected.utf8()}")
        }

        position += expected.size
        nextTokenByte = -1
    }

    fun skipAndValidateLiteral(expected: ByteArray) {
        val byteString = ByteString.of(*expected)
        skipAndValidateLiteral(byteString)
    }

    /**
     * Reads a quoted JSON string and returns it as a Kotlin String.
     * The compiler uses this for all string-type fields. This implementation
     * features a fast-path for pool-cached strings and a StringBuilder-based slow-path for escapes.
     */
    fun readQuotedString(): String {
        if (peekNextToken() != QUOTE_INT) {
            throwError(GhostJsonConstants.ERR_EXPECTED_QUOTE)
        }

        position++
        nextTokenByte = -1

        val start = position
        val rollingHash = source.scanString(start, limit, this)

        if (rollingHash != -1) {
            val end = position
            val length = end - start
            if (length <= 0) {
                position = end + 1
                return ""
            }
            if (length > GhostHeuristics.maxStringPoolLength) {
                val result = source.decodeToString(start, end)
                position = end + 1
                return result
            }

            val poolBucketIndex = rollingHash and (GhostJsonConstants.STR_POOL_SIZE - 1)
            val cachedString = stringPool[poolBucketIndex]

            if (cachedString != null && cachedString.length == length) {
                var isMatch = true
                var charIdx = 0
                while (charIdx < length) {
                    if (cachedString[charIdx].code != getByte(start + charIdx)) {
                        isMatch = false; break
                    }
                    charIdx++
                }

                if (isMatch) {
                    position = end + 1
                    return cachedString
                }
            }

            val decodedString = source.decodeToString(start, end)
            stringPool[poolBucketIndex] = decodedString
            position = end + 1
            return decodedString
        }

        // Slow path: manual string building for escapes (Zero-Allocation approach)
        val stringBuilder = StringBuilder()

        while (position < limit) {
            val byteValue = getByte(position++)
            if (byteValue == QUOTE_INT) {
                nextTokenByte = -1
                return stringBuilder.toString()
            }

            if (byteValue in GhostJsonConstants.CONTROL_CHAR_START_INT..GhostJsonConstants.CONTROL_CHAR_LIMIT_INT) {
                throwError(GhostJsonConstants.UNESCAPED_CONTROL_CHAR_ERROR)
            }

            if (byteValue == GhostJsonConstants.BACKSLASH_INT) {
                if (position >= limit) throwError(GhostJsonConstants.UNTERMINATED_ESCAPE_ERROR)
                val escaped = getByte(position++)
                when (escaped) {
                    GhostJsonConstants.UNICODE_PREFIX_U_INT -> {
                        if (position + GhostJsonConstants.UNICODE_HEX_LENGTH > limit) {
                            throwError(GhostJsonConstants.UNTERMINATED_UNICODE_ERROR)
                        }

                        var code = parseUnicodeHex(position)
                        position += GhostJsonConstants.UNICODE_HEX_LENGTH

                        if (code in GhostJsonConstants.HIGH_SURROGATE_START..GhostJsonConstants.HIGH_SURROGATE_END) {
                            if (
                                position + GhostJsonConstants.SURROGATE_OFFSET > limit ||
                                source[position] != GhostJsonConstants.BACKSLASH_INT ||
                                source[position + 1] != GhostJsonConstants.UNICODE_PREFIX_U_INT
                            ) {
                                throwError("Lone high surrogate")
                            }
                            position += 2
                            val lowCode = parseUnicodeHex(position)

                            if (lowCode !in GhostJsonConstants.LOW_SURROGATE_START..GhostJsonConstants.LOW_SURROGATE_END) {
                                throwError("Lone high surrogate")
                            }

                            position += GhostJsonConstants.UNICODE_HEX_LENGTH
                            code = GhostJsonConstants.UNICODE_BASE +
                                    ((code - GhostJsonConstants.HIGH_SURROGATE_START) shl GhostJsonConstants.SHIFT_10) +
                                    (lowCode - GhostJsonConstants.LOW_SURROGATE_START)
                        }

                        if (code <= GhostJsonConstants.BMP_LIMIT) {
                            stringBuilder.append(code.toChar())
                        } else {
                            val base = code - GhostJsonConstants.UNICODE_BASE
                            stringBuilder.append((GhostJsonConstants.HIGH_SURROGATE_START or (base shr GhostJsonConstants.SHIFT_10)).toChar())
                            stringBuilder.append((GhostJsonConstants.LOW_SURROGATE_START or (base and GhostJsonConstants.SURROGATE_LOW_BITS_MASK)).toChar())
                        }
                    }

                    GhostJsonConstants.N_BYTE_INT -> stringBuilder.append('\n')
                    GhostJsonConstants.R_BYTE_INT -> stringBuilder.append('\r')
                    GhostJsonConstants.T_BYTE_INT -> stringBuilder.append('\t')
                    GhostJsonConstants.B_BYTE_INT -> stringBuilder.append('\b')
                    GhostJsonConstants.F_BYTE_INT -> stringBuilder.append('\u000C')
                    else -> stringBuilder.append(escaped.toChar())
                }
            } else {
                stringBuilder.append(byteValue.toChar())
            }
        }
        throwError(UNTERMINATED_STRING_ERROR)
    }


    /**
     * Skips a quoted JSON string without decoding its content.
     * Used by generated code to skip unknown fields in non-strict mode.
     */
    fun skipQuotedString() {
        if (peekNextToken() != QUOTE_INT) {
            throwError(GhostJsonConstants.ERR_EXPECTED_QUOTE)
        }
        position++
        nextTokenByte = -1

        val start = position
        val end = source.findClosingQuote(start, limit)
        if (end != -1) {
            position = end + 1
            return
        }

        while (position < limit) {
            val byteValue = getByte(position++)
            if (byteValue == QUOTE_INT) {
                nextTokenByte = -1
                return
            }

            if (byteValue in GhostJsonConstants.CONTROL_CHAR_START_INT..GhostJsonConstants.CONTROL_CHAR_LIMIT_INT) {
                throwError(GhostJsonConstants.UNESCAPED_CONTROL_CHAR_ERROR)
            }

            if (byteValue == GhostJsonConstants.BACKSLASH_INT) {
                if (position >= limit) throwError(GhostJsonConstants.UNTERMINATED_ESCAPE_ERROR)
                val escaped = getByte(position++)

                if (escaped == GhostJsonConstants.UNICODE_PREFIX_U_INT) {
                    if (position + GhostJsonConstants.UNICODE_HEX_LENGTH > limit) {
                        throwError(GhostJsonConstants.UNTERMINATED_UNICODE_ERROR)
                    }
                    parseUnicodeHex(position)
                    position += GhostJsonConstants.UNICODE_HEX_LENGTH
                }
            }
        }

        throwError(UNTERMINATED_STRING_ERROR)
    }

    private fun parseUnicodeHex(currentPosition: Int): Int {
        val hexByte0 = getByte(currentPosition)
        val hexByte1 = getByte(currentPosition + 1)
        val hexByte2 = getByte(currentPosition + 2)
        val hexByte3 = getByte(currentPosition + 3)

        val hexLookupTable = GhostJsonConstants.HEX_LUT
        val digitValue0 = hexLookupTable[hexByte0]
        val digitValue1 = hexLookupTable[hexByte1]
        val digitValue2 = hexLookupTable[hexByte2]
        val digitValue3 = hexLookupTable[hexByte3]

        if ((digitValue0 or digitValue1 or digitValue2 or digitValue3) < 0) {
            throwError("Invalid unicode escape at $currentPosition")
        }

        return (digitValue0 shl GhostJsonConstants.SHIFT_12) or
                (digitValue1 shl GhostJsonConstants.SHIFT_8) or
                (digitValue2 shl GhostJsonConstants.SHIFT_4) or
                digitValue3
    }

    fun reset(newData: ByteArray, newLimit: Int = newData.size) {
        reset(createByteArraySource(newData), newLimit)
    }

    fun reset(okioSource: BufferedSource) {
        reset(createSourceBridge(okioSource), Int.MAX_VALUE)
    }

    fun reset(newSource: GhostSource, newLimit: Int = newSource.size) {
        this.source = newSource
        this.rawData = newSource.rawSourceData
        this.position = 0
        this.limit = newLimit
        this.nextTokenByte = -1
        this.depth = 0
        this.strictMode = false
        this.coerceStringsToNumbers = false
        this.maxDepth = 255
        this.maxCollectionSize = GhostHeuristics.maxCollectionSize
    }
}
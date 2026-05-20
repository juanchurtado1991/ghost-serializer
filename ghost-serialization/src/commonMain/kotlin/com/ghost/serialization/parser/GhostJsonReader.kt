@file:OptIn(InternalGhostApi::class)
@file:Suppress("FunctionName", "unused")

package com.ghost.serialization.parser

import com.ghost.serialization.parser.GhostJsonConstants as C

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.releaseScratchBuffer
import okio.BufferedSource
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

/**
 * A high-performance, zero-allocation JSON parser for Kotlin Multiplatform.
 *
 * This reader is optimized for generated code and raw speed by:
 * - Using an [Int]-based (0-255) data access pattern to eliminate sign-extension overhead.
 * - Implementing a "Fast-Path" that operates directly on [ByteArray] when possible.
 * - Reusing strings via an internal [stringPool] to reduce memory pressure.
 * - Providing a "Discriminator Peeker" for ultra-fast polymorphic deserialization.
 * - Minimizing virtual dispatch by caching the raw source data.
 */
class GhostJsonReader(
    @PublishedApi internal var source: GhostSource,
    @PublishedApi internal var limit: Int = source.size,
    var maxDepth: Int = C.MAX_DEPTH,
    var strictMode: Boolean = false,
    var coerceStringsToNumbers: Boolean = false,
    var coerceBooleans: Boolean = false,
    var maxCollectionSize: Int = GhostHeuristics.maxCollectionSize
) {

    /** Cached raw byte array for fast-path access. Eliminates interface dispatch. */
    @PublishedApi
    internal var rawData: ByteArray = source.rawSourceData

    @PublishedApi
    internal val isStreaming: Boolean = source is StreamingGhostSource

    @PublishedApi
    internal var position: Int = 0

    @PublishedApi
    internal var nextTokenByte: Int = -1

    @InternalGhostApi
    fun _getPosition(): Int = position

    @InternalGhostApi
    fun _setPosition(p: Int) {
        position = p
    }

    @InternalGhostApi
    fun _getRawData(): ByteArray = rawData

    @InternalGhostApi
    fun _setNextTokenByte(t: Int) {
        nextTokenByte = t
    }

    internal val stringPool = arrayOfNulls<String>(C.STR_POOL_SIZE)

    /**
     * Set during [GhostSource.scanString] fast path: false if any content byte had bit 7 set
     * (UTF-8 multibyte); true if only ASCII bytes were scanned (including empty string).
     */
    internal var lastScanContentWas7BitOnly: Boolean = false

    /** Current nesting depth (object/array).
     * Incremented on begin*, decremented on end*. */
    var depth: Int = 0

    /** Convenience constructor for ByteArray —
     * used by KSP-generated serializers and tests. */
    constructor(
        bytes: ByteArray,
        maxDepth: Int = C.MAX_DEPTH,
        strictMode: Boolean = false,
        coerceStringsToNumbers: Boolean = false,
        coerceBooleans: Boolean = false,
        maxCollectionSize: Int = GhostHeuristics.maxCollectionSize
    ) : this(
        createByteArraySource(bytes),
        bytes.size,
        maxDepth,
        strictMode,
        coerceStringsToNumbers,
        coerceBooleans,
        maxCollectionSize
    )

    /** New Streaming Constructor for Okio Source */
    constructor(
        okioSource: BufferedSource,
        maxDepth: Int = C.MAX_DEPTH,
        strictMode: Boolean = false,
        coerceStringsToNumbers: Boolean = false,
        coerceBooleans: Boolean = false,
        maxCollectionSize: Int = GhostHeuristics.maxCollectionSize
    ) : this(
        createSourceBridge(okioSource),
        Int.MAX_VALUE, // Limit is unknown for streaming
        maxDepth,
        strictMode,
        coerceStringsToNumbers,
        coerceBooleans,
        maxCollectionSize
    )

    /**
     * Optimized byte access.
     * Uses hardware-level zero-extension if [rawData] is available,
     * bypassing interface overhead.
     */
    @PublishedApi
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun getByte(index: Int): Int {
        if (isStreaming) {
            return source[index]
        }
        return rawData[index].toInt() and C.BYTE_MASK
    }

    /**
     * Helper to verify if the given byte is a valid JSON numeric digit.
     */
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun isDigit(byteCode: Int): Boolean {
        return (byteCode xor C.ZERO_INT) < C.BASE_TEN
    }

    /**
     * Throws a structured [GhostJsonException] with exact position, line, and column numbers.
     */
    fun throwError(message: String): Nothing {
        val errorPosition = position
        val sourceRef = source
        val errorEnd = if (errorPosition > sourceRef.size) {
            sourceRef.size
        } else {
            errorPosition
        }

        throw GhostJsonException(
            baseMessage = "$message at position $errorPosition",
            computeLineCol = {
                var columnNumber = 0
                var lineNumber = 0
                var byteIndex = 0
                while (byteIndex < errorEnd) {
                    if (sourceRef[byteIndex] == C.NEWLINE_INT) {
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

    /**
     * Skips [n] bytes and resets the cached [nextTokenByte].
     */
    fun internalSkip(n: Int) {
        position += n
        nextTokenByte = -1
    }

    /**
     * Advances the position past any whitespace and caches the next non-whitespace token byte.
     */
    fun skipWhitespace() {
        val nextPos = source.findNextNonWhitespace(
            position, limit
        )

        if (nextPos != -1) {
            position = nextPos
            nextTokenByte = getByte(position)
        } else {
            position = limit
            nextTokenByte = C.MATCH_END
        }
    }

    /**
     * Attempts to peek at the discriminator value (e.g. "type") of the current object.
     * Does not advance the reader's position.
     * Returns null if not found or if the current token is not an object start.
     * Used by KSP-generated serializers for polymorphic deserialization.
     */
    fun peekDiscriminator(key: String = C.DEFAULT_DISCRIMINATOR_KEY): String? {
        if (key == C.DEFAULT_DISCRIMINATOR_KEY) {
            return peekDiscriminator(C.TYPE_BS)
        }
        return peekDiscriminator(key.encodeUtf8())
    }

    /**
     * Internal version that takes a [ByteString] for maximum performance.
     * Used by KSP-generated serializers for polymorphic deserialization.
     */
    fun peekDiscriminator(key: ByteString): String? {
        return GhostDiscriminatorPeeker.peek(
            source,
            rawData,
            isStreaming,
            position,
            limit,
            key
        )
    }

    /**
     * Peeks and returns the next token byte in the stream, skipping preceding whitespaces.
     */
    fun peekNextToken(): Int {
        val cached = nextTokenByte
        if (cached != -1) {
            return cached
        }
        skipWhitespace()
        return nextTokenByte
    }

    /**
     * Peeks and returns the next token byte as a [Byte].
     */
    fun peekByte(): Byte = peekNextToken().toByte()

    /**
     * Consumes and returns the next non-whitespace token byte in the stream.
     */
    fun nextNonWhitespace(): Int {
        val nextToken = peekNextToken()
        if (nextToken == -1) {
            throwError(C.ERR_UNEXPECTED_EOF)
        }
        internalSkip(1)
        return nextToken
    }

    /**
     * Skips and validates that the next characters in the stream match the [expected] byte sequence.
     */
    @InternalGhostApi
    fun skipAndValidateLiteral(expected: ByteString) {
        if (!source.contentEquals(position, expected)) {
            throwError("Expected literal ${expected.utf8()}")
        }

        position += expected.size
        nextTokenByte = -1
    }

    /**
     * Reads a quoted JSON string.
     *
     * This implementation features:
     * 1. **Fast-path**: Direct decoding if no escapes are present.
     * 2. **String Pooling**: Checks [stringPool] to reuse existing String instances.
     * 3. **Slow-path**: StringBuilder-like approach using a pooled char buffer for escapes.
     */
    fun readQuotedString(): String {
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
                nextTokenByte = -1
                return ""
            }
            if (length > GhostHeuristics.maxStringPoolLength) {
                val result = source.decodeJsonStringRange(start, end, only7Bit)
                position = end + 1
                nextTokenByte = -1
                return result
            }

            val poolBucketIndex = rollingHash and (C.STR_POOL_SIZE - 1)
            val cachedString = stringPool[poolBucketIndex]

            if (only7Bit && cachedString != null && source.contentEqualsString(start, length, cachedString)) {
                position = end + 1
                nextTokenByte = -1
                return cachedString
            }

            val decodedString = source.decodeJsonStringRange(start, end, only7Bit)
            if (only7Bit) {
                stringPool[poolBucketIndex] = decodedString
            }
            position = end + 1
            nextTokenByte = -1
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
                    nextTokenByte = -1
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
     * Skips a double-quoted JSON string in the raw source without decoding its content.
     */
    fun skipQuotedString() {
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
                nextTokenByte = -1
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
    private fun parseUnicodeHex(currentPosition: Int): Int {
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

    /**
     * Resets the reader's state to process a new flat byte array payload.
     */
    fun reset(newData: ByteArray, newLimit: Int = newData.size) {
        val currentSource = this.source
        if (currentSource is ByteArrayGhostSource) {
            currentSource.data = newData
            reset(currentSource, newLimit)
        } else {
            reset(createByteArraySource(newData), newLimit)
        }
    }

    /**
     * Resets the reader's state to process a new streaming Okio [BufferedSource] payload.
     */
    fun reset(okioSource: BufferedSource) {
        reset(createSourceBridge(okioSource), Int.MAX_VALUE)
    }

    /**
     * Resets the reader state with a new [GhostSource].
     * Clears cached tokens, positions, and depth for reuse.
     */
    fun reset(newSource: GhostSource, newLimit: Int = newSource.size) {
        this.source = newSource
        this.rawData = newSource.rawSourceData
        this.position = 0
        this.limit = newLimit
        this.nextTokenByte = -1
        this.depth = 0
        this.strictMode = false
        this.coerceStringsToNumbers = false
        this.coerceBooleans = false
        this.maxDepth = C.MAX_DEPTH
        this.maxCollectionSize = GhostHeuristics.maxCollectionSize
        this.lastScanContentWas7BitOnly = false
    }
}

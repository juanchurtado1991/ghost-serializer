@file:OptIn(InternalGhostApi::class)

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
    @InternalGhostApi
    var rawData: ByteArray = source.rawSourceData

    @PublishedApi
    internal val isStreaming: Boolean = source is StreamingGhostSource

    @InternalGhostApi
    var position: Int = 0

    @InternalGhostApi
    var nextTokenByte: Int = -1

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
        if (isStreaming) return source[index]
        return rawData[index].toInt() and C.BYTE_MASK
    }

    /** Branchless digit check using bitmask. */
    @PublishedApi
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun isDigit(byteCode: Int): Boolean {
        return (C.DIGIT_BITMASK shr byteCode) and C.BYTE_SHIFT_UNIT != C.RESULT_NONE
    }

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

    fun peekNextToken(): Int {
        val cached = nextTokenByte
        if (cached != -1) return cached
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
            val length = ((scanResult and C.SCAN_LENGTH_MASK) ushr C.SCAN_LENGTH_SHIFT)
                .toInt()

            val rollingHash = scanResult.toInt()
            lastScanContentWas7BitOnly = (scanResult and C.SCAN_7BIT_BIT) != 0L
            val end = start + length
            if (length <= 0) {
                position = end + 1
                return ""
            }
            if (length > GhostHeuristics.maxStringPoolLength) {
                val result = source.decodeJsonStringRange(
                    start,
                    end,
                    lastScanContentWas7BitOnly
                )
                position = end + 1
                return result
            }

            val poolBucketIndex = rollingHash and (C.STR_POOL_SIZE - 1)
            val cachedString = stringPool[poolBucketIndex]

            if (
                cachedString != null &&
                source.contentEqualsString(start, length, cachedString)
            ) {
                position = end + 1
                return cachedString
            }

            val decodedString = source.decodeJsonStringRange(
                start,
                end,
                lastScanContentWas7BitOnly
            )

            stringPool[poolBucketIndex] = decodedString
            position = end + 1
            return decodedString
        }

        // Slow path: manual string building for escapes (Bitwise & Zero-Allocation approach)
        var outBuffer = acquireScratchBuffer(C.TIER_SMALL_INT)
        var outPos = 0

        fun ensureCapacity(extra: Int) {
            if (outPos + extra > outBuffer.size) {
                val newBuffer = acquireScratchBuffer(
                    outBuffer.size * C.BUFFER_SCALE_FACTOR
                )
                outBuffer.copyInto(
                    newBuffer,
                    0,
                    0,
                    outPos
                )
                releaseScratchBuffer(outBuffer)
                outBuffer = newBuffer
            }
        }

        try {
            while (position < limit) {
                val byteValue = getByte(position++)
                if (byteValue == C.QUOTE_INT) {
                    nextTokenByte = -1
                    return outBuffer.decodeToString(0, outPos)
                }

                if (byteValue in C.CONTROL_CHAR_START_INT..C.CONTROL_CHAR_LIMIT_INT) {
                    throwError(C.UNESCAPED_CONTROL_CHAR_ERROR)
                }

                if (byteValue == C.BACKSLASH_INT) {
                    if (position >= limit) throwError(C.UNTERMINATED_ESCAPE_ERROR)
                    val escaped = getByte(position++)
                    when (escaped) {
                        C.UNICODE_PREFIX_U_INT -> {
                            if (position + C.UNICODE_HEX_LENGTH > limit) {
                                throwError(C.UNTERMINATED_UNICODE_ERROR)
                            }

                            var code = parseUnicodeHex(position)
                            position += C.UNICODE_HEX_LENGTH

                            if (code in C.HIGH_SURROGATE_START..C.HIGH_SURROGATE_END) {
                                if (
                                    position + C.SURROGATE_OFFSET > limit ||
                                    getByte(position) == C.BACKSLASH_INT &&
                                    getByte(position + C.SINGLE_CHAR_SIZE) == C.UNICODE_PREFIX_U_INT
                                ) {
                                    // Valid surrogate pair check
                                    position += C.UNICODE_ESCAPE_PREFIX_SIZE
                                    val lowCode = parseUnicodeHex(position)
                                    if (lowCode in C.LOW_SURROGATE_START..C.LOW_SURROGATE_END) {
                                        position += C.UNICODE_HEX_LENGTH
                                        code = C.UNICODE_BASE +
                                                ((code - C.HIGH_SURROGATE_START) shl C.SHIFT_10) +
                                                (lowCode - C.LOW_SURROGATE_START)
                                    } else {
                                        throwError(C.ERR_HIGH_SURROGATE)
                                    }
                                } else {
                                    throwError(C.ERR_HIGH_SURROGATE)
                                }
                            }

                            // Encode code point to UTF-8 bytes in outBuffer
                            if (code <= C.UTF8_1BYTE_MAX) {
                                ensureCapacity(1)
                                outBuffer[outPos++] = code.toByte()
                            } else if (code <= C.UTF8_2BYTE_MAX) {
                                ensureCapacity(2)
                                outBuffer[outPos++] = (C.UTF8_2BYTE_PREFIX or (code shr C.UTF8_SHIFT_6)).toByte()
                                outBuffer[outPos++] = (C.UTF8_CONT_PREFIX or (code and C.UTF8_CONT_MASK)).toByte()
                            } else if (code <= C.BMP_LIMIT) {
                                ensureCapacity(3)
                                outBuffer[outPos++] = (C.UTF8_3BYTE_PREFIX or (code shr C.UTF8_SHIFT_12)).toByte()
                                outBuffer[outPos++] = (C.UTF8_CONT_PREFIX or ((code shr C.UTF8_SHIFT_6) and C.UTF8_CONT_MASK)).toByte()
                                outBuffer[outPos++] = (C.UTF8_CONT_PREFIX or (code and C.UTF8_CONT_MASK)).toByte()
                            } else {
                                ensureCapacity(4)
                                outBuffer[outPos++] = (C.UTF8_4BYTE_PREFIX or (code shr C.UTF8_SHIFT_18)).toByte()
                                outBuffer[outPos++] = (C.UTF8_CONT_PREFIX or ((code shr C.UTF8_SHIFT_12) and C.UTF8_CONT_MASK)).toByte()
                                outBuffer[outPos++] = (C.UTF8_CONT_PREFIX or ((code shr C.UTF8_SHIFT_6) and C.UTF8_CONT_MASK)).toByte()
                                outBuffer[outPos++] = (C.UTF8_CONT_PREFIX or (code and C.UTF8_CONT_MASK)).toByte()
                            }
                        }

                        C.N_BYTE_INT -> {
                            ensureCapacity(1)
                            outBuffer[outPos++] = C.LF_INT.toByte()
                        }

                        C.R_BYTE_INT -> {
                            ensureCapacity(1)
                            outBuffer[outPos++] = C.CR_INT.toByte()
                        }

                        C.T_BYTE_INT -> {
                            ensureCapacity(1)
                            outBuffer[outPos++] = C.TAB_INT.toByte()
                        }

                        C.B_BYTE_INT -> {
                            ensureCapacity(1)
                            outBuffer[outPos++] = C.BS_INT.toByte()
                        }

                        C.F_BYTE_INT -> {
                            ensureCapacity(1)
                            outBuffer[outPos++] = C.FF_INT.toByte()
                        }

                        else -> {
                            // Any other escaped char (like \", \\, \/) is written as its UTF-8 byte directly.
                            // Standard JSON only allows these, so they are all single-byte ASCII.
                            ensureCapacity(1)
                            outBuffer[outPos++] = escaped.toByte()
                        }
                    }
                } else {
                    // Normal byte (could be ASCII or part of UTF-8 sequence)
                    ensureCapacity(1)
                    outBuffer[outPos++] = byteValue.toByte()
                }
            }
        } finally {
            releaseScratchBuffer(outBuffer)
        }
        throwError(C.UNTERMINATED_STRING_ERROR)
    }


    /**
     * Skips a quoted JSON string without decoding its content.
     * Used by generated code to skip unknown fields in non-strict mode.
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

        while (position < limit) {
            val byteValue = getByte(position++)
            if (byteValue == C.QUOTE_INT) {
                nextTokenByte = -1
                return
            }

            if (byteValue in C.CONTROL_CHAR_START_INT..C.CONTROL_CHAR_LIMIT_INT) {
                throwError(C.UNESCAPED_CONTROL_CHAR_ERROR)
            }

            if (byteValue == C.BACKSLASH_INT) {
                if (position >= limit) throwError(C.UNTERMINATED_ESCAPE_ERROR)
                val escaped = getByte(position++)

                if (escaped == C.UNICODE_PREFIX_U_INT) {
                    if (position + C.UNICODE_HEX_LENGTH > limit) {
                        throwError(C.UNTERMINATED_UNICODE_ERROR)
                    }
                    parseUnicodeHex(position)
                    position += C.UNICODE_HEX_LENGTH
                }
            }
        }

        throwError(C.UNTERMINATED_STRING_ERROR)
    }

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

    fun reset(newData: ByteArray, newLimit: Int = newData.size) {
        val currentSource = this.source
        if (currentSource is ByteArrayGhostSource) {
            currentSource.data = newData
            reset(currentSource, newLimit)
        } else {
            reset(createByteArraySource(newData), newLimit)
        }
    }

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

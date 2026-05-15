@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireCharBuffer
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.GhostJsonConstants.BACKSLASH_INT
import com.ghost.serialization.parser.GhostJsonConstants.BMP_LIMIT
import com.ghost.serialization.parser.GhostJsonConstants.BUFFER_SCALE_FACTOR
import com.ghost.serialization.parser.GhostJsonConstants.BYTE_MASK
import com.ghost.serialization.parser.GhostJsonConstants.BYTE_SHIFT_UNIT
import com.ghost.serialization.parser.GhostJsonConstants.CONTROL_CHAR_LIMIT_INT
import com.ghost.serialization.parser.GhostJsonConstants.CONTROL_CHAR_START_INT
import com.ghost.serialization.parser.GhostJsonConstants.DIGIT_BITMASK
import com.ghost.serialization.parser.GhostJsonConstants.ERR_EXPECTED_QUOTE
import com.ghost.serialization.parser.GhostJsonConstants.ERR_HIGH_SURROGATE
import com.ghost.serialization.parser.GhostJsonConstants.ERR_UNEXPECTED_EOF
import com.ghost.serialization.parser.GhostJsonConstants.HIGH_SURROGATE_END
import com.ghost.serialization.parser.GhostJsonConstants.HIGH_SURROGATE_START
import com.ghost.serialization.parser.GhostJsonConstants.LOW_SURROGATE_END
import com.ghost.serialization.parser.GhostJsonConstants.LOW_SURROGATE_START
import com.ghost.serialization.parser.GhostJsonConstants.MATCH_END
import com.ghost.serialization.parser.GhostJsonConstants.NEWLINE_INT
import com.ghost.serialization.parser.GhostJsonConstants.QUOTE_INT
import com.ghost.serialization.parser.GhostJsonConstants.RESULT_NONE
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_10
import com.ghost.serialization.parser.GhostJsonConstants.SINGLE_CHAR_SIZE
import com.ghost.serialization.parser.GhostJsonConstants.STR_POOL_SIZE
import com.ghost.serialization.parser.GhostJsonConstants.SURROGATE_LOW_BITS_MASK
import com.ghost.serialization.parser.GhostJsonConstants.SURROGATE_PAIR_SIZE
import com.ghost.serialization.parser.GhostJsonConstants.TIER_SMALL_INT
import com.ghost.serialization.parser.GhostJsonConstants.UNESCAPED_CONTROL_CHAR_ERROR
import com.ghost.serialization.parser.GhostJsonConstants.UNICODE_BASE
import com.ghost.serialization.parser.GhostJsonConstants.UNICODE_ESCAPE_PREFIX_SIZE
import com.ghost.serialization.parser.GhostJsonConstants.UNICODE_HEX_LENGTH
import com.ghost.serialization.parser.GhostJsonConstants.UNICODE_PREFIX_U_INT
import com.ghost.serialization.parser.GhostJsonConstants.UNTERMINATED_ESCAPE_ERROR
import com.ghost.serialization.parser.GhostJsonConstants.UNTERMINATED_STRING_ERROR
import com.ghost.serialization.parser.GhostJsonConstants.UNTERMINATED_UNICODE_ERROR
import com.ghost.serialization.releaseCharBuffer
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
@InternalGhostApi
class GhostJsonReader(
    @PublishedApi internal var source: GhostSource,
    @PublishedApi internal var limit: Int = source.size,
    var maxDepth: Int = 255,
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

    @PublishedApi
    internal val stringPool = arrayOfNulls<String>(STR_POOL_SIZE)

    /**
     * Set during [GhostSource.scanString] fast path: false if any content byte had bit 7 set
     * (UTF-8 multibyte); true if only ASCII bytes were scanned (including empty string).
     */
    @PublishedApi
    internal var lastScanContentWas7BitOnly: Boolean = false

    /** Current nesting depth (object/array).
     * Incremented on begin*, decremented on end*. */
    var depth: Int = 0

    /** Convenience constructor for ByteArray —
     * used by KSP-generated serializers and tests. */
    constructor(
        bytes: ByteArray,
        maxDepth: Int = 255,
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
        maxDepth: Int = 255,
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
        return rawData[index].toInt() and BYTE_MASK
    }

    /** Branchless digit check using bitmask. */
    @PublishedApi
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun isDigit(byteCode: Int): Boolean {
        return (DIGIT_BITMASK shr byteCode) and BYTE_SHIFT_UNIT != RESULT_NONE
    }

    fun throwError(message: String): Nothing {
        val errorPosition = position
        val sourceRef = source
        val errorEnd = if (errorPosition > sourceRef.size) sourceRef.size else errorPosition

        throw GhostJsonException(
            baseMessage = "$message at position $errorPosition",
            computeLineCol = {
                var col = 0
                var ln = 0
                var i = 0
                while (i < errorEnd) {
                    if (sourceRef[i] == NEWLINE_INT) {
                        ln++
                        col = 0
                    } else {
                        col++
                    }
                    i++
                }
                intArrayOf(ln, col)
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
        val nextPos = source.findNextNonWhitespace(position, limit)
        if (nextPos != -1) {
            position = nextPos
            nextTokenByte = getByte(position)
        } else {
            position = limit
            nextTokenByte = MATCH_END
        }
    }

    /**
     * Attempts to peek at the discriminator value (e.g. "type") of the current object.
     * Does not advance the reader's position.
     * Returns null if not found or if the current token is not an object start.
     */
    fun peekDiscriminator(key: String = "type"): String? {
        if (key == "type") return peekDiscriminator(GhostJsonConstants.TYPE_BS)
        return peekDiscriminator(key.encodeUtf8())
    }

    /**
     * Internal version that takes a [ByteString] for maximum performance.
     */
    fun peekDiscriminator(key: ByteString): String? {
        return GhostDiscriminatorPeeker.peek(source, rawData, isStreaming, position, limit, key)
    }

    /**
     * Consumes and returns the current token byte.
     * If no token is cached, it skips whitespace and reads the next byte.
     */
    fun nextToken(): Int {
        if (nextTokenByte != -1) {
            val token = nextTokenByte
            nextTokenByte = -1
            return token
        }
        skipWhitespace()
        if (position >= limit) return MATCH_END
        return getByte(position)
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
            throwError(ERR_UNEXPECTED_EOF)
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
     * Reads a quoted JSON string.
     *
     * This implementation features:
     * 1. **Fast-path**: Direct decoding if no escapes are present.
     * 2. **String Pooling**: Checks [stringPool] to reuse existing String instances.
     * 3. **Slow-path**: StringBuilder-like approach using a pooled char buffer for escapes.
     */
    fun readQuotedString(): String {
        if (nextNonWhitespace() != QUOTE_INT) {
            throwError(ERR_EXPECTED_QUOTE)
        }

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
                val result = source.decodeJsonStringRange(start, end, lastScanContentWas7BitOnly)
                position = end + 1
                return result
            }

            val poolBucketIndex = rollingHash and (STR_POOL_SIZE - 1)
            val cachedString = stringPool[poolBucketIndex]

            if (
                cachedString != null &&
                source.contentEqualsString(start, length, cachedString)
            ) {
                position = end + 1
                return cachedString
            }

            val decodedString = source.decodeJsonStringRange(start, end, lastScanContentWas7BitOnly)
            stringPool[poolBucketIndex] = decodedString
            position = end + 1
            return decodedString
        }

        // Slow path: manual string building for escapes (Zero-Allocation approach)
        var outBuffer = acquireCharBuffer(TIER_SMALL_INT)
        var outPos = 0

        fun ensureCapacity(extra: Int) {
            if (outPos + extra > outBuffer.size) {
                val newBuffer = acquireCharBuffer(outBuffer.size * BUFFER_SCALE_FACTOR)
                outBuffer.copyInto(newBuffer, 0, 0, outPos)
                releaseCharBuffer(outBuffer)
                outBuffer = newBuffer
            }
        }

        try {
            while (position < limit) {
                val byteValue = getByte(position++)
                if (byteValue == QUOTE_INT) {
                    nextTokenByte = -1
                    return outBuffer.concatToString(0, outPos)
                }

                if (byteValue in CONTROL_CHAR_START_INT..CONTROL_CHAR_LIMIT_INT) {
                    throwError(UNESCAPED_CONTROL_CHAR_ERROR)
                }

                if (byteValue == BACKSLASH_INT) {
                    if (position >= limit) throwError(UNTERMINATED_ESCAPE_ERROR)
                    val escaped = getByte(position++)
                    when (escaped) {
                        UNICODE_PREFIX_U_INT -> {
                            if (position + UNICODE_HEX_LENGTH > limit) {
                                throwError(UNTERMINATED_UNICODE_ERROR)
                            }

                            var code = parseUnicodeHex(position)
                            position += UNICODE_HEX_LENGTH

                            if (code in HIGH_SURROGATE_START..HIGH_SURROGATE_END) {
                                if (
                                    position + GhostJsonConstants.SURROGATE_OFFSET > limit ||
                                    getByte(position) == BACKSLASH_INT &&
                                    getByte(position + SINGLE_CHAR_SIZE) == UNICODE_PREFIX_U_INT
                                ) {
                                    // Valid surrogate pair check
                                    position += UNICODE_ESCAPE_PREFIX_SIZE
                                    val lowCode = parseUnicodeHex(position)
                                    if (lowCode in LOW_SURROGATE_START..LOW_SURROGATE_END) {
                                        position += UNICODE_HEX_LENGTH
                                        code = UNICODE_BASE +
                                                ((code - HIGH_SURROGATE_START) shl SHIFT_10) +
                                                (lowCode - LOW_SURROGATE_START)
                                    } else {
                                        throwError(ERR_HIGH_SURROGATE)
                                    }
                                } else {
                                    throwError(ERR_HIGH_SURROGATE)
                                }
                            }

                            if (code <= BMP_LIMIT) {
                                ensureCapacity(SINGLE_CHAR_SIZE)
                                outBuffer[outPos++] = code.toChar()
                            } else {
                                ensureCapacity(SURROGATE_PAIR_SIZE)
                                val base = code - UNICODE_BASE
                                outBuffer[outPos++] =
                                    (HIGH_SURROGATE_START or (base shr SHIFT_10)).toChar()
                                outBuffer[outPos++] =
                                    (LOW_SURROGATE_START or (base and SURROGATE_LOW_BITS_MASK)).toChar()
                            }
                        }

                        GhostJsonConstants.N_BYTE_INT -> {
                            ensureCapacity(SINGLE_CHAR_SIZE)
                            outBuffer[outPos++] = '\n'
                        }

                        GhostJsonConstants.R_BYTE_INT -> {
                            ensureCapacity(SINGLE_CHAR_SIZE)
                            outBuffer[outPos++] = '\r'
                        }

                        GhostJsonConstants.T_BYTE_INT -> {
                            ensureCapacity(SINGLE_CHAR_SIZE)
                            outBuffer[outPos++] = '\t'
                        }

                        GhostJsonConstants.B_BYTE_INT -> {
                            ensureCapacity(SINGLE_CHAR_SIZE)
                            outBuffer[outPos++] = '\b'
                        }

                        GhostJsonConstants.F_BYTE_INT -> {
                            ensureCapacity(SINGLE_CHAR_SIZE)
                            outBuffer[outPos++] = '\u000C'
                        }

                        else -> {
                            ensureCapacity(SINGLE_CHAR_SIZE); outBuffer[outPos++] =
                                escaped.toChar()
                        }
                    }
                } else {
                    ensureCapacity(SINGLE_CHAR_SIZE)
                    outBuffer[outPos++] = byteValue.toChar()
                }
            }
        } finally {
            releaseCharBuffer(outBuffer)
        }
        throwError(UNTERMINATED_STRING_ERROR)
    }


    /**
     * Skips a quoted JSON string without decoding its content.
     * Used by generated code to skip unknown fields in non-strict mode.
     */
    fun skipQuotedString() {
        if (nextNonWhitespace() != QUOTE_INT) {
            throwError(ERR_EXPECTED_QUOTE)
        }

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

            if (byteValue in CONTROL_CHAR_START_INT..CONTROL_CHAR_LIMIT_INT) {
                throwError(UNESCAPED_CONTROL_CHAR_ERROR)
            }

            if (byteValue == BACKSLASH_INT) {
                if (position >= limit) throwError(UNTERMINATED_ESCAPE_ERROR)
                val escaped = getByte(position++)

                if (escaped == UNICODE_PREFIX_U_INT) {
                    if (position + UNICODE_HEX_LENGTH > limit) {
                        throwError(UNTERMINATED_UNICODE_ERROR)
                    }
                    parseUnicodeHex(position)
                    position += UNICODE_HEX_LENGTH
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
        this.maxDepth = 255
        this.maxCollectionSize = GhostHeuristics.maxCollectionSize
        this.lastScanContentWas7BitOnly = false
    }
}

@InternalGhostApi
@Suppress("NOTHING_TO_INLINE")
internal inline fun GhostJsonReader.beginUnescapedStringContentScan() {
    lastScanContentWas7BitOnly = true
}

/**
 * Marks non-ASCII UTF-8 content (high bit set). No allocation: [Byte] stays primitive on JVM.
 * Uses signed [Byte]: bit 7 set ⇒ negative.
 */
@InternalGhostApi
@Suppress("NOTHING_TO_INLINE")
internal inline fun GhostJsonReader.noteUnescapedStringContentByte(b: Byte) {
    if (b < 0) lastScanContentWas7BitOnly = false
}

/** Variant for [GhostSource.get] / streaming, where bytes are already unsigned 0–255 as [Int]. */
@InternalGhostApi
@Suppress("NOTHING_TO_INLINE")
internal inline fun GhostJsonReader.noteUnescapedStringContentByte(unsignedCode0To255: Int) {
    if (unsignedCode0To255 and 0x80 != 0) lastScanContentWas7BitOnly = false
}
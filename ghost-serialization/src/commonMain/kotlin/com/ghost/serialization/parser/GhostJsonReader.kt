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
        return readQuotedStringImpl(
            getPosition = { position },
            setPosition = { position = it },
            getLimit = { limit },
            getNextNonWhitespace = { nextNonWhitespace() },
            setNextTokenByte = { nextTokenByte = it },
            getByte = { getByte(it) },
            getStringPool = { stringPool },
            setLastScanContentWas7BitOnly = { lastScanContentWas7BitOnly = it },
            scanString = { p, lim -> source.scanString(p, lim) },
            decodeJsonStringRange = { s, e, asc -> source.decodeJsonStringRange(s, e, asc) },
            contentEqualsString = { s, len, str -> source.contentEqualsString(s, len, str) },
            parseUnicodeHex = { parseUnicodeHex(it) },
            throwError = { throwError(it) }
        )
    }


    /**
     * Skips a quoted JSON string without decoding its content.
     * Used by generated code to skip unknown fields in non-strict mode.
     */
    fun skipQuotedString() {
        skipQuotedStringImpl(
            getPosition = { position },
            setPosition = { position = it },
            getLimit = { limit },
            getNextNonWhitespace = { nextNonWhitespace() },
            setNextTokenByte = { nextTokenByte = it },
            getByte = { getByte(it) },
            findClosingQuote = { p, lim -> source.findClosingQuote(p, lim) },
            parseUnicodeHex = { parseUnicodeHex(it) },
            throwError = { throwError(it) }
        )
    }

    private fun parseUnicodeHex(currentPosition: Int): Int {
        return parseUnicodeHexImpl(
            currentPosition = currentPosition,
            getByte = { getByte(it) },
            throwError = { throwError(it) }
        )
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

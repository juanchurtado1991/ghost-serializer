@file:OptIn(InternalGhostApi::class)
@file:Suppress("FunctionName", "unused", "NOTHING_TO_INLINE")

package com.ghost.serialization.parser.streaming

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.exception.hintForJsonError
import com.ghost.serialization.parser.bytes.ByteArrayGhostSource
import com.ghost.serialization.parser.common.GhostDiscriminatorPeeker
import com.ghost.serialization.parser.common.GhostHeuristics
import com.ghost.serialization.parser.common.json.GhostJsonPathTracker
import com.ghost.serialization.parser.common.GhostSource
import com.ghost.serialization.parser.common.contentEqualsStringImpl
import com.ghost.serialization.parser.common.createByteArraySource
import com.ghost.serialization.parser.common.createSourceBridge
import com.ghost.serialization.parser.common.findClosingQuoteImpl
import com.ghost.serialization.parser.common.findNextNonWhitespaceImpl
import com.ghost.serialization.parser.common.growBuffer
import com.ghost.serialization.parser.common.json.readQuotedStringSlowCore
import com.ghost.serialization.parser.common.scanStringImpl
import com.ghost.serialization.parser.strings.beginObject
import okio.BufferedSource
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import com.ghost.serialization.parser.common.constants.GhostJsonErrorMessages as EM
import com.ghost.serialization.parser.common.constants.GhostJsonNumericLimits as NUM
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants as SCN
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants as WR

/**
 * A high-performance, zero-allocation JSON parser for Kotlin Multiplatform.
 *
 * This reader is optimized for generated code and raw speed by:
 * - Using an [Int]-based (0-255) data access pattern to eliminate sign-extension overhead.
 * - Implementing a "Fast-Path" that operates directly on [ByteArray] when possible.
 * - Reusing strings via an internal [stringPool] to reduce memory pressure.
 * - Providing a "Discriminator Peeker" for ultra-fast polymorphic deserialization.
 * - Minimizing virtual dispatch by caching the raw source data ([rawData], read via [getByte]).
 *
 * [strictMode] enables strict JSON validation (rejects unknown/unmapped fields, strict bitwise
 * comma syntax checks); defaults to false for maximum lenient parsing performance.
 * [lastScanContentWas7BitOnly] is set by [GhostSource.scanString]'s fast path: false if any
 * content byte had bit 7 set (UTF-8 multibyte), true for ASCII-only content (including empty).
 * [predictedFieldIndex] is an optimistic hint for the field-select fast path: the next expected
 * field index when JSON objects list fields in declaration order; reset on `beginObject`, and a
 * misprediction falls back to hashed dispatch, so it never affects correctness.
 * [pathTracker] holds JSONPath breadcrumbs, formatted only when [throwError] builds an exception.
 */
class GhostJsonReader(
    @PublishedApi internal var source: GhostSource,
    @PublishedApi internal var limit: Int = source.size,
    var maxDepth: Int = NUM.MAX_DEPTH,
    var strictMode: Boolean = false,
    var coerceStringsToNumbers: Boolean = false,
    var coerceBooleans: Boolean = false,
    var maxCollectionSize: Int = GhostHeuristics.maxCollectionSize
) {

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
    fun _getRawData(): ByteArray = rawData

    @InternalGhostApi
    fun _setNextTokenByte(tokenByte: Int) {
        nextTokenByte = tokenByte
    }

    @InternalGhostApi
    fun _setPosition(position: Int) {
        this.position = position
    }

    internal val stringPool = arrayOfNulls<String>(SCN.STR_POOL_SIZE)
    internal var lastScanContentWas7BitOnly: Boolean = false
    internal var predictedFieldIndex: Int = SCN.FIELD_PREDICTION_START
    var depth: Int = 0

    @PublishedApi
    internal var needsCommaMask: Long = 0L
    @PublishedApi
    internal var commaConsumedMask: Long = 0L

    @PublishedApi
    internal val pathTracker: GhostJsonPathTracker = GhostJsonPathTracker()

    /** Convenience constructor for [ByteArray] — used by KSP-generated serializers and tests. */
    constructor(
        bytes: ByteArray,
        maxDepth: Int = NUM.MAX_DEPTH,
        strictMode: Boolean = false,
        coerceStringsToNumbers: Boolean = false,
        coerceBooleans: Boolean = false,
        maxCollectionSize: Int = GhostHeuristics.maxCollectionSize
    ) : this(
        createByteArraySource(data = bytes),
        bytes.size,
        maxDepth,
        strictMode,
        coerceStringsToNumbers,
        coerceBooleans,
        maxCollectionSize
    )

    constructor(
        okioSource: BufferedSource,
        maxDepth: Int = NUM.MAX_DEPTH,
        strictMode: Boolean = false,
        coerceStringsToNumbers: Boolean = false,
        coerceBooleans: Boolean = false,
        maxCollectionSize: Int = GhostHeuristics.maxCollectionSize
    ) : this(
        createSourceBridge(source = okioSource),
        Int.MAX_VALUE, // Limit is unknown for streaming
        maxDepth,
        strictMode,
        coerceStringsToNumbers,
        coerceBooleans,
        maxCollectionSize
    )

    /** Consumes the next non-whitespace byte and validates it against [expected]; for manual parsing/tests. */
    fun expectByte(expected: Int) {
        if (peekNextToken() != expected) {
            throwError(
                "${EM.ERR_EXPECTED_CHAR_PREFIX}${Char(expected)}${EM.ERR_EXPECTED_CHAR_MID}" +
                    "${Char(nextTokenByte)}${EM.ERR_EXPECTED_CHAR_SUFFIX}"
            )
        }
        if (expected == TOK.COMMA_INT) {
            if (depth < SCN.MAX_BITMASK_DEPTH) {
                val bit = SCN.BITMASK_UNIT shl depth
                commaConsumedMask = commaConsumedMask or bit
                needsCommaMask = needsCommaMask and bit.inv()
            }
        }
        internalSkip(1)
    }

    @PublishedApi
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun getByte(index: Int): Int {
        if (isStreaming) return source[index]
        return rawData[index].toInt() and TOK.BYTE_MASK
    }

    fun internalSkip(byteCount: Int) {
        position += byteCount
        nextTokenByte = SCN.RESET_TOKEN_BYTE
    }

    fun nextNonWhitespace(): Int {
        val nextToken = peekNextToken()
        if (nextToken == -1) {
            throwError(EM.ERR_UNEXPECTED_EOF)
        }
        internalSkip(1)
        return nextToken
    }

    fun peekByte(): Byte = peekNextToken().toByte()

    /**
     * Peeks the discriminator value (e.g. `"type"`) of the current object without advancing;
     * `null` if not found or the current token isn't an object start. Used for polymorphic
     * deserialization in KSP-generated serializers.
     */
    fun peekDiscriminator(key: String = TOK.DEFAULT_DISCRIMINATOR_KEY): String? {
        if (key == TOK.DEFAULT_DISCRIMINATOR_KEY) {
            return peekDiscriminator(key = WR.TYPE_BS)
        }
        return peekDiscriminator(key = key.encodeUtf8())
    }

    fun peekDiscriminator(key: ByteString): String? {
        return GhostDiscriminatorPeeker.peek(
            source = source,
            rawData = rawData,
            isStreaming = isStreaming,
            start = position,
            limit = limit,
            key = key
        )
    }

    /** Returns the next token byte, skipping whitespace; cached until consumed. */
    fun peekNextToken(): Int {
        val cached = nextTokenByte
        if (cached != -1) return cached
        skipWhitespace()
        return nextTokenByte
    }

    /**
     * Reads a quoted JSON string: fast-path direct decode when unescaped, reusing instances
     * via [stringPool]; falls back to a pooled-char-buffer slow path when escapes are present.
     */
    fun readQuotedString(): String {
        if (nextNonWhitespace() != TOK.QUOTE_INT) {
            throwError(EM.ERR_EXPECTED_QUOTE)
        }

        val start = position
        val scanResult = if (isStreaming) {
            source.scanString(start = start, limit = limit)
        } else {
            val localData = rawData
            scanStringImpl(start = start, limit = limit) { localData[it].toInt() and TOK.BYTE_MASK }
        }

        if (scanResult != -1L) {
            val length = ((scanResult and SCN.SCAN_LENGTH_MASK) ushr SCN.SCAN_LENGTH_SHIFT).toInt()
            val rollingHash = scanResult.toInt()
            val only7Bit = (scanResult and SCN.SCAN_7BIT_BIT) != 0L
            lastScanContentWas7BitOnly = only7Bit
            val end = start + length
            if (length <= 0) {
                advancePastQuotedValue(end = end)
                return ""
            }
            if (length > GhostHeuristics.maxStringPoolLength) {
                val result = source.decodeJsonStringRange(start = start, end = end, isKnown7BitContent = only7Bit)
                advancePastQuotedValue(end = end)
                return result
            }

            val poolBucketIndex = rollingHash and (SCN.STR_POOL_SIZE - 1)
            val cachedString = stringPool[poolBucketIndex]

            if (only7Bit && cachedString != null) {
                val isMatch = if (isStreaming) {
                    source.contentEqualsString(start = start, length = length, expected = cachedString)
                } else {
                    val localData = rawData
                    contentEqualsStringImpl(
                        start = start,
                        length = length,
                        targetString = cachedString
                    ) { localData[it].toInt() and TOK.BYTE_MASK }
                }
                if (isMatch) {
                    advancePastQuotedValue(end = end)
                    return cachedString
                }
            }

            val decodedString = source.decodeJsonStringRange(start = start, end = end, isKnown7BitContent = only7Bit)
            if (only7Bit) {
                stringPool[poolBucketIndex] = decodedString
            }
            advancePastQuotedValue(end = end)
            return decodedString
        }

        return readQuotedStringSlow(start = start)
    }

    /**
     * Asks a [StreamingGhostSource] to skip Okio bytes already behind [position].
     * No-op for flat [ByteArray] sources. Safe to call after any forward-only advance;
     * ranges that may still be re-read must be [StreamingGhostSource.pin]ned first.
     */
    @PublishedApi
    internal fun releaseStreamingPrefix() {
        val streaming = source as? StreamingGhostSource ?: return
        val pos = position
        if (pos == Int.MAX_VALUE || pos <= 0) return
        streaming.releaseBefore(absoluteIndex = pos)
    }

    @InternalGhostApi
    fun skipAndValidateLiteral(expected: ByteString) {
        val size = expected.size
        val isValid = if (isStreaming) {
            source.contentEquals(start = position, expected = expected)
        } else {
            position + size <= limit && expected.rangeEquals(0, rawData, position, size)
        }
        if (!isValid) {
            throwError(EM.ERR_EXPECTED_LITERAL + expected.utf8())
        }

        position += size
        nextTokenByte = SCN.RESET_TOKEN_BYTE
    }

    /** Advances past whitespace and caches the next non-whitespace token byte. */
    fun skipWhitespace() {
        val nextPos = if (isStreaming) {
            source.findNextNonWhitespace(position = position, limit = limit)
        } else {
            val localData = rawData
            findNextNonWhitespaceImpl(position = position, limit = limit) { localData[it].toInt() and TOK.BYTE_MASK }
        }

        if (nextPos != -1) {
            position = nextPos
            nextTokenByte = getByte(position)
        } else {
            position = limit
            nextTokenByte = SCN.MATCH_END
        }
        releaseStreamingPrefix()
    }

    /** Throws a [GhostJsonException] with exact position, line, column, and JSONPath. */
    fun throwError(message: String): Nothing {
        val errorPosition = position
        val sourceRef = source
        val errorEnd = if (errorPosition > sourceRef.size) {
            sourceRef.size
        } else {
            errorPosition
        }
        val errorPath = pathTracker.formatPath()

        throw GhostJsonException(
            baseMessage = "$message${EM.ERR_AT_POSITION_PREFIX}$errorPosition",
            computeLineCol = {
                var columnNumber = 0
                var lineNumber = 0
                var byteIndex = 0
                while (byteIndex < errorEnd) {
                    if (sourceRef[byteIndex] == TOK.NEWLINE_INT) {
                        lineNumber++
                        columnNumber = 0
                    } else {
                        columnNumber++
                    }
                    byteIndex++
                }
                intArrayOf(lineNumber, columnNumber)
            },
            path = errorPath,
            hint = message.hintForJsonError(),
        )
    }

    /**
     * Throws for a missing required field, pushing [jsonName] onto the JSONPath so the
     * exception points at `$.….<jsonName>` (validation runs before [endObject]).
     */
    fun throwMissingRequiredField(jsonName: String): Nothing {
        pathTracker.pushKey(name = jsonName)
        throwError("${EM.ERR_REQUIRED_FIELD_PREFIX}$jsonName${EM.ERR_REQUIRED_FIELD_SUFFIX}")
    }

    private inline fun advancePastQuotedValue(end: Int) {
        position = end + 1
        nextTokenByte = SCN.RESET_TOKEN_BYTE
    }

    private fun GhostJsonReader.readQuotedStringSlow(start: Int): String =
        readQuotedStringSlowCore(
            start = start,
            limit = limit,
            getByte = { getByte(it) },
            setPosition = { position = it },
            setNextTokenByte = { nextTokenByte = it },
            parseUnicodeHex = { parseUnicodeHex(it) },
            grow = { buf, outPos -> growBuffer(outBuffer = buf, outPos = outPos) },
            throwError = { throwError(it) },
        )

    fun skipQuotedString() {
        if (nextNonWhitespace() != TOK.QUOTE_INT) {
            throwError(EM.ERR_EXPECTED_QUOTE)
        }

        val start = position
        val end = if (isStreaming) {
            source.findClosingQuote(start, limit)
        } else {
            val localData = rawData
            findClosingQuoteImpl(position = start, limit = limit) { localData[it].toInt() and TOK.BYTE_MASK }
        }
        if (end != -1) {
            position = end + 1
            return
        }

        var pos = start
        while (pos < limit) {
            val byteValue = getByte(pos++)
            if (byteValue == TOK.QUOTE_INT) {
                position = pos
                nextTokenByte = SCN.RESET_TOKEN_BYTE
                return
            }

            if (byteValue == TOK.BACKSLASH_INT) {
                if (pos >= limit) {
                    position = pos
                    throwError(EM.UNTERMINATED_ESCAPE_ERROR)
                }
                val escaped = getByte(pos++)

                if (escaped == TOK.UNICODE_PREFIX_U_INT) {
                    if (pos + TOK.UNICODE_HEX_LENGTH > limit) {
                        position = pos
                        throwError(EM.UNTERMINATED_UNICODE_ERROR)
                    }
                    parseUnicodeHex(pos)
                    pos += TOK.UNICODE_HEX_LENGTH
                }
            } else if (byteValue < TOK.SPACE_INT) {
                position = pos
                throwError(EM.UNESCAPED_CONTROL_CHAR_ERROR)
            }
        }
        position = pos
        throwError(EM.UNTERMINATED_STRING_ERROR)
    }

    private fun parseUnicodeHex(currentPosition: Int): Int {
        val hexByte0 = getByte(currentPosition)
        val hexByte1 = getByte(currentPosition + 1)
        val hexByte2 = getByte(currentPosition + 2)
        val hexByte3 = getByte(currentPosition + 3)

        val hexLookupTable = TOK.HEX_LUT
        val digitValue0 = hexLookupTable[hexByte0]
        val digitValue1 = hexLookupTable[hexByte1]
        val digitValue2 = hexLookupTable[hexByte2]
        val digitValue3 = hexLookupTable[hexByte3]

        if ((digitValue0 or digitValue1 or digitValue2 or digitValue3) < 0) {
            throwError(EM.ERR_INVALID_UNICODE_AT + currentPosition)
        }

        return (digitValue0 shl SCN.SHIFT_12) or
                (digitValue1 shl SCN.SHIFT_8) or
                (digitValue2 shl SCN.SHIFT_4) or
                digitValue3
    }

    fun reset(newData: ByteArray, newLimit: Int = newData.size) {
        val currentSource = this.source
        if (currentSource is ByteArrayGhostSource) {
            currentSource.data = newData
            reset(currentSource, newLimit)
        } else {
            reset(createByteArraySource(data = newData), newLimit)
        }
    }

    fun reset(okioSource: BufferedSource) {
        reset(createSourceBridge(source = okioSource), Int.MAX_VALUE)
    }

    fun reset(newSource: GhostSource, newLimit: Int = newSource.size) {
        this.source = newSource
        this.rawData = newSource.rawSourceData
        this.position = 0
        this.limit = newLimit
        this.nextTokenByte = SCN.RESET_TOKEN_BYTE
        this.depth = 0
        this.needsCommaMask = 0L
        this.commaConsumedMask = 0L
        this.strictMode = false
        this.coerceStringsToNumbers = false
        this.coerceBooleans = false
        this.maxDepth = NUM.MAX_DEPTH
        this.maxCollectionSize = GhostHeuristics.maxCollectionSize
        this.lastScanContentWas7BitOnly = false
        this.predictedFieldIndex = SCN.FIELD_PREDICTION_START
        this.pathTracker.reset()
    }
}

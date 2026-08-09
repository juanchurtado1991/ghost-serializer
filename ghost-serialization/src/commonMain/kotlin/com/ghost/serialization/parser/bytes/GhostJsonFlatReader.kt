@file:OptIn(InternalGhostApi::class)
@file:Suppress("FunctionName")

package com.ghost.serialization.parser.bytes


import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.common.GhostDiscriminatorPeeker
import com.ghost.serialization.parser.common.GhostHeuristics
import com.ghost.serialization.parser.common.GhostHeuristics.initialCollectionCapacity
import com.ghost.serialization.parser.common.GhostJsonConstants
import com.ghost.serialization.parser.common.GhostSource
import com.ghost.serialization.parser.common.JsonReaderOptions
import com.ghost.serialization.parser.common.createByteArraySource
import com.ghost.serialization.parser.common.findClosingQuoteImpl
import com.ghost.serialization.parser.streaming.captureRawJson
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.captureRawJson
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import com.ghost.serialization.parser.common.GhostJsonConstants as C

/**
 * Ultra-fast, specialized JSON parser for Kotlin Multiplatform that operates directly
 * on a flat [ByteArray] without any interface dispatch or hasFastPath boundaries.
 */
open class GhostJsonFlatReader(
    var rawData: ByteArray,
    var maxDepth: Int = C.MAX_DEPTH,
    /**
     * When true, enables strict JSON validation: rejects unknown/unmapped fields
     * and performs strict bitwise syntax validation on missing or duplicate commas.
     * Defaults to false for maximum lenient parsing performance.
     */
    var strictMode: Boolean = false,
    var coerceStringsToNumbers: Boolean = false,
    var coerceBooleans: Boolean = false,
    var maxCollectionSize: Int = GhostHeuristics.maxCollectionSize,
    /**
     * When true, [captureRawJson] copies captured UTF-8 into an owned array (offset 0)
     * instead of slicing [rawData]. Set by the [GhostJsonStringReader] deserialize bridge.
     */
    var materializeRawJsonCaptures: Boolean = false,
) {

    /**
     * Platform source used for string materialization ([GhostSource.decodeJsonStringRange]).
     * Constructed via [createByteArraySource] so JVM/Android use ISO-8859-1 for known 7-bit
     * spans instead of full UTF-8 [ByteArray.decodeToString]. Typed as [ByteArrayGhostSource]
     * so [resetSlice] can rebind [ByteArrayGhostSource.data] without reallocating the wrapper.
     */
    @PublishedApi
    internal val source: ByteArrayGhostSource =
        createByteArraySource(rawData) as ByteArrayGhostSource

    var limit: Int = rawData.size

    var position: Int = 0

    var nextTokenByte: Int = C.RESET_TOKEN_BYTE

    @InternalGhostApi
    fun _getPosition(): Int = position

    @InternalGhostApi
    fun _setPosition(position: Int) {
        this.position = position
    }

    @InternalGhostApi
    fun _getRawData(): ByteArray = rawData

    @InternalGhostApi
    fun _setNextTokenByte(tokenByte: Int) {
        nextTokenByte = tokenByte
    }

    internal val stringPool = arrayOfNulls<String>(C.STR_POOL_SIZE)
    internal val stringPoolHashes = IntArray(C.STR_POOL_SIZE)

    internal var lastScanContentWas7BitOnly: Boolean = false

    /**
     * Optimistic hint for [internalSelect]: the field index expected next, assuming JSON
     * objects list their fields in declaration order (the common case for machine-generated
     * payloads). When the incoming key matches this candidate,
     * key identification collapses from three byte passes (scan + hash + verify) to a single
     * compare pass. A misprediction transparently falls back to the hashed dispatch, so the
     * hint never affects correctness — only speed. Reset to
     * [GhostJsonConstants.FIELD_PREDICTION_START] on [beginObject].
     */
    private var predictedFieldIndex: Int = C.FIELD_PREDICTION_START

    var depth: Int = 0
    @PublishedApi
    internal var needsCommaMask: Long = 0L
    @PublishedApi
    internal var commaConsumedMask: Long = 0L

    /**
     * Gets the byte at the specified index, masking it to a positive integer.
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun getByte(index: Int): Int {
        return rawData[index].toInt() and C.BYTE_MASK
    }

    /**
     * Throws a structured [GhostJsonException] with exact position, line, and column numbers.
     */
    fun throwError(message: String): Nothing {
        val errorPosition = position
        val errorEnd = if (errorPosition > limit) {
            limit
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
                    if ((rawData[byteIndex].toInt() and C.BYTE_MASK) == C.NEWLINE_INT) {
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
     * Skips forward in the byte array by [byteCount] bytes and resets [nextTokenByte].
     */
    fun internalSkip(byteCount: Int) {
        position += byteCount
        nextTokenByte = C.RESET_TOKEN_BYTE
    }

    /**
     * Advances the position past any whitespace and caches the next non-whitespace token byte.
     */
    fun skipWhitespace() {
        val data = rawData
        val lim = limit
        var cursor = position
        while (true) {
            // SWAR fast path: swallow LONG_BYTES runs of ASCII space (SPACE_INT), which dominate
            // the byte volume of pretty-printed JSON indentation. SPACE_RUN_LONG is
            // byte-symmetric, so the platform byte order of ghostReadLong8 is irrelevant.
            while (cursor + C.LONG_BYTES <= lim && ghostReadLong8(data, cursor) == C.SPACE_RUN_LONG) {
                cursor += C.LONG_BYTES
            }
            if (cursor >= lim) {
                position = lim
                nextTokenByte = C.MATCH_END
                return
            }
            val tokenByte = data[cursor].toInt() and C.BYTE_MASK
            if (tokenByte > C.SPACE_INT) {
                position = cursor
                nextTokenByte = tokenByte
                return
            }
            // Non-space whitespace (tab / LF / CR) or a control byte; mirror WHITESPACE_MASK.
            if (tokenByte != C.SPACE_INT && tokenByte != C.LF_INT && tokenByte != C.CR_INT && tokenByte != C.TAB_INT) {
                position = cursor
                nextTokenByte = tokenByte
                return
            }
            cursor++
        }
    }

    /**
     * Peeks at the next key to see if it matches the discriminator name without consuming it.
     */
    fun peekDiscriminator(key: String = C.DEFAULT_DISCRIMINATOR_KEY): String? {
        if (key == C.DEFAULT_DISCRIMINATOR_KEY) {
            return peekDiscriminator(C.TYPE_BS)
        }
        return peekDiscriminator(key.encodeUtf8())
    }

    /**
     * Peeks at the next key to see if it matches the discriminator byte string without consuming it.
     */
    fun peekDiscriminator(key: ByteString): String? {
        return GhostDiscriminatorPeeker.peek(
            source,
            rawData,
            false,
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
        val size = expected.size
        if (position + size > limit || !expected.rangeEquals(0, rawData, position, size)) {
            throwError(C.ERR_EXPECTED_LITERAL + expected.utf8())
        }
        position += size
        nextTokenByte = C.RESET_TOKEN_BYTE
    }

    open fun nextFloat(): Float = nextFloatExtension()
    open fun nextDouble(): Double = nextDoubleExtension()
    open fun nextInt(): Int = nextIntExtension()
    open fun nextLong(): Long = nextLongExtension()

    /**
     * proto3 `uint64` scalar — quoted decimal string on the wire; bare JSON numbers accepted
     * on read when they fit in [Long]. Subclasses (e.g. `GhostProtoJsonFlatReader`)
     * override for full [ULong] range.
     */
    open fun nextProtoUInt64(): ULong {
        val saved = coerceStringsToNumbers
        coerceStringsToNumbers = true
        return try {
            if (peekNextToken() == C.QUOTE_INT) {
                nextString().toULong()
            } else {
                nextLong().toULong()
            }
        } finally {
            coerceStringsToNumbers = saved
        }
    }

    /** Plain JSON/YAML scalar `ULong` — quoted decimal string for full range, bare number when it fits in [Long]. */
    open fun nextULong(): ULong = nextProtoUInt64()

    fun nextULongOrNull(): ULong? {
        if (isNextNullValue()) {
            consumeNull()
            return null
        }
        return nextULong()
    }

    /**
     * Resets the reader's state to process a new byte payload.
     */
    fun reset(newData: ByteArray, newLimit: Int = newData.size) {
        resetSlice(newData, offset = 0, length = newLimit)
    }

    /**
     * Resets the reader to parse a sub-range of [buffer] without copying (zero-copy slice decode).
     */
    fun resetSlice(buffer: ByteArray, offset: Int, length: Int) {
        rawData = buffer
        source.data = buffer
        position = offset
        limit = offset + length
        nextTokenByte = C.RESET_TOKEN_BYTE
        depth = 0
        needsCommaMask = 0L
        commaConsumedMask = 0L
        strictMode = false
        coerceStringsToNumbers = false
        coerceBooleans = false
        maxDepth = C.MAX_DEPTH
        maxCollectionSize = GhostHeuristics.maxCollectionSize
        lastScanContentWas7BitOnly = false
    }

    /**
     * Begins consumption of a JSON object '{'. Increments validation depth.
     */
    fun beginObject() {
        if (nextNonWhitespace() != C.OPEN_OBJ_INT) {
            throwError(C.ERR_EXPECTED_BEGIN_OBJ)
        }
        predictedFieldIndex = C.FIELD_PREDICTION_START
        depth++
        if (depth > maxDepth) {
            throwError(C.ERR_DEPTH_EXCEEDED)
        }
        if (depth < C.MAX_BITMASK_DEPTH) {
            val bit = C.BITMASK_UNIT shl depth
            needsCommaMask = needsCommaMask and bit.inv()
            commaConsumedMask = commaConsumedMask and bit.inv()
        }
    }

    /**
     * Ends consumption of a JSON object '}'. Decrements validation depth.
     */
    fun endObject() {
        if (nextNonWhitespace() != C.CLOSE_OBJ_INT) {
            throwError(C.ERR_EXPECTED_END_OBJ)
        }
        if (depth > 0) {
            depth--
        }
    }

    /**
     * Begins consumption of a JSON array '['. Increments validation depth.
     */
    fun beginArray() {
        if (nextNonWhitespace() != C.OPEN_ARR_INT) {
            throwError(C.ERR_EXPECTED_BEGIN_ARR)
        }
        depth++
        if (depth > maxDepth) {
            throwError(C.ERR_DEPTH_EXCEEDED)
        }
        if (depth < C.MAX_BITMASK_DEPTH) {
            val bit = C.BITMASK_UNIT shl depth
            needsCommaMask = needsCommaMask and bit.inv()
            commaConsumedMask = commaConsumedMask and bit.inv()
        }
    }

    /**
     * Ends consumption of a JSON array ']'. Decrements validation depth.
     */
    fun endArray() {
        if (nextNonWhitespace() != C.CLOSE_ARR_INT) {
            throwError(C.ERR_EXPECTED_END_ARR)
        }
        if (depth > 0) {
            depth--
        }
    }

    /**
     * Checks if there are more elements in the current JSON container.
     */
    fun hasNext(): Boolean {
        val token = peekNextToken()
        if (
            token == C.CLOSE_ARR_INT ||
            token == C.CLOSE_OBJ_INT ||
            token == C.MATCH_END
        ) {
            return false
        }
        if (strictMode && depth < C.MAX_BITMASK_DEPTH) {
            val bit = C.BITMASK_UNIT shl depth
            if ((commaConsumedMask and bit) != C.RESULT_NONE) {
                if (token == C.COMMA_INT) {
                    commaConsumedMask = commaConsumedMask and bit.inv()
                    needsCommaMask = needsCommaMask or bit
                }
            }
            if ((commaConsumedMask and bit) != C.RESULT_NONE) {
                commaConsumedMask = commaConsumedMask and bit.inv()
                needsCommaMask = needsCommaMask or bit
            } else {
                val required = (needsCommaMask and bit) != C.RESULT_NONE
                if (token == C.COMMA_INT) {
                    if (!required) {
                        throwError(C.ERR_UNEXPECTED_COMMA)
                    }
                    internalSkip(1)
                    val next = peekNextToken()
                    if (next == C.CLOSE_ARR_INT || next == C.CLOSE_OBJ_INT) {
                        throwError(C.ERR_TRAILING_COMMA)
                    }
                    commaConsumedMask = commaConsumedMask or bit
                    needsCommaMask = needsCommaMask and bit.inv()
                } else {
                    if (required) throwError(C.ERR_EXPECTED_COMMA)
                    needsCommaMask = needsCommaMask or bit
                }
            }
        } else {
            if (token == C.COMMA_INT) {
                internalSkip(1)
                val next = peekNextToken()
                if (next == C.CLOSE_ARR_INT || next == C.CLOSE_OBJ_INT) {
                    throwError(C.ERR_TRAILING_COMMA)
                }
            }
        }
        return true
    }

    /**
     * Consumes any comma separator and returns the next object key string. Returns null if object ends.
     */
    fun nextKey(): String? {
        val token = peekNextToken()
        if (token == C.CLOSE_OBJ_INT) {
            return null
        }
        if (strictMode && depth < C.MAX_BITMASK_DEPTH) {
            val bit = C.BITMASK_UNIT shl depth
            // If comma was already consumed by consumeArraySeparator(), skip re-requiring it.
            if ((commaConsumedMask and bit) != C.RESULT_NONE) {
                commaConsumedMask = commaConsumedMask and bit.inv()
                needsCommaMask = needsCommaMask or bit
            } else {
                val required = (needsCommaMask and bit) != C.RESULT_NONE
                if (token == C.COMMA_INT) {
                    if (!required) {
                        throwError(C.ERR_UNEXPECTED_COMMA)
                    }
                    internalSkip(1)
                    if (peekNextToken() == C.CLOSE_OBJ_INT) {
                        throwError(C.ERR_TRAILING_COMMA)
                    }
                    needsCommaMask = needsCommaMask or bit
                } else {
                    if (required) {
                        throwError(C.ERR_EXPECTED_COMMA_OR_CLOSE_OBJ)
                    }
                    needsCommaMask = needsCommaMask or bit
                }
            }
        } else {
            if (token == C.COMMA_INT) {
                internalSkip(1)
                if (peekNextToken() == C.CLOSE_OBJ_INT) {
                    throwError(C.ERR_TRAILING_COMMA)
                }
            }
        }
        return readQuotedString()
    }

    /**
     * Consumes the ':' key-value separator character.
     */
    fun consumeKeySeparator() {
        if (nextNonWhitespace() != C.COLON_INT) {
            throwError(C.ERR_EXPECTED_COLON)
        }
    }

    /**
     * Consumes the array element separating comma if present.
     */
    fun consumeArraySeparator() {
        if (strictMode && depth < C.MAX_BITMASK_DEPTH) {
            val bit = C.BITMASK_UNIT shl depth
            // If hasNext() already consumed the comma, honor that.
            if ((commaConsumedMask and bit) != C.RESULT_NONE) {
                commaConsumedMask = commaConsumedMask and bit.inv()
                needsCommaMask = needsCommaMask or bit
                return
            }
            val token = peekNextToken()
            val required = (needsCommaMask and bit) != C.RESULT_NONE
            if (token == C.COMMA_INT) {
                // Consume the comma and signal to the next nextKey()/selectNameAndConsume() that
                // it was already consumed, so they don't re-require one.
                internalSkip(1)
                val next = peekNextToken()
                if (next == C.CLOSE_ARR_INT || next == C.CLOSE_OBJ_INT) {
                    throwError(C.ERR_TRAILING_COMMA)
                }
                commaConsumedMask = commaConsumedMask or bit
            } else if (required) {
                if (token != C.CLOSE_ARR_INT && token != C.CLOSE_OBJ_INT) {
                    throwError(C.ERR_EXPECTED_COMMA_OR_CLOSE_ARR)
                }
            } else {
                // First call at this depth: no prior comma needed, but if a non-separator token
                // follows (neither comma nor closing bracket), the JSON is malformed.
                if (token != C.CLOSE_ARR_INT && token != C.CLOSE_OBJ_INT) {
                    throwError(C.ERR_EXPECTED_COMMA_OR_CLOSE_ARR)
                }
            }
            needsCommaMask = needsCommaMask or bit
        } else {
            val token = peekNextToken()
            if (token == C.COMMA_INT) {
                internalSkip(1)
                if (peekNextToken() == C.CLOSE_ARR_INT) {
                    throwError(C.ERR_TRAILING_COMMA)
                }
            }
        }
    }

    /**
     * Parses and returns the next [Boolean] value.
     */
    fun nextBoolean(): Boolean {
        val token = peekNextToken()
        if (token == C.TRUE_CHAR_INT) {
            skipAndValidateLiteral(C.TRUE_BS)
            return true
        }
        if (token == C.FALSE_CHAR_INT) {
            skipAndValidateLiteral(C.FALSE_BS)
            return false
        }
        if (coerceBooleans) {
            if (token == C.ONE_INT) {
                internalSkip(1)
                return true
            }
            if (token == C.ZERO_INT) {
                internalSkip(1)
                return false
            }
            if (token == C.QUOTE_INT) {
                // can the quoted string bytes directly. No String allocation.
                return matchCoerceBooleanBytes()
            }
        }
        throwError(C.ERR_EXPECTED_BOOLEAN)
    }

    /**
     * Parses and returns the next string literal.
     */
    fun nextString(): String = readQuotedString()

    /**
     * Peeks whether the next JSON token is the null value token.
     */
    fun isNextNullValue(): Boolean = peekNextToken() == C.NULL_CHAR_INT

    /**
     * Consumes the null value literal from the stream.
     *
     * After [peekNextToken] has already positioned on `'n'`, validates the remaining
     * `ull` bytes inline — avoids `okio.ByteString.rangeEquals` on the hot nullable path.
     */
    fun consumeNull() {
        val cursor = position
        val data = rawData
        if (cursor + 4 > limit ||
            (data[cursor].toInt() and C.BYTE_MASK) != C.NULL_CHAR_INT ||
            (data[cursor + 1].toInt() and C.BYTE_MASK) != C.U_BYTE_INT ||
            (data[cursor + 2].toInt() and C.BYTE_MASK) != C.L_BYTE_INT ||
            (data[cursor + 3].toInt() and C.BYTE_MASK) != C.L_BYTE_INT
        ) {
            throwError(C.ERR_EXPECTED_LITERAL + C.LITERAL_NULL)
        }
        position = cursor + 4
        nextTokenByte = C.RESET_TOKEN_BYTE
    }

    /** Reads a JSON string, or `null` when the next token is the `null` literal. */
    fun nextStringOrNull(): String? {
        if (peekNextToken() == C.NULL_CHAR_INT) {
            consumeNull()
            return null
        }
        return nextString()
    }

    /** Reads a JSON int, or `null` when the next token is the `null` literal. */
    fun nextIntOrNull(): Int? {
        if (peekNextToken() == C.NULL_CHAR_INT) {
            consumeNull()
            return null
        }
        return nextInt()
    }

    /** Reads a JSON long, or `null` when the next token is the `null` literal. */
    fun nextLongOrNull(): Long? {
        if (peekNextToken() == C.NULL_CHAR_INT) {
            consumeNull()
            return null
        }
        return nextLong()
    }

    /** Reads a JSON boolean, or `null` when the next token is the `null` literal. */
    fun nextBooleanOrNull(): Boolean? {
        if (peekNextToken() == C.NULL_CHAR_INT) {
            consumeNull()
            return null
        }
        return nextBoolean()
    }

    /**
     * Zero-copy boolean coercion matcher. Delegates byte comparison to
     * [matchCoerceBooleanBytes] in GhostParserUtils — single source of truth.
     */
    private fun matchCoerceBooleanBytes(): Boolean {
        val localData = rawData
        val lim = limit
        val contentStart = position + 1 // skip opening
        val end = findClosingQuoteImpl(contentStart, lim) {
            localData[it].toInt() and C.BYTE_MASK
        }
        if (end == -1) throwError(C.UNTERMINATED_STRING_ERROR)
        val length = end - contentStart
        position = end + 1
        nextTokenByte = C.RESET_TOKEN_BYTE
        return com.ghost.serialization.parser.common.matchCoerceBooleanBytes(
            start = contentStart,
            length = length,
            onError = { throwError(C.ERR_EXPECTED_BOOLEAN) },
            getByte = { localData[it].toInt() and C.BYTE_MASK },
        )
    }

    /**
     * Selects name and consumes the key separator.
     */
    fun selectNameAndConsume(options: JsonReaderOptions): Int =
        internalSelect(options, consumeSeparator = true)

    /**
     * Selects matching string options.
     */
    fun selectString(options: JsonReaderOptions): Int =
        internalSelect(options, consumeSeparator = false)

    /**
     * Low-level helper to search and return matched index in options lookup table.
     */
    private fun internalSelect(options: JsonReaderOptions, consumeSeparator: Boolean): Int {
        var token = peekNextToken()
        if (token == C.CLOSE_OBJ_INT) {
            return -1
        }

        if (strictMode && depth < C.MAX_BITMASK_DEPTH) {
            val bit = C.BITMASK_UNIT shl depth
            if ((commaConsumedMask and bit) != C.RESULT_NONE) {
                commaConsumedMask = commaConsumedMask and bit.inv()
                needsCommaMask = needsCommaMask or bit
            } else {
                val required = (needsCommaMask and bit) != C.RESULT_NONE
                if (token == C.COMMA_INT) {
                    if (!required) {
                        throwError(C.ERR_UNEXPECTED_COMMA)
                    }
                    internalSkip(1)
                    token = peekNextToken()
                    if (token == C.CLOSE_OBJ_INT) {
                        throwError(C.ERR_TRAILING_COMMA)
                    }
                    commaConsumedMask = commaConsumedMask and bit.inv()
                    needsCommaMask = needsCommaMask or bit
                } else {
                    if (required && consumeSeparator) {
                        throwError(C.ERR_EXPECTED_COMMA_OR_CLOSE_OBJ)
                    }
                    needsCommaMask = needsCommaMask or bit
                }
            }
        } else {
            if (token == C.COMMA_INT) {
                internalSkip(1)
                token = peekNextToken()
                if (token == C.CLOSE_OBJ_INT) {
                    throwError(C.ERR_TRAILING_COMMA)
                }
            }
        }

        if (token != C.QUOTE_INT) {
            throwError(
                if (consumeSeparator) {
                    C.ERR_EXPECTED_KEY
                } else {
                    C.ERR_EXPECTED_STRING
                }
            )
        }
        val start = position + 1
        val localData = rawData
        val lim = limit

        // Optimistic in-order field match: most objects list fields in declaration order,
        // so compare the key directly against the predicted candidate in a single pass. On a
        // hit this avoids the separate closing-quote scan, hash, and verify passes entirely.
        val predicted = predictedFieldIndex
        val rawBytes = options.rawBytes
        if (predicted < rawBytes.size) {
            val candidate = rawBytes[predicted]
            val candLen = candidate.size
            val keyEnd = start + candLen
            if (candLen > 0 && keyEnd < lim &&
                (localData[keyEnd].toInt() and C.BYTE_MASK) == C.QUOTE_INT
            ) {
                var i = 0
                // Compare LONG_BYTES at a time for longer field names. Comparing two
                // ghostReadLong8 results is byte-order independent (equality is symmetric),
                // so no masking is needed; a trailing byte loop covers the remainder.
                while (i + C.LONG_BYTES <= candLen &&
                    ghostReadLong8(localData, start + i) == ghostReadLong8(candidate, i)
                ) {
                    i += C.LONG_BYTES
                }
                while (i < candLen && localData[start + i] == candidate[i]) {
                    i++
                }
                if (i == candLen) {
                    predictedFieldIndex = predicted + 1
                    val newPos = keyEnd + 1
                    position = newPos
                    nextTokenByte = C.RESET_TOKEN_BYTE
                    if (consumeSeparator) {
                        if (newPos < lim && (localData[newPos].toInt() and C.BYTE_MASK) == C.COLON_INT) {
                            position = newPos + 1
                        } else {
                            consumeKeySeparator()
                        }
                    }
                    return predicted
                }
            }
        }

        val end = findClosingQuoteImpl(start, lim) {
            localData[it].toInt() and C.BYTE_MASK
        }

        if (end == -1) {
            throwError(C.UNTERMINATED_STRING_ERROR)
        }

        val length = end - start
        val key = computeKeyHash(start, length, options.hasCollisions)

        val hasIndex =
            ((key * options.multiplier + length) shr options.shift) and (options.dispatch.size - 1)
        val index = options.dispatch[hasIndex]

        if (index != C.MATCH_END) {
            if (verifyKeyMatch(start, length, options.rawBytes[index], consumeSeparator)) {
                predictedFieldIndex = index + 1
                return index
            }
        }

        // No match found
        val newPos = end + 1
        position = newPos
        nextTokenByte = C.MATCH_END
        if (consumeSeparator) {
            if (newPos < limit && getByte(newPos) == C.COLON_INT) {
                position = newPos + 1
            } else {
                consumeKeySeparator()
            }
        } else if (strictMode) {
            val unknownKey = source.decodeToString(start, end)
            throwError("${C.STRICT_MODE_UNKNOWN_FIELD}$unknownKey")
        }

        return C.MATCH_NONE
    }

    /**
     * Computes the 32-bit hash value of the string range.
     */
    private fun computeKeyHash(start: Int, length: Int, hasCollisions: Boolean): Int {
        var key = 0
        if (length >= 4) {
            val b0 = rawData[start].toInt() and C.BYTE_MASK
            val b1 = rawData[start + 1].toInt() and C.BYTE_MASK
            val b2 = rawData[start + 2].toInt() and C.BYTE_MASK
            val b3 = rawData[start + 3].toInt() and C.BYTE_MASK
            key = b0 or (b1 shl C.SHIFT_8) or (b2 shl C.SHIFT_16) or (b3 shl C.SHIFT_24)
            if (hasCollisions) {
                var ci = C.UNICODE_HEX_LENGTH
                while (ci < length) {
                    key =
                        key * C.COLLISION_HASH_MULTIPLIER + (rawData[start + ci].toInt() and C.BYTE_MASK); ci++
                }
            }
        } else {
            if (length >= 1) {
                key = key or (rawData[start].toInt() and C.BYTE_MASK)
            }
            if (length >= 2) {
                key = key or ((rawData[start + 1].toInt() and C.BYTE_MASK) shl C.SHIFT_8)
            }
            if (length >= 3) {
                key = key or ((rawData[start + 2].toInt() and C.BYTE_MASK) shl C.SHIFT_16)
            }
        }
        return key
    }

    /**
     * Performs a fast comparison of the parsed string against expected bytes to verify matches.
     */
    private fun verifyKeyMatch(
        start: Int,
        length: Int,
        expected: ByteArray,
        consumeSeparator: Boolean
    ): Boolean {
        // Length is already guaranteed equal by the dispatch table (same hash slot).
        if (expected.size == length) {
            val localData = rawData
            var i = 0
            // Unrolled x4 for typical ASCII field name lengths (4–20 chars).
            while (i + 3 < length) {
                if (localData[start + i] != expected[i]) return false
                if (localData[start + i + 1] != expected[i + 1]) return false
                if (localData[start + i + 2] != expected[i + 2]) return false
                if (localData[start + i + 3] != expected[i + 3]) return false
                i += 4
            }
            while (i < length) {
                if (localData[start + i] != expected[i]) return false
                i++
            }
            val endPos = start + length
            val newPos = endPos + 1
            position = newPos
            nextTokenByte = C.RESET_TOKEN_BYTE
            if (consumeSeparator) {
                if (newPos < limit) {
                    val colonToken = getByte(newPos)
                    if (colonToken == C.COLON_INT) {
                        position = newPos + 1
                    } else {
                        consumeKeySeparator()
                    }
                } else {
                    consumeKeySeparator()
                }
            }
            return true
        }
        return false
    }


    /**
     * Peeks at a key name and returns it if it is a string match.
     */
    fun peekStringField(name: String): String? {
        return peekDiscriminator(name)
    }

    /**
     * Skips the next complete value token (object, array, string, number, boolean, null) from the stream.
     */
    fun skipValue() {
        val token = peekNextToken()
        when (token) {
            C.OPEN_OBJ_INT -> {
                beginObject()
                while (hasNext()) {
                    if (peekNextToken() != C.QUOTE_INT) {
                        throwError(C.ERR_EXPECTED_KEY)
                    }
                    skipQuotedString()
                    consumeKeySeparator()
                    skipValue()
                }
                endObject()
            }

            C.OPEN_ARR_INT -> {
                beginArray()
                while (hasNext()) {
                    skipValue()
                }
                endArray()
            }

            C.QUOTE_INT -> {
                skipQuotedString()
            }

            C.TRUE_CHAR_INT -> {
                skipAndValidateLiteral(C.TRUE_BS)
            }

            C.FALSE_CHAR_INT -> {
                skipAndValidateLiteral(C.FALSE_BS)
            }

            C.NULL_CHAR_INT -> {
                skipAndValidateLiteral(C.NULL_BS)
            }

            else -> {
                skipNumber()
            }
        }
    }

    /**
     * Reads a list of items using the provided [itemParser].
     */
    inline fun <T> readList(crossinline itemParser: () -> T): List<T> {
        beginArray()
        if (peekNextToken() == C.CLOSE_ARR_INT) {
            endArray()
            return emptyList()
        }
        val list = ArrayList<T>(initialCollectionCapacity)
        val maxSize = maxCollectionSize

        while (true) {
            list.add(itemParser())
            val next = nextNonWhitespace()
            if (next == C.CLOSE_ARR_INT) {
                if (depth > 0) {
                    depth--
                }
                break
            }
            if (next != C.COMMA_INT) {
                throwError("${C.ERR_EXPECTED_COMMA_OR_CLOSE_ARR} but found $next")
            }
            if (list.size > maxSize) {
                throwError("${C.ERR_MAX_COLLECTION_SIZE} ($maxSize)")
            }
        }
        return list
    }

    /**
     * Reads a set of items using the provided [itemParser].
     * Builds a [HashSet] directly — no intermediate [List] allocation.
     */
    inline fun <T> readSet(crossinline itemParser: () -> T): Set<T> {
        beginArray()
        if (peekNextToken() == C.CLOSE_ARR_INT) {
            endArray()
            return emptySet()
        }
        val set = HashSet<T>(initialCollectionCapacity)
        val maxSize = maxCollectionSize

        while (true) {
            set.add(itemParser())
            val next = nextNonWhitespace()
            if (next == C.CLOSE_ARR_INT) {
                if (depth > 0) {
                    depth--
                }
                break
            }
            if (next != C.COMMA_INT) {
                throwError("${C.ERR_EXPECTED_COMMA_OR_CLOSE_ARR} but found $next")
            }
            if (set.size > maxSize) {
                throwError("${C.ERR_MAX_COLLECTION_SIZE} ($maxSize)")
            }
        }
        return set
    }

    /**
     * Reads a map of keys and values using the provided [keyParser] and [valueParser].
     */
    inline fun <K, V> readMap(
        crossinline keyParser: () -> K,
        crossinline valueParser: () -> V
    ): Map<K, V> {
        beginObject()
        if (peekNextToken() == C.CLOSE_OBJ_INT) {
            endObject()
            return emptyMap()
        }

        val map = HashMap<K, V>(initialCollectionCapacity)
        val maxSize = maxCollectionSize

        while (true) {
            val key = keyParser()
            consumeKeySeparator()
            val value = valueParser()
            map[key] = value

            val next = nextNonWhitespace()
            if (next == C.CLOSE_OBJ_INT) {
                if (depth > 0) {
                    depth--
                }
                break
            }
            if (next != C.COMMA_INT) {
                throwError("${C.ERR_EXPECTED_COMMA_OR_CLOSE_OBJ} but found $next")
            }
            // The comma was consumed directly via nextNonWhitespace(); clear needsCommaMask so
            // the next keyParser() (nextKey()) doesn't re-require another comma.
            if (depth < C.MAX_BITMASK_DEPTH) {
                val bit = C.BITMASK_UNIT shl depth
                needsCommaMask = needsCommaMask and bit.inv()
            }
            if (map.size > maxSize) {
                throwError("${C.ERR_MAX_COLLECTION_SIZE} ($maxSize)")
            }
        }
        return map
    }

    /**
     * Resiliently decodes a value. If an error occurs, skips the value and returns null.
     */
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

}

@file:OptIn(InternalGhostApi::class)
@file:Suppress("FunctionName")

package com.ghost.serialization.parser.bytes

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.exception.hintForJsonError
import com.ghost.serialization.parser.common.GhostDiscriminatorPeeker
import com.ghost.serialization.parser.common.GhostHeuristics
import com.ghost.serialization.parser.common.GhostHeuristics.initialCollectionCapacity
import com.ghost.serialization.parser.common.GhostJsonConstants
import com.ghost.serialization.parser.common.GhostJsonPathTracker
import com.ghost.serialization.parser.common.GhostSource
import com.ghost.serialization.parser.common.JsonReaderOptions
import com.ghost.serialization.parser.common.createByteArraySource
import com.ghost.serialization.parser.common.findClosingQuoteImpl
import com.ghost.serialization.parser.common.skipValueCore
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
    internal var predictedFieldIndex: Int = C.FIELD_PREDICTION_START

    var depth: Int = 0

    @PublishedApi
    internal var needsCommaMask: Long = 0L

    @PublishedApi
    internal var commaConsumedMask: Long = 0L

    /** JSONPath breadcrumbs — formatted only when [throwError] builds an exception. */
    @PublishedApi
    internal val pathTracker: GhostJsonPathTracker = GhostJsonPathTracker()

    /** Byte at [index], masked to a positive int (Kotlin's [Byte] is signed). */
    @Suppress("NOTHING_TO_INLINE")
    inline fun getByte(index: Int): Int {
        return rawData[index].toInt() and C.BYTE_MASK
    }

    /** Throws a [GhostJsonException] with exact position, line, column, and JSONPath. */
    fun throwError(message: String): Nothing {
        val errorPosition = position
        val errorEnd = if (errorPosition > limit) {
            limit
        } else {
            errorPosition
        }
        val errorPath = pathTracker.formatPath()

        throw GhostJsonException(
            baseMessage = "$message${C.ERR_AT_POSITION_PREFIX}$errorPosition",
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
            },
            path = errorPath,
            hint = hintForJsonError(message),
        )
    }

    /**
     * Throws for a missing required field, pushing [jsonName] onto the JSONPath so the
     * exception points at `$.….<jsonName>` even though the object loop has already finished
     * selecting keys (validation runs before [endObject]).
     */
    fun throwMissingRequiredField(jsonName: String): Nothing {
        pathTracker.pushKey(jsonName)
        throwError("${C.ERR_REQUIRED_FIELD_PREFIX}$jsonName${C.ERR_REQUIRED_FIELD_SUFFIX}")
    }

    fun internalSkip(byteCount: Int) {
        position += byteCount
        nextTokenByte = C.RESET_TOKEN_BYTE
    }

    /** Advances past whitespace and caches the next non-whitespace token byte. */
    fun skipWhitespace() {
        val data = rawData
        val byteLimit = limit
        var cursor = position
        while (true) {
            // SWAR fast path: swallow LONG_BYTES runs of ASCII space (SPACE_INT), which dominate
            // the byte volume of pretty-printed JSON indentation. SPACE_RUN_LONG is
            // byte-symmetric, so the platform byte order of ghostReadLong8 is irrelevant.
            while (cursor + C.LONG_BYTES <= byteLimit &&
                ghostReadLong8(data, cursor) == C.SPACE_RUN_LONG
            ) {
                cursor += C.LONG_BYTES
            }
            if (cursor >= byteLimit) {
                position = byteLimit
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

    /** Peeks whether the next key matches the discriminator [key], without consuming it. */
    fun peekDiscriminator(key: String = C.DEFAULT_DISCRIMINATOR_KEY): String? {
        if (key == C.DEFAULT_DISCRIMINATOR_KEY) {
            return peekDiscriminator(C.TYPE_BS)
        }
        return peekDiscriminator(key.encodeUtf8())
    }

    /** Peeks whether the next key matches the discriminator [key], without consuming it. */
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

    /** Returns the next token byte, skipping whitespace; cached until consumed. */
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

    @InternalGhostApi
    fun skipAndValidateLiteral(expected: ByteString) {
        val size = expected.size
        if (
            position + size > limit || !expected.rangeEquals(
                offset = 0,
                other = rawData,
                otherOffset = position,
                byteCount = size
            )
        ) {
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

    fun reset(newData: ByteArray, newLimit: Int = newData.size) {
        resetSlice(newData, offset = 0, length = newLimit)
    }

    /** Resets the reader to parse a sub-range of [buffer] without copying (zero-copy slice decode). */
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
        pathTracker.reset()
    }

    /** Starts parsing a JSON object, enforcing [maxDepth] to guard against stack overflow. */
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
        pathTracker.pushObject()
    }

    fun endObject() {
        if (nextNonWhitespace() != C.CLOSE_OBJ_INT) {
            throwError(C.ERR_EXPECTED_END_OBJ)
        }
        if (depth > 0) {
            depth--
        }
        pathTracker.finishObjectValue()
    }

    /** Starts parsing a JSON array, enforcing [maxDepth] to guard against stack exhaustion. */
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
        pathTracker.pushArray()
    }

    fun endArray() {
        if (nextNonWhitespace() != C.CLOSE_ARR_INT) {
            throwError(C.ERR_EXPECTED_END_ARR)
        }
        if (depth > 0) {
            depth--
        }
        pathTracker.finishArrayValue()
    }

    /** Returns whether the current container has more elements; rejects trailing commas. */
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
        pathTracker.enterArrayElement()
        return true
    }

    /** Consumes any comma separator and returns the next key, or `null` if the object has ended. */
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
        val key = readQuotedString()
        pathTracker.pushKey(key)
        return key
    }

    fun consumeKeySeparator() {
        if (nextNonWhitespace() != C.COLON_INT) {
            throwError(C.ERR_EXPECTED_COLON)
        }
    }

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

    /** Parses the next boolean; if [coerceBooleans], also accepts `0`/`1` and matching strings. */
    fun nextBoolean(): Boolean {
        val token = peekNextToken()
        if (token == C.TRUE_CHAR_INT) {
            skipAndValidateLiteral(C.TRUE_BS)
            pathTracker.finishScalarValue()
            return true
        }
        if (token == C.FALSE_CHAR_INT) {
            skipAndValidateLiteral(C.FALSE_BS)
            pathTracker.finishScalarValue()
            return false
        }
        if (coerceBooleans) {
            if (token == C.ONE_INT) {
                internalSkip(1)
                pathTracker.finishScalarValue()
                return true
            }
            if (token == C.ZERO_INT) {
                internalSkip(1)
                pathTracker.finishScalarValue()
                return false
            }
            if (token == C.QUOTE_INT) {
                // can the quoted string bytes directly. No String allocation.
                val coerced = matchCoerceBooleanBytes()
                pathTracker.finishScalarValue()
                return coerced
            }
        }
        throwError(C.ERR_EXPECTED_BOOLEAN)
    }

    fun nextString(): String {
        val value = readQuotedString()
        pathTracker.finishScalarValue()
        return value
    }

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
        pathTracker.finishScalarValue()
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

    /** Zero-copy boolean coercion matcher; delegates byte comparison to the shared helper in GhostParserUtils. */
    private fun matchCoerceBooleanBytes(): Boolean {
        val localData = rawData
        val byteLimit = limit
        val contentStart = position + 1 // skip opening
        val end = findClosingQuoteImpl(contentStart, byteLimit) {
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

    /** Identifies the next field name via [options]'s perfect hash, consuming the following `:`. */
    fun selectNameAndConsume(options: JsonReaderOptions): Int {
        val index = internalSelect(options, consumeSeparator = true)
        if (index >= 0) {
            pathTracker.pushKey(options.rawStrings[index])
        }
        return index
    }

    /** Matches a string token (e.g. an enum value) against [options], without consuming a `:` separator. */
    fun selectString(options: JsonReaderOptions): Int =
        internalSelect(options, consumeSeparator = false)

    /** Peeks a key's string value without advancing the cursor; used for sealed class discriminators. */
    fun peekStringField(name: String): String? {
        return peekDiscriminator(name)
    }

    /** Skips the next complete JSON value (object, array, string, number, boolean, null), balancing nesting. */
    fun skipValue() {
        skipValueCore(
            peekNextToken = { peekNextToken() },
            beginObject = { beginObject() },
            endObject = { endObject() },
            beginArray = { beginArray() },
            endArray = { endArray() },
            hasNext = { hasNext() },
            skipQuotedString = { skipQuotedString() },
            consumeKeySeparator = { consumeKeySeparator() },
            skipValue = { skipValue() },
            skipAndValidateLiteral = { skipAndValidateLiteral(it) },
            skipNumber = { skipNumber() },
            throwError = { throwError(it) },
        )
    }

    /** Decodes a JSON array into a [List] using [itemParser]. Enforces [maxCollectionSize]. */
    inline fun <T> readList(crossinline itemParser: () -> T): List<T> {
        beginArray()
        if (peekNextToken() == C.CLOSE_ARR_INT) {
            endArray()
            return emptyList()
        }
        val list = ArrayList<T>(initialCollectionCapacity)
        val maxSize = maxCollectionSize

        while (true) {
            pathTracker.enterArrayElement()
            list.add(itemParser())
            val next = nextNonWhitespace()
            if (next == C.CLOSE_ARR_INT) {
                if (depth > 0) {
                    depth--
                }
                pathTracker.finishArrayValue()
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

    /** Reads a JSON array into a [Set] without an intermediate [List] allocation. */
    inline fun <T> readSet(crossinline itemParser: () -> T): Set<T> {
        beginArray()
        if (peekNextToken() == C.CLOSE_ARR_INT) {
            endArray()
            return emptySet()
        }
        val set = HashSet<T>(initialCollectionCapacity)
        val maxSize = maxCollectionSize

        while (true) {
            pathTracker.enterArrayElement()
            set.add(itemParser())
            val next = nextNonWhitespace()
            if (next == C.CLOSE_ARR_INT) {
                if (depth > 0) {
                    depth--
                }
                pathTracker.finishArrayValue()
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

    /** Decodes a JSON object into a [Map] using [keyParser]/[valueParser]. Enforces [maxCollectionSize]. */
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
                pathTracker.finishObjectValue()
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

    /** Runs [block]; on [GhostJsonException] rolls back state and skips the invalid value, returning `null`. */
    @InternalGhostApi
    inline fun <T> decodeResilient(crossinline block: () -> T): T? {
        val savedPos = position
        val savedToken = nextTokenByte
        val savedDepth = depth
        val savedNeedsCommaMask = needsCommaMask
        val savedCommaConsumedMask = commaConsumedMask
        val savedPathMark = pathTracker.mark()
        try {
            return block()
        } catch (_: GhostJsonException) {
            position = savedPos
            nextTokenByte = savedToken
            depth = savedDepth
            needsCommaMask = savedNeedsCommaMask
            commaConsumedMask = savedCommaConsumedMask
            pathTracker.resetTo(savedPathMark)
            skipValue()
            pathTracker.finishScalarValue()
            return null
        }
    }

}

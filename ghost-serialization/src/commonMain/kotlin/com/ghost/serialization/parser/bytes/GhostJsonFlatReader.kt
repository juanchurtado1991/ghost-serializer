@file:OptIn(InternalGhostApi::class)
@file:Suppress("FunctionName")

package com.ghost.serialization.parser.bytes

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.exception.hintForJsonError
import com.ghost.serialization.parser.bytes.extensions.internalSelect
import com.ghost.serialization.parser.bytes.extensions.nextDoubleExtension
import com.ghost.serialization.parser.bytes.extensions.nextFloatExtension
import com.ghost.serialization.parser.bytes.extensions.nextIntExtension
import com.ghost.serialization.parser.bytes.extensions.nextLongExtension
import com.ghost.serialization.parser.bytes.extensions.readQuotedString
import com.ghost.serialization.parser.bytes.extensions.skipNumber
import com.ghost.serialization.parser.bytes.extensions.skipQuotedString
import com.ghost.serialization.parser.common.GhostDiscriminatorPeeker
import com.ghost.serialization.parser.common.GhostHeuristics
import com.ghost.serialization.parser.common.json.GhostJsonPathReconstruction
import com.ghost.serialization.parser.common.json.GhostJsonPathTracker
import com.ghost.serialization.parser.common.GhostSource
import com.ghost.serialization.parser.common.json.JsonReaderOptions
import com.ghost.serialization.parser.common.createByteArraySource
import com.ghost.serialization.parser.common.findClosingQuoteImpl
import com.ghost.serialization.parser.common.matchCoerceBooleanBytes
import com.ghost.serialization.parser.common.json.skipValueCore
import com.ghost.serialization.parser.streaming.captureRawJson
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.captureRawJson
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import com.ghost.serialization.parser.common.constants.GhostJsonErrorMessages as EM
import com.ghost.serialization.parser.common.constants.GhostJsonNumericLimits as NUM
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants as SCN
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants as WR

/**
 * Ultra-fast, specialized JSON parser for Kotlin Multiplatform that operates directly
 * on a flat [ByteArray] without any interface dispatch or hasFastPath boundaries.
 *
 * @property strictMode When true, rejects unknown/unmapped fields and enforces strict comma
 * validation; `false` favors lenient parsing performance.
 * @property materializeRawJsonCaptures When true, [captureRawJson] copies into an owned array
 * instead of slicing [rawData]; set by the [GhostJsonStringReader] deserialize bridge.
 * @property source String-decoding backend ([GhostSource.decodeJsonStringRange]); built via
 * [createByteArraySource] so JVM/Android use ISO-8859-1 for known 7-bit spans. Typed as
 * [ByteArrayGhostSource] so [resetSlice] can rebind its buffer without reallocating.
 * @property predictedFieldIndex Optimistic hint for [internalSelect]: the field index expected
 * next, assuming declaration-order JSON (the common case for machine-generated payloads). A hit
 * skips straight to a single compare instead of hash+verify; a miss falls back transparently, so
 * only speed is at stake, never correctness. Reset on [beginObject].
 * @property pathTracker Scratch stack for the error JSONPath, rebuilt by re-scanning the input only
 * when [throwError] builds an exception (nothing is tracked on the happy path).
 *
 * [beginObject]/[beginArray] enforce [maxDepth] against stack overflow. [getByte] masks to a
 * positive int since Kotlin's [Byte] is signed — the same `and BYTE_MASK` pattern recurs
 * throughout for direct byte access. [selectNameAndConsume] matches a field name via
 * [JsonReaderOptions]'s perfect hash and consumes the following `:`; [selectString] does the same
 * without consuming a separator (e.g. enum values). [peekStringField] is the codegen-facing alias
 * for [peekDiscriminator], used for sealed class discriminators. [throwMissingRequiredField]
 * pushes the field name onto the JSONPath itself, since that validation runs after the object
 * loop already finished — [endObject] would have popped it by then. [resetSlice] rebinds to a
 * sub-range of a buffer with no copy. `GhostProtoJsonFlatReader` overrides [nextProtoUInt64] for
 * the full [ULong] range.
 */
open class GhostJsonFlatReader(
    var rawData: ByteArray,
    var maxDepth: Int = NUM.MAX_DEPTH,
    var strictMode: Boolean = false,
    var coerceStringsToNumbers: Boolean = false,
    var coerceBooleans: Boolean = false,
    var maxCollectionSize: Int = GhostHeuristics.maxCollectionSize,
    var materializeRawJsonCaptures: Boolean = false,
) {

    @PublishedApi
    internal val source: ByteArrayGhostSource =
        createByteArraySource(data = rawData) as ByteArrayGhostSource

    var limit: Int = rawData.size

    var position: Int = 0

    var nextTokenByte: Int = SCN.RESET_TOKEN_BYTE

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
    internal val stringPoolHashes = IntArray(SCN.STR_POOL_SIZE)

    internal var lastScanContentWas7BitOnly: Boolean = false

    internal var predictedFieldIndex: Int = SCN.FIELD_PREDICTION_START

    var depth: Int = 0

    @PublishedApi
    internal var needsCommaMask: Long = 0L

    @PublishedApi
    internal var commaConsumedMask: Long = 0L

    @PublishedApi
    internal val pathTracker: GhostJsonPathTracker = GhostJsonPathTracker()

    private var sliceStart: Int = 0

    fun beginArray() {
        if (nextNonWhitespace() != TOK.OPEN_ARR_INT) throwError(EM.ERR_EXPECTED_BEGIN_ARR)
        depth++

        if (depth > maxDepth) throwError(EM.ERR_DEPTH_EXCEEDED)

        if (depth < SCN.MAX_BITMASK_DEPTH) {
            val bit = SCN.BITMASK_UNIT shl depth
            needsCommaMask = needsCommaMask and bit.inv()
            commaConsumedMask = commaConsumedMask and bit.inv()
        }

    }

    fun beginObject() {
        if (nextNonWhitespace() != TOK.OPEN_OBJ_INT) throwError(EM.ERR_EXPECTED_BEGIN_OBJ)
        predictedFieldIndex = SCN.FIELD_PREDICTION_START
        depth++

        if (depth > maxDepth) throwError(EM.ERR_DEPTH_EXCEEDED)

        if (depth < SCN.MAX_BITMASK_DEPTH) {
            val bit = SCN.BITMASK_UNIT shl depth
            needsCommaMask = needsCommaMask and bit.inv()
            commaConsumedMask = commaConsumedMask and bit.inv()
        }

    }

    fun endArray() {
        if (nextNonWhitespace() != TOK.CLOSE_ARR_INT) throwError(EM.ERR_EXPECTED_END_ARR)
        if (depth > 0) depth--

    }

    fun endObject() {
        if (nextNonWhitespace() != TOK.CLOSE_OBJ_INT) throwError(EM.ERR_EXPECTED_END_OBJ)
        if (depth > 0) depth--

    }

    @Suppress("NOTHING_TO_INLINE")
    inline fun getByte(index: Int): Int {
        return rawData[index].toInt() and TOK.BYTE_MASK
    }

    fun internalSkip(byteCount: Int) {
        position += byteCount
        nextTokenByte = SCN.RESET_TOKEN_BYTE
    }

    open fun nextDouble(): Double = nextDoubleExtension()

    open fun nextFloat(): Float = nextFloatExtension()

    open fun nextInt(): Int = nextIntExtension()

    open fun nextLong(): Long = nextLongExtension()

    fun nextNonWhitespace(): Int {
        val nextToken = peekNextToken()
        if (nextToken == -1) throwError(EM.ERR_UNEXPECTED_EOF)

        internalSkip(1)
        return nextToken
    }

    open fun nextProtoUInt64(): ULong {
        val saved = coerceStringsToNumbers
        coerceStringsToNumbers = true
        return try {
            if (peekNextToken() == TOK.QUOTE_INT) {
                nextString().toULong()
            } else {
                nextLong().toULong()
            }
        } finally {
            coerceStringsToNumbers = saved
        }
    }

    open fun nextULong(): ULong = nextProtoUInt64()

    fun nextULongOrNull(): ULong? {
        if (isNextNullValue()) {
            consumeNull()
            return null
        }
        return nextULong()
    }

    fun peekByte(): Byte = peekNextToken().toByte()

    fun peekDiscriminator(key: String = TOK.DEFAULT_DISCRIMINATOR_KEY): String? {
        if (key == TOK.DEFAULT_DISCRIMINATOR_KEY) return peekDiscriminator(key = WR.TYPE_BS)
        return peekDiscriminator(key = key.encodeUtf8())
    }

    fun peekDiscriminator(
        key: ByteString
    ): String? = GhostDiscriminatorPeeker.peek(
        source = source,
        rawData = rawData,
        isStreaming = false,
        start = position,
        limit = limit,
        key = key
    )

    fun peekNextToken(): Int {
        val cached = nextTokenByte
        if (cached != -1) return cached

        skipWhitespace()
        return nextTokenByte
    }

    fun reset(newData: ByteArray, newLimit: Int = newData.size) {
        resetSlice(buffer = newData, offset = 0, length = newLimit)
    }

    fun resetSlice(buffer: ByteArray, offset: Int, length: Int) {
        rawData = buffer
        source.data = buffer
        sliceStart = offset
        position = offset
        limit = offset + length
        nextTokenByte = SCN.RESET_TOKEN_BYTE
        depth = 0
        needsCommaMask = 0L
        commaConsumedMask = 0L
        strictMode = false
        coerceStringsToNumbers = false
        coerceBooleans = false
        maxDepth = NUM.MAX_DEPTH
        maxCollectionSize = GhostHeuristics.maxCollectionSize
        lastScanContentWas7BitOnly = false
    }

    @InternalGhostApi
    fun skipAndValidateLiteral(expected: ByteString) {
        val size = expected.size
        val literalMismatch = position + size > limit || !expected.rangeEquals(
            offset = 0,
            other = rawData,
            otherOffset = position,
            byteCount = size
        )
        if (literalMismatch) throwError(EM.ERR_EXPECTED_LITERAL + expected.utf8())

        position += size
        nextTokenByte = SCN.RESET_TOKEN_BYTE
    }

    fun skipWhitespace() {
        val data = rawData
        val byteLimit = limit
        var cursor = position
        while (true) {
            // SWAR fast path: swallow LONG_BYTES runs of ASCII space (SPACE_INT), which dominate
            // the byte volume of pretty-printed JSON indentation. SPACE_RUN_LONG is
            // byte-symmetric, so the platform byte order of ghostReadLong8 is irrelevant.
            while (cursor + SCN.LONG_BYTES <= byteLimit &&
                ghostReadLong8(data = data, index = cursor) == SCN.SPACE_RUN_LONG
            ) {
                cursor += SCN.LONG_BYTES
            }
            if (cursor >= byteLimit) {
                position = byteLimit
                nextTokenByte = SCN.MATCH_END
                return
            }
            val tokenByte = data[cursor].toInt() and TOK.BYTE_MASK
            if (tokenByte > TOK.SPACE_INT) {
                position = cursor
                nextTokenByte = tokenByte
                return
            }
            // Non-space whitespace (tab / LF / CR) or a control byte; mirror WHITESPACE_MASK.
            val isNonWhitespaceControlByte = tokenByte != TOK.SPACE_INT &&
                    tokenByte != TOK.LF_INT && tokenByte != TOK.CR_INT && tokenByte != TOK.TAB_INT
            if (isNonWhitespaceControlByte) {
                position = cursor
                nextTokenByte = tokenByte
                return
            }
            cursor++
        }
    }

    fun throwError(message: String): Nothing = throwErrorWithPath(
        message = message,
        missingKey = null
    )

    fun throwMissingRequiredField(jsonName: String): Nothing = throwErrorWithPath(
        message = "${EM.ERR_REQUIRED_FIELD_PREFIX}$jsonName${EM.ERR_REQUIRED_FIELD_SUFFIX}",
        missingKey = jsonName
    )

    private fun throwErrorWithPath(
        message: String,
        missingKey: String?
    ): Nothing {
        val errorPosition = position
        val errorEnd = if (errorPosition > limit) {
            limit
        } else {
            errorPosition
        }
        val localData = rawData
        val tracker = GhostJsonPathReconstruction.reconstruct(
            tracker = pathTracker,
            start = sliceStart,
            end = errorEnd,
            getByte = { localData[it].toInt() and TOK.BYTE_MASK },
            decodeRange = { from, to -> localData.decodeToString(startIndex = from, endIndex = to) }
        )
        if (missingKey != null) {
            tracker.finishScalarValue()
            tracker.pushKey(name = missingKey)
        }
        val errorPath = tracker.formatPath()

        throw GhostJsonException(
            baseMessage = "$message${EM.ERR_AT_POSITION_PREFIX}$errorPosition",
            computeLineCol = {
                var columnNumber = 0
                var lineNumber = 0
                var byteIndex = 0
                while (byteIndex < errorEnd) {
                    if ((rawData[byteIndex].toInt() and TOK.BYTE_MASK) == TOK.NEWLINE_INT) {
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


    /** Whether [token] closes either container kind (`]` or `}`). */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun isCloseToken(token: Int): Boolean =
        token == TOK.CLOSE_ARR_INT || token == TOK.CLOSE_OBJ_INT

    /** Skips a comma already confirmed present, rejecting one immediately followed by a close token. */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun skipCommaRejectingTrailing() {
        internalSkip(1)
        if (isCloseToken(token = peekNextToken())) throwError(EM.ERR_TRAILING_COMMA)
    }

    fun consumeArraySeparator() {
        if (strictMode && depth < SCN.MAX_BITMASK_DEPTH) {
            val bit = SCN.BITMASK_UNIT shl depth
            // If hasNext() already consumed the comma, honor that.
            if ((commaConsumedMask and bit) != SCN.RESULT_NONE) {
                commaConsumedMask = commaConsumedMask and bit.inv()
                needsCommaMask = needsCommaMask or bit
                return
            }
            val token = peekNextToken()
            if (token == TOK.COMMA_INT) {
                skipCommaRejectingTrailing()
                commaConsumedMask = commaConsumedMask or bit
            } else {
                // Whether a comma was required (a prior element exists) or not (first call at
                // this depth), a non-separator token here is only valid as the closing bracket.
                if (!isCloseToken(token = token)) throwError(EM.ERR_EXPECTED_COMMA_OR_CLOSE_ARR)
            }
            needsCommaMask = needsCommaMask or bit
        } else {
            val token = peekNextToken()
            if (token == TOK.COMMA_INT) {
                internalSkip(1)
                if (peekNextToken() == TOK.CLOSE_ARR_INT) throwError(EM.ERR_TRAILING_COMMA)
            }
        }
    }

    fun consumeKeySeparator() {
        if (nextNonWhitespace() != TOK.COLON_INT) throwError(EM.ERR_EXPECTED_COLON)
    }

    /**
     * Consumes the null value literal from the stream.
     *
     * After [peekNextToken] has already positioned on `'n'`, validates the remaining
     * `ull` bytes inline — avoids `okio.ByteString.rangeEquals` on the hot nullable path.
     */
    fun consumeNull() {
        val cursor = position
        val data = rawData
        val isNotNullLiteral = cursor + TOK.LITERAL_NULL_LEN > limit ||
            (data[cursor].toInt() and TOK.BYTE_MASK) != TOK.NULL_CHAR_INT ||
            (data[cursor + 1].toInt() and TOK.BYTE_MASK) != TOK.U_BYTE_INT ||
            (data[cursor + 2].toInt() and TOK.BYTE_MASK) != TOK.L_BYTE_INT ||
            (data[cursor + 3].toInt() and TOK.BYTE_MASK) != TOK.L_BYTE_INT
        if (isNotNullLiteral) {
            throwError(EM.ERR_EXPECTED_LITERAL + TOK.LITERAL_NULL)
        }
        position = cursor + TOK.LITERAL_NULL_LEN
        nextTokenByte = SCN.RESET_TOKEN_BYTE
    }

    /** Returns whether the current container has more elements; rejects trailing commas. */
    fun hasNext(): Boolean {
        val token = peekNextToken()
        val containerExhausted = isCloseToken(token = token) || token == SCN.MATCH_END
        if (containerExhausted) return false

        if (strictMode && depth < SCN.MAX_BITMASK_DEPTH) {
            val bit = SCN.BITMASK_UNIT shl depth
            if ((commaConsumedMask and bit) != SCN.RESULT_NONE) {
                if (token == TOK.COMMA_INT) {
                    commaConsumedMask = commaConsumedMask and bit.inv()
                    needsCommaMask = needsCommaMask or bit
                }
            }
            if ((commaConsumedMask and bit) != SCN.RESULT_NONE) {
                commaConsumedMask = commaConsumedMask and bit.inv()
                needsCommaMask = needsCommaMask or bit
            } else {
                val required = (needsCommaMask and bit) != SCN.RESULT_NONE
                if (token == TOK.COMMA_INT) {
                    if (!required) throwError(EM.ERR_UNEXPECTED_COMMA)

                    skipCommaRejectingTrailing()
                    commaConsumedMask = commaConsumedMask or bit
                    needsCommaMask = needsCommaMask and bit.inv()
                } else {
                    if (required) throwError(EM.ERR_EXPECTED_COMMA)
                    needsCommaMask = needsCommaMask or bit
                }
            }
        } else {
            if (token == TOK.COMMA_INT) skipCommaRejectingTrailing()
        }
        return true
    }

    fun isNextNullValue(): Boolean = peekNextToken() == TOK.NULL_CHAR_INT

    /** Parses the next boolean; if [coerceBooleans]
     *  also accepts `0`/`1` and matching strings. */
    fun nextBoolean(): Boolean {
        val token = peekNextToken()
        if (token == TOK.TRUE_CHAR_INT) {
            skipAndValidateLiteral(WR.TRUE_BS)
            return true
        }
        if (token == TOK.FALSE_CHAR_INT) {
            skipAndValidateLiteral(WR.FALSE_BS)
            return false
        }
        if (coerceBooleans) {
            if (token == TOK.ONE_INT) {
                internalSkip(1)
                return true
            }
            if (token == TOK.ZERO_INT) {
                internalSkip(1)
                return false
            }
            if (token == TOK.QUOTE_INT) {
                // Scans the quoted string bytes directly — no String allocation.
                val coerced = matchCoerceBooleanBytes()
                return coerced
            }
        }
        throwError(EM.ERR_EXPECTED_BOOLEAN)
    }

    fun nextBooleanOrNull(): Boolean? {
        if (peekNextToken() == TOK.NULL_CHAR_INT) {
            consumeNull()
            return null
        }
        return nextBoolean()
    }

    fun nextIntOrNull(): Int? {
        if (peekNextToken() == TOK.NULL_CHAR_INT) {
            consumeNull()
            return null
        }
        return nextInt()
    }

    /** Consumes any comma separator
     *  returns the next key or `null` if the object has ended. */
    fun nextKey(): String? {
        val token = peekNextToken()
        if (token == TOK.CLOSE_OBJ_INT) {
            return null
        }
        if (strictMode && depth < SCN.MAX_BITMASK_DEPTH) {
            val bit = SCN.BITMASK_UNIT shl depth
            // If comma was already consumed by consumeArraySeparator(), skip re-requiring it.
            if ((commaConsumedMask and bit) != SCN.RESULT_NONE) {
                commaConsumedMask = commaConsumedMask and bit.inv()
                needsCommaMask = needsCommaMask or bit
            } else {
                val required = (needsCommaMask and bit) != SCN.RESULT_NONE
                if (token == TOK.COMMA_INT) {
                    if (!required) throwError(EM.ERR_UNEXPECTED_COMMA)

                    internalSkip(1)
                    if (peekNextToken() == TOK.CLOSE_OBJ_INT) throwError(EM.ERR_TRAILING_COMMA)

                    needsCommaMask = needsCommaMask or bit
                } else {
                    if (required) throwError(EM.ERR_EXPECTED_COMMA_OR_CLOSE_OBJ)
                    needsCommaMask = needsCommaMask or bit
                }
            }
        } else {
            if (token == TOK.COMMA_INT) {
                internalSkip(1)
                if (peekNextToken() == TOK.CLOSE_OBJ_INT) throwError(EM.ERR_TRAILING_COMMA)
            }
        }
        val key = readQuotedString()
        return key
    }

    fun nextLongOrNull(): Long? {
        if (peekNextToken() == TOK.NULL_CHAR_INT) {
            consumeNull()
            return null
        }
        return nextLong()
    }

    fun nextString(): String {
        val value = readQuotedString()
        return value
    }

    fun nextStringOrNull(): String? {
        if (peekNextToken() == TOK.NULL_CHAR_INT) {
            consumeNull()
            return null
        }
        return nextString()
    }

    /** Zero-copy boolean coercion matcher
     *  delegates byte comparison to the shared helper in GhostParserUtils. */
    private fun matchCoerceBooleanBytes(): Boolean {
        val localData = rawData
        val byteLimit = limit
        val contentStart = position + 1 // skip opening
        val end = findClosingQuoteImpl(position = contentStart, limit = byteLimit) {
            localData[it].toInt() and TOK.BYTE_MASK
        }
        if (end == -1) throwError(EM.UNTERMINATED_STRING_ERROR)
        val length = end - contentStart
        position = end + 1
        nextTokenByte = SCN.RESET_TOKEN_BYTE
        return matchCoerceBooleanBytes(
            start = contentStart,
            length = length,
            onError = { throwError(EM.ERR_EXPECTED_BOOLEAN) },
            getByte = { localData[it].toInt() and TOK.BYTE_MASK },
        )
    }

    fun peekStringField(
        name: String
    ): String? = peekDiscriminator(key = name)

    fun selectNameAndConsume(options: JsonReaderOptions): Int {
        val index = internalSelect(options = options, consumeSeparator = true)
        if (index >= 0) {
        }
        return index
    }

    fun selectString(options: JsonReaderOptions): Int =
        internalSelect(options = options, consumeSeparator = false)

    /** Skips the next complete JSON value
     * (object, array, string, number, boolean, null), balancing nesting. */
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
}

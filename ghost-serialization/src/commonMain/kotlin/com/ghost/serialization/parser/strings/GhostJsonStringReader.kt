@file:OptIn(InternalGhostApi::class)
@file:Suppress("FunctionName", "NOTHING_TO_INLINE")

package com.ghost.serialization.parser.strings

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.exception.hintForJsonError
import com.ghost.serialization.parser.common.GhostHeuristics
import com.ghost.serialization.parser.common.json.GhostJsonPathReconstruction
import com.ghost.serialization.parser.common.json.GhostJsonPathTracker
import com.ghost.serialization.parser.common.byteToCharPosition
import com.ghost.serialization.parser.common.findNextNonWhitespaceImpl
import com.ghost.serialization.parser.common.isSurrogatePairStart
import com.ghost.serialization.parser.common.utf8ByteSizeAt
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.writer.strings.copyRangeToCharArray
import okio.ByteString
import com.ghost.serialization.parser.common.constants.GhostJsonErrorMessages as EM
import com.ghost.serialization.parser.common.constants.GhostJsonNumericLimits as NUM
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants as SCN
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants as WR

/**
 * Flat JSON parser for in-memory [String] payloads (the default `textChannel` path).
 *
 * Indexes a reused [rawChars] [CharArray] of UTF-16 code units — hot structure/select/number
 * work never UTF-8-encodes the document. Bridge helpers ([ensureUtf8Bytes], [sliceUtf8Bytes],
 * [captureRawJsonBytes]) lazily encode [utf8CacheBytes]/[charToByteOffsets] once per [reset],
 * only when a byte API is needed. Sibling of the byte flat reader (`GhostJsonFlatReader`) and
 * the streaming `GhostJsonReader`.
 *
 * [pathTracker] is scratch for the error JSONPath, rebuilt by re-scanning the input only when
 * [throwError] builds an exception (nothing is tracked on the happy path). [predictedFieldIndex] is an optimistic hint for the field-select fast path: the
 * next expected field index when JSON objects list fields in declaration order; reset on
 * [beginObject], and a misprediction falls back to hashed dispatch, so it never affects
 * correctness. [stringPool]/[stringPoolHashes] are a cross-call string intern pool — same
 * design as the byte flat reader's `stringPool`. [slowPathChars] is reused across
 * [readQuotedString]'s escape-decoding slow path.
 */
class GhostJsonStringReader(
    var rawData: String,
    var maxDepth: Int = NUM.MAX_DEPTH,
    var strictMode: Boolean = false,
    var coerceStringsToNumbers: Boolean = false,
    var coerceBooleans: Boolean = false,
    var maxCollectionSize: Int = GhostHeuristics.maxCollectionSize
) {
    var limit: Int = rawData.length
    var position: Int = 0
    var nextTokenByte: Int = SCN.RESET_TOKEN_BYTE
    var lastScanContentWas7BitOnly: Boolean = false
    var depth: Int = 0
    var needsCommaMask: Long = 0L
    var commaConsumedMask: Long = 0L

    @PublishedApi
    internal val pathTracker: GhostJsonPathTracker = GhostJsonPathTracker()
    internal var predictedFieldIndex: Int = SCN.FIELD_PREDICTION_START
    var rawChars: CharArray
    private var utf8CacheBytes: ByteArray? = null
    private var charToByteOffsets: IntArray? = null
    val stringPool: Array<String?> = arrayOfNulls(SCN.STR_POOL_SIZE)
    val stringPoolHashes = IntArray(SCN.STR_POOL_SIZE)
    var slowPathChars: CharArray = CharArray(WR.STRING_ESCAPE_SCRATCH_SIZE)

    init {
        val len = rawData.length
        val chars = CharArray(len)
        rawData.copyRangeToCharArray(dest = chars, destOffset = 0, startIndex = 0, endIndex = len)
        rawChars = chars
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

    inline fun getByte(index: Int): Int {
        return rawChars[index].code
    }

    fun internalSkip(charCount: Int) {
        position += charCount
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

    fun peekNextToken(): Int {
        val cached = nextTokenByte
        if (cached != -1) {
            return cached
        }
        skipWhitespace()
        return nextTokenByte
    }

    @Suppress("StringReferentialEquality")
    fun reset(newData: String, newLimit: Int = newData.length) {
        val oldData = rawData
        rawData = newData
        position = 0
        limit = newLimit
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
        predictedFieldIndex = SCN.FIELD_PREDICTION_START
        invalidateUtf8Cache()

        if (newData !== oldData) {
            val len = newData.length
            var chars = rawChars
            if (chars.size < len) {
                chars = CharArray(len)
                rawChars = chars
            }
            newData.copyRangeToCharArray(dest = chars, destOffset = 0, startIndex = 0, endIndex = len)
        }
    }

    fun skipAndValidateLiteral(expected: ByteString) {
        val size = expected.size
        if (position + size > limit) {
            throwError(EM.ERR_EXPECTED_LITERAL + expected.utf8())
        }
        val chars = rawChars
        for (i in 0 until size) {
            if (chars[position + i].code != (expected[i].toInt() and TOK.BYTE_MASK)) {
                throwError(EM.ERR_EXPECTED_LITERAL + expected.utf8())
            }
        }
        position += size
        nextTokenByte = SCN.RESET_TOKEN_BYTE
    }

    fun skipWhitespace() {
        val chars = rawChars
        val newPosition = findNextNonWhitespaceImpl(position = position, limit = limit) { chars[it].code }
        if (newPosition == SCN.MATCH_END) {
            position = limit
            nextTokenByte = SCN.MATCH_END
        } else {
            position = newPosition
            nextTokenByte = chars[newPosition].code
        }
    }

    fun throwError(message: String): Nothing = throwErrorWithPath(
        message = message,
        missingKey = null
    )

    /**
     * Throws for a missing required field, appending [jsonName] to the JSONPath so the
     * exception points at `$.….<jsonName>` (validation runs before [endObject]).
     */
    fun throwMissingRequiredField(jsonName: String): Nothing = throwErrorWithPath(
        message = "${EM.ERR_REQUIRED_FIELD_PREFIX}$jsonName${EM.ERR_REQUIRED_FIELD_SUFFIX}",
        missingKey = jsonName
    )

    private fun throwErrorWithPath(
        message: String,
        missingKey: String?
    ): Nothing {
        val errorPosition = position
        val errorEnd = if (errorPosition > limit) limit else errorPosition
        val localData = rawData
        val tracker = GhostJsonPathReconstruction.reconstruct(
            tracker = pathTracker,
            start = 0,
            end = errorEnd,
            getByte = { localData[it].code },
            decodeRange = { from, to -> localData.substring(startIndex = from, endIndex = to) }
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
                val chars = rawChars
                while (byteIndex < errorEnd) {
                    if (chars[byteIndex].code == TOK.NEWLINE_INT) {
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


    private fun invalidateUtf8Cache() {
        utf8CacheBytes = null
        charToByteOffsets = null
    }

    /**
     * Inverse of [charPositionToBytePosition] for syncing reader position after byte decoders.
     */
    @InternalGhostApi
    fun bytePositionToCharPosition(targetBytePos: Int): Int {
        return byteToCharPosition(s = rawData, targetBytePos = targetBytePos)
    }

    /**
     * Maps a char index to its UTF-8 byte offset using a cached offset table.
     */
    @InternalGhostApi
    fun charPositionToBytePosition(charPos: Int): Int {
        val safePos = charPos.coerceIn(0, limit)
        return ensureCharToByteOffsets()[safePos]
    }

    /**
     * Returns UTF-8 bytes for [rawData], encoding at most once until the next [reset].
     */
    @InternalGhostApi
    fun ensureUtf8Bytes(): ByteArray {
        var bytes = utf8CacheBytes
        if (bytes == null) {
            bytes = rawData.encodeToByteArray()
            utf8CacheBytes = bytes
        }
        return bytes
    }

    /**
     * Returns UTF-8 bytes for the char range [[charStart], [charEnd]).
     *
     * When [ensureUtf8Bytes] already materialized the payload, copies that range from the
     * cached array (used by custom-decoder bridges). Otherwise, encodes only the range —
     * avoids a full document UTF-8 pass for `captureRawJsonBytes` on large envelopes.
     */
    @InternalGhostApi
    fun sliceUtf8Bytes(charStart: Int, charEnd: Int): ByteArray {
        val cached = utf8CacheBytes
        if (cached != null) {
            return cached.copyOfRange(
                charPositionToBytePosition(charPos = charStart),
                charPositionToBytePosition(charPos = charEnd),
            )
        }
        return rawData.substring(charStart, charEnd).encodeToByteArray()
    }

    private fun ensureCharToByteOffsets(): IntArray {
        val cached = charToByteOffsets
        if (cached != null && cached.size == limit + 1) {
            return cached
        }
        val currentLimit = limit
        val table = IntArray(currentLimit + 1)
        var bytePos = 0
        var charIndex = 0
        table[0] = 0
        while (charIndex < currentLimit) {
            table[charIndex] = bytePos
            val isSurrogatePair = isSurrogatePairStart(s = rawData, index = charIndex)
            bytePos += utf8ByteSizeAt(s = rawData, index = charIndex)
            if (isSurrogatePair) {
                table[charIndex + 1] = bytePos
                charIndex += 2
            } else {
                charIndex++
            }
        }
        table[currentLimit] = bytePos
        charToByteOffsets = table
        return table
    }

}

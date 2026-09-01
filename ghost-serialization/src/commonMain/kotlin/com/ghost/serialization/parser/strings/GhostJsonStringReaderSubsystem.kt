@file:Suppress("NOTHING_TO_INLINE")
@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser.strings

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.GhostDiscriminatorPeeker
import com.ghost.serialization.parser.common.GhostHeuristics.initialCollectionCapacity
import com.ghost.serialization.parser.common.JsonReaderOptions
import com.ghost.serialization.parser.common.beginArrayCore
import com.ghost.serialization.parser.common.beginObjectCore
import com.ghost.serialization.parser.common.computeKeyHashCore
import com.ghost.serialization.parser.common.consumeArraySeparatorCore
import com.ghost.serialization.parser.common.consumeKeySeparatorCore
import com.ghost.serialization.parser.common.endArrayCore
import com.ghost.serialization.parser.common.endObjectCore
import com.ghost.serialization.parser.common.handleSelectNoMatchCore
import com.ghost.serialization.parser.common.hasNextCore
import com.ghost.serialization.parser.common.nextBooleanCore
import com.ghost.serialization.parser.common.nextKeyCommaPreambleCore
import com.ghost.serialization.parser.common.nextOrNullCore
import com.ghost.serialization.parser.common.selectValidateCommasCore
import com.ghost.serialization.parser.common.skipValueCore
import com.ghost.serialization.parser.common.GhostJsonConstants as C


fun GhostJsonStringReader.beginObject() {
    beginObjectCore(
        nextNonWhitespace = { nextNonWhitespace() },
        getDepth = { depth },
        setDepth = { depth = it },
        maxDepth = maxDepth,
        getNeedsCommaMask = { needsCommaMask },
        setNeedsCommaMask = { needsCommaMask = it },
        getCommaConsumedMask = { commaConsumedMask },
        setCommaConsumedMask = { commaConsumedMask = it },
        setPredictedFieldIndex = { predictedFieldIndex = it },
        throwError = { throwError(it) },
    )
    pathTracker.pushObject()
}

fun GhostJsonStringReader.endObject() {
    endObjectCore(
        nextNonWhitespace = { nextNonWhitespace() },
        getDepth = { depth },
        setDepth = { depth = it },
        throwError = { throwError(it) },
    )
    pathTracker.finishObjectValue()
}

fun GhostJsonStringReader.beginArray() {
    beginArrayCore(
        nextNonWhitespace = { nextNonWhitespace() },
        getDepth = { depth },
        setDepth = { depth = it },
        maxDepth = maxDepth,
        getNeedsCommaMask = { needsCommaMask },
        setNeedsCommaMask = { needsCommaMask = it },
        getCommaConsumedMask = { commaConsumedMask },
        setCommaConsumedMask = { commaConsumedMask = it },
        throwError = { throwError(it) },
    )
    pathTracker.pushArray()
}

fun GhostJsonStringReader.endArray() {
    endArrayCore(
        nextNonWhitespace = { nextNonWhitespace() },
        getDepth = { depth },
        setDepth = { depth = it },
        throwError = { throwError(it) },
    )
    pathTracker.finishArrayValue()
}

fun GhostJsonStringReader.hasNext(): Boolean {
    val hasMore = hasNextCore(
        peekNextToken = { peekNextToken() },
        strictMode = strictMode,
        depth = depth,
        getNeedsCommaMask = { needsCommaMask },
        setNeedsCommaMask = { needsCommaMask = it },
        getCommaConsumedMask = { commaConsumedMask },
        setCommaConsumedMask = { commaConsumedMask = it },
        internalSkip = { internalSkip(it) },
        throwError = { throwError(it) },
    )
    if (hasMore) {
        pathTracker.enterArrayElement()
    }
    return hasMore
}

fun GhostJsonStringReader.nextKey(): String? {
    if (!nextKeyCommaPreambleCore(
            peekNextToken = { peekNextToken() },
            strictMode = strictMode,
            depth = depth,
            getNeedsCommaMask = { needsCommaMask },
            setNeedsCommaMask = { needsCommaMask = it },
            getCommaConsumedMask = { commaConsumedMask },
            setCommaConsumedMask = { commaConsumedMask = it },
            internalSkip = { internalSkip(it) },
            throwError = { throwError(it) },
        )
    ) {
        return null
    }
    val key = readQuotedString()
    pathTracker.pushKey(key)
    return key
}

fun GhostJsonStringReader.consumeKeySeparator() {
    consumeKeySeparatorCore(
        nextNonWhitespace = { nextNonWhitespace() },
        throwError = { throwError(it) },
    )
}

fun GhostJsonStringReader.consumeArraySeparator() {
    consumeArraySeparatorCore(
        strictMode = strictMode,
        depth = depth,
        getNeedsCommaMask = { needsCommaMask },
        setNeedsCommaMask = { needsCommaMask = it },
        getCommaConsumedMask = { commaConsumedMask },
        setCommaConsumedMask = { commaConsumedMask = it },
        peekNextToken = { peekNextToken() },
        internalSkip = { internalSkip(it) },
        throwError = { throwError(it) },
    )
}

fun GhostJsonStringReader.nextBoolean(): Boolean {
    val value = nextBooleanCore(
        peekNextToken = { peekNextToken() },
        skipAndValidateLiteral = { skipAndValidateLiteral(it) },
        coerceBooleans = coerceBooleans,
        internalSkip = { internalSkip(it) },
        matchCoerceBooleanBytes = { matchCoerceBooleanBytes() },
        throwError = { throwError(it) },
    )
    pathTracker.finishScalarValue()
    return value
}

fun GhostJsonStringReader.nextString(): String {
    val value = readQuotedString()
    pathTracker.finishScalarValue()
    return value
}

/**
 * Reads a JSON string value that must contain exactly one [Char].
 */
fun GhostJsonStringReader.nextChar(): Char {
    if (nextNonWhitespace() != C.QUOTE_INT) {
        throwError(C.ERR_EXPECTED_QUOTE)
    }

    val start = position
    val end = findClosingQuote(start, limit)
    if (end != C.MATCH_END) {
        val length = end - start
        position = end + C.SINGLE_CHAR_SIZE
        nextTokenByte = C.RESET_TOKEN_BYTE
        when {
            length == 0 -> throwError(C.ERR_EXPECTED_SINGLE_CHAR_STRING)
            length == C.SINGLE_CHAR_JSON_LENGTH -> {
                pathTracker.finishScalarValue()
                return rawData[start]
            }
            else -> throwError(C.ERR_SINGLE_CHAR_STRING_WRONG_LENGTH + length)
        }
    }

    position = start - 1
    val decoded = readQuotedString()
    if (decoded.length != C.SINGLE_CHAR_JSON_LENGTH) {
        throwError(C.ERR_SINGLE_CHAR_STRING_WRONG_LENGTH + decoded.length)
    }
    pathTracker.finishScalarValue()
    return decoded[0]
}

fun GhostJsonStringReader.isNextNullValue(): Boolean = peekNextToken() == C.NULL_CHAR_INT

fun GhostJsonStringReader.consumeNull() {
    val cursor = position
    val chars = rawChars
    if (cursor + 4 > limit ||
        chars[cursor].code != C.NULL_CHAR_INT ||
        chars[cursor + 1].code != C.U_BYTE_INT ||
        chars[cursor + 2].code != C.L_BYTE_INT ||
        chars[cursor + 3].code != C.L_BYTE_INT
    ) {
        throwError(C.ERR_EXPECTED_LITERAL + C.LITERAL_NULL)
    }
    position = cursor + 4
    nextTokenByte = C.RESET_TOKEN_BYTE
    pathTracker.finishScalarValue()
}

/** Reads a JSON string, or `null` when the next token is the `null` literal. */
fun GhostJsonStringReader.nextStringOrNull(): String? =
    nextOrNullCore(
        peekNextToken = { peekNextToken() },
        consumeNull = { consumeNull() },
        readValue = { nextString() },
    )

/** Reads a JSON int, or `null` when the next token is the `null` literal. */
fun GhostJsonStringReader.nextIntOrNull(): Int? =
    nextOrNullCore(
        peekNextToken = { peekNextToken() },
        consumeNull = { consumeNull() },
        readValue = { nextInt() },
    )

/** Reads a JSON long, or `null` when the next token is the `null` literal. */
fun GhostJsonStringReader.nextLongOrNull(): Long? =
    nextOrNullCore(
        peekNextToken = { peekNextToken() },
        consumeNull = { consumeNull() },
        readValue = { nextLong() },
    )

/** Reads a JSON unsigned long, or `null` when the next token is the `null` literal. */
fun GhostJsonStringReader.nextULongOrNull(): ULong? =
    nextOrNullCore(
        peekNextToken = { peekNextToken() },
        consumeNull = { consumeNull() },
        readValue = { nextULong() },
    )

/** Reads a JSON boolean, or `null` when the next token is the `null` literal. */
fun GhostJsonStringReader.nextBooleanOrNull(): Boolean? =
    nextOrNullCore(
        peekNextToken = { peekNextToken() },
        consumeNull = { consumeNull() },
        readValue = { nextBoolean() },
    )

internal inline fun GhostJsonStringReader.findClosingQuote(start: Int, limit: Int): Int {
    var currentPosition = start
    val chars = rawChars
    val escapeMasks = C.ESCAPE_MASKS
    val unrollStep = 4
    val indexOffset1 = 1
    val indexOffset2 = 2
    val indexOffset3 = 3

    val localAsciiLimit = C.ASCII_LIMIT
    val localBitmaskShift = C.BITMASK_SHIFT
    val localBitmaskIndexMask = C.BITMASK_INDEX_MASK
    val localBitmaskUnit = C.BITMASK_UNIT
    val localResultNone = C.RESULT_NONE
    val localQuoteInt = C.QUOTE_INT
    val localMatchEnd = C.MATCH_END

    while (currentPosition + indexOffset3 < limit) {
        val byte0 = chars[currentPosition].code
        if (byte0 < localAsciiLimit &&
            ((escapeMasks[byte0 shr localBitmaskShift] shr
                    (byte0 and localBitmaskIndexMask)) and localBitmaskUnit != localResultNone)
        ) {
            if (byte0 == localQuoteInt) return currentPosition
            return localMatchEnd
        }
        val byte1 = chars[currentPosition + indexOffset1].code
        if (byte1 < localAsciiLimit &&
            ((escapeMasks[byte1 shr localBitmaskShift] shr
                    (byte1 and localBitmaskIndexMask)) and localBitmaskUnit != localResultNone)
        ) {
            if (byte1 == localQuoteInt) return currentPosition + indexOffset1
            return localMatchEnd
        }
        val byte2 = chars[currentPosition + indexOffset2].code
        if (byte2 < localAsciiLimit &&
            ((escapeMasks[byte2 shr localBitmaskShift] shr
                    (byte2 and localBitmaskIndexMask)) and localBitmaskUnit != localResultNone)
        ) {
            if (byte2 == localQuoteInt) return currentPosition + indexOffset2
            return localMatchEnd
        }
        val byte3 = chars[currentPosition + indexOffset3].code
        if (byte3 < localAsciiLimit &&
            ((escapeMasks[byte3 shr localBitmaskShift] shr
                    (byte3 and localBitmaskIndexMask)) and localBitmaskUnit != localResultNone)
        ) {
            if (byte3 == localQuoteInt) return currentPosition + indexOffset3
            return localMatchEnd
        }
        currentPosition += unrollStep
    }

    while (currentPosition < limit) {
        val singleByte = chars[currentPosition].code
        if (singleByte < localAsciiLimit &&
            ((escapeMasks[singleByte shr localBitmaskShift] shr
                    (singleByte and localBitmaskIndexMask)) and localBitmaskUnit != localResultNone)
        ) {
            if (singleByte == localQuoteInt) {
                return currentPosition
            }
            return localMatchEnd
        }
        currentPosition++
    }
    return localMatchEnd
}

private fun GhostJsonStringReader.matchCoerceBooleanBytes(): Boolean {
    val chars = rawChars
    val charLimit = limit
    val contentStart = position + 1
    val end = findClosingQuote(contentStart, charLimit)
    if (end == -1) throwError(C.UNTERMINATED_STRING_ERROR)
    val length = end - contentStart
    position = end + 1
    nextTokenByte = C.RESET_TOKEN_BYTE
    return com.ghost.serialization.parser.common.matchCoerceBooleanBytes(
        start = contentStart,
        length = length,
        onError = { throwError(C.ERR_EXPECTED_BOOLEAN) },
        getByte = { chars[it].code },
    )
}

fun GhostJsonStringReader.selectNameAndConsume(options: JsonReaderOptions): Int {
    val index = internalSelect(options, consumeSeparator = true)
    if (index >= 0) {
        pathTracker.pushKey(options.rawStrings[index])
    }
    return index
}

fun GhostJsonStringReader.selectString(options: JsonReaderOptions): Int =
    internalSelect(options, consumeSeparator = false)

private fun GhostJsonStringReader.internalSelect(
    options: JsonReaderOptions,
    consumeSeparator: Boolean
): Int {
    var token = peekNextToken()
    if (token == C.CLOSE_OBJ_INT) {
        return -1
    }

    token = selectValidateCommas(token, consumeSeparator)

    if (token != C.QUOTE_INT) {
        throwExpectedKeyOrStringError(consumeSeparator)
    }
    val start = position + 1
    val charLimit = limit
    val chars = rawChars

    // Optimistic in-order field match: compare the key against the predicted candidate in one
    // pass, skipping findClosingQuote + hash + verify on a hit.
    val predicted = predictedFieldIndex
    val candidates = options.rawChars
    if (predicted < candidates.size) {
        val candidate = candidates[predicted]
        val candidateLength = candidate.size
        val keyEnd = start + candidateLength
        if (candidateLength > 0 && keyEnd < charLimit && chars[keyEnd].code == C.QUOTE_INT) {
            var matchedOffset = 0
            while (matchedOffset + 3 < candidateLength &&
                chars[start + matchedOffset] == candidate[matchedOffset] &&
                chars[start + matchedOffset + 1] == candidate[matchedOffset + 1] &&
                chars[start + matchedOffset + 2] == candidate[matchedOffset + 2] &&
                chars[start + matchedOffset + 3] == candidate[matchedOffset + 3]
            ) {
                matchedOffset += 4
            }
            while (matchedOffset < candidateLength &&
                chars[start + matchedOffset] == candidate[matchedOffset]
            ) {
                matchedOffset++
            }
            if (matchedOffset == candidateLength) {
                predictedFieldIndex = predicted + 1
                val newPos = keyEnd + 1
                position = newPos
                nextTokenByte = C.RESET_TOKEN_BYTE
                if (consumeSeparator) {
                    if (newPos < charLimit && chars[newPos].code == C.COLON_INT) {
                        position = newPos + 1
                    } else {
                        consumeKeySeparator()
                    }
                }
                return predicted
            }
        }
    }

    val end = findClosingQuote(start, charLimit)

    if (end == -1) {
        throwUnterminatedStringError()
    }

    val length = end - start
    val key = computeKeyHash(start, length, options.hasCollisions)

    val dispatchTable = options.stringDispatch
    val hasIndex =
        ((key * options.multiplier + length) shr options.shift) and (dispatchTable.size - 1)
    val index = dispatchTable[hasIndex]

    if (index != C.MATCH_END) {
        if (verifyKeyMatch(start, length, options.rawChars[index], consumeSeparator)) {
            predictedFieldIndex = index + 1
            return index
        }
    }

    return handleSelectNoMatch(start, end, consumeSeparator)
}

private fun GhostJsonStringReader.selectValidateCommas(token: Int, consumeSeparator: Boolean): Int =
    selectValidateCommasCore(
        token = token,
        consumeSeparator = consumeSeparator,
        strictMode = strictMode,
        depth = depth,
        getNeedsCommaMask = { needsCommaMask },
        setNeedsCommaMask = { needsCommaMask = it },
        getCommaConsumedMask = { commaConsumedMask },
        setCommaConsumedMask = { commaConsumedMask = it },
        peekNextToken = { peekNextToken() },
        internalSkip = { internalSkip(it) },
        throwError = { throwError(it) },
    )

private fun GhostJsonStringReader.handleSelectNoMatch(
    start: Int,
    end: Int,
    consumeSeparator: Boolean,
): Int =
    handleSelectNoMatchCore(
        start = start,
        end = end,
        consumeSeparator = consumeSeparator,
        strictMode = strictMode,
        limit = limit,
        getByte = { getByte(it) },
        setPosition = { position = it },
        setNextTokenByte = { nextTokenByte = it },
        consumeKeySeparator = { consumeKeySeparator() },
        decodeUnknownKey = { s, e -> rawData.substring(s, e) },
        throwError = { throwError(it) },
    )

private fun GhostJsonStringReader.throwExpectedKeyOrStringError(consumeSeparator: Boolean) {
    throwError(if (consumeSeparator) C.ERR_EXPECTED_KEY else C.ERR_EXPECTED_STRING)
}

private fun GhostJsonStringReader.throwUnterminatedStringError() {
    throwError(C.UNTERMINATED_STRING_ERROR)
}

private fun GhostJsonStringReader.computeKeyHash(
    start: Int,
    length: Int,
    hasCollisions: Boolean,
): Int =
    computeKeyHashCore(start, length, hasCollisions) { getByte(it) }

private inline fun GhostJsonStringReader.verifyKeyMatch(
    start: Int,
    length: Int,
    expected: CharArray,
    consumeSeparator: Boolean
): Boolean {
    if (expected.size == length) {
        val chars = rawChars
        var index = 0
        while (index + 3 < length) {
            if (chars[start + index] != expected[index]) return false
            if (chars[start + index + 1] != expected[index + 1]) return false
            if (chars[start + index + 2] != expected[index + 2]) return false
            if (chars[start + index + 3] != expected[index + 3]) return false
            index += 4
        }
        while (index < length) {
            if (chars[start + index] != expected[index]) return false
            index++
        }
        val endPos = start + length
        val newPos = endPos + C.SINGLE_CHAR_SIZE
        position = newPos
        nextTokenByte = C.RESET_TOKEN_BYTE
        if (consumeSeparator) {
            if (newPos < limit) {
                val colonToken = chars[newPos].code
                if (colonToken == C.COLON_INT) {
                    position = newPos + C.SINGLE_CHAR_SIZE
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

fun GhostJsonStringReader.peekStringField(name: String): String? {
    return GhostDiscriminatorPeeker.peekChars(
        chars = rawChars,
        rawData = rawData,
        start = position,
        limit = limit,
        key = name,
    )
}

fun GhostJsonStringReader.skipValue() {
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

inline fun <T> GhostJsonStringReader.readList(crossinline itemParser: () -> T): List<T> {
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

inline fun <T> GhostJsonStringReader.readSet(crossinline itemParser: () -> T): Set<T> {
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

inline fun <K, V> GhostJsonStringReader.readMap(
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
        if (depth < C.MAX_BITMASK_DEPTH) {
            val bit = C.BITMASK_UNIT shl depth
            commaConsumedMask = commaConsumedMask and bit.inv()
            needsCommaMask = needsCommaMask or bit
        }
        if (map.size > maxSize) {
            throwError("${C.ERR_MAX_COLLECTION_SIZE} ($maxSize)")
        }
    }
    return map
}

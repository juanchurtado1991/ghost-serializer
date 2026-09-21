@file:Suppress("NOTHING_TO_INLINE")
@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser.streaming

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.bytes.ghostReadLong8
import com.ghost.serialization.parser.bytes.ghostSWARLengthMasks
import com.ghost.serialization.parser.common.GhostHeuristics
import com.ghost.serialization.parser.common.GhostHeuristics.initialCollectionCapacity
import com.ghost.serialization.parser.common.GhostJsonConstants
import com.ghost.serialization.parser.common.JsonReaderOptions
import com.ghost.serialization.parser.common.beginArrayCore
import com.ghost.serialization.parser.common.beginObjectCore
import com.ghost.serialization.parser.common.computeKeyHashCore
import com.ghost.serialization.parser.common.consumeArraySeparatorCore
import com.ghost.serialization.parser.common.consumeKeySeparatorCore
import com.ghost.serialization.parser.common.endArrayCore
import com.ghost.serialization.parser.common.endObjectCore
import com.ghost.serialization.parser.common.findClosingQuoteImpl
import com.ghost.serialization.parser.common.handleSelectNoMatchCore
import com.ghost.serialization.parser.common.hasNextCore
import com.ghost.serialization.parser.common.nextBooleanCore
import com.ghost.serialization.parser.common.nextKeyCommaPreambleCore
import com.ghost.serialization.parser.common.nextOrNullCore
import com.ghost.serialization.parser.common.scanStringImpl
import com.ghost.serialization.parser.common.selectValidateCommasCore
import com.ghost.serialization.parser.common.skipValueCore
import com.ghost.serialization.parser.common.GhostJsonConstants as C


/**
 * Starts parsing a JSON object, enforcing [GhostJsonReader.maxDepth] to guard against stack overflow.
 *
 * @throws GhostJsonException if the token is invalid or [GhostJsonReader.maxDepth] is exceeded.
 */
fun GhostJsonReader.beginObject() {
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

/**
 * Finishes parsing a JSON object.
 *
 * @throws GhostJsonException if the token is not `}`.
 */
fun GhostJsonReader.endObject() {
    endObjectCore(
        nextNonWhitespace = { nextNonWhitespace() },
        getDepth = { depth },
        setDepth = { depth = it },
        throwError = { throwError(it) },
    )
    pathTracker.finishObjectValue()
}

/**
 * Starts parsing a JSON array, enforcing [GhostJsonReader.maxDepth] to guard against stack exhaustion.
 *
 * @throws GhostJsonException if the token is invalid or [GhostJsonReader.maxDepth] is exceeded.
 */
fun GhostJsonReader.beginArray() {
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

/**
 * Finishes parsing a JSON array.
 *
 * @throws GhostJsonException if the token is not `]`.
 */
fun GhostJsonReader.endArray() {
    endArrayCore(
        nextNonWhitespace = { nextNonWhitespace() },
        getDepth = { depth },
        setDepth = { depth = it },
        throwError = { throwError(it) },
    )
    pathTracker.finishArrayValue()
}

/**
 * Returns whether the current object or array has more elements. Rejects trailing commas
 * (a comma immediately followed by `]` or `}`) rather than silently accepting them.
 *
 * @throws GhostJsonException if a trailing comma is detected or input is invalid.
 */
fun GhostJsonReader.hasNext(): Boolean {
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

/**
 * Consumes the optional separator comma and decodes the next JSON key name.
 *
 * @return The decoded key, or `null` if the object has ended.
 * @throws GhostJsonException if a trailing comma is detected or the key string is malformed.
 */
fun GhostJsonReader.nextKey(): String? {
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

/** Consumes the `:` key-value separator. @throws GhostJsonException if not found. */
fun GhostJsonReader.consumeKeySeparator() {
    consumeKeySeparatorCore(
        nextNonWhitespace = { nextNonWhitespace() },
        throwError = { throwError(it) },
    )
}

/** Consumes the `,` array item separator if it is next in the stream. */
fun GhostJsonReader.consumeArraySeparator() {
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

/**
 * Parses the next boolean. If [GhostJsonReader.coerceBooleans] is set, also accepts `0`/`1` and the
 * strings `"true"/"yes"/"on"/"1"/"y"` and `"false"/"no"/"off"/"0"/"n"`.
 *
 * @throws GhostJsonException if the token is not a boolean or fails to be coerced.
 */
fun GhostJsonReader.nextBoolean(): Boolean {
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

/** Decodes the next JSON string value. @throws GhostJsonException if the next token is not a string. */
fun GhostJsonReader.nextString(): String {
    val value = readQuotedString()
    pathTracker.finishScalarValue()
    return value
}

/** proto3 `uint64` on the streaming reader channel — quoted decimal string on the wire. */
fun GhostJsonReader.nextProtoUInt64(): ULong {
    val saved = coerceStringsToNumbers
    coerceStringsToNumbers = true
    return try {
        nextString().toULong()
    } finally {
        coerceStringsToNumbers = saved
    }
}

/**
 * Reads a JSON string value that must contain exactly one [Char].
 * Fast path avoids [String] allocation for a single unescaped ASCII/Latin-1 code unit.
 */
fun GhostJsonReader.nextChar(): Char {
    if (nextNonWhitespace() != C.QUOTE_INT) {
        throwError(C.ERR_EXPECTED_QUOTE)
    }

    val start = position
    val scanResult = scanStringImpl(start, limit) { getByte(it) }

    if (scanResult != -1L) {
        val length = ((scanResult and C.SCAN_LENGTH_MASK) ushr C.SCAN_LENGTH_SHIFT).toInt()
        val only7Bit = (scanResult and C.SCAN_7BIT_BIT) != 0L
        val end = start + length
        if (length == C.SINGLE_CHAR_JSON_LENGTH && only7Bit) {
            position = end + 1
            nextTokenByte = C.RESET_TOKEN_BYTE
            pathTracker.finishScalarValue()
            return getByte(start).toChar()
        }
        if (length == 0) {
            position = end + 1
            nextTokenByte = C.RESET_TOKEN_BYTE
            throwError(C.ERR_EXPECTED_SINGLE_CHAR_STRING)
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

/** Peeks (without advancing) whether the next value is JSON `null`. */
fun GhostJsonReader.isNextNullValue(): Boolean =
    peekNextToken() == C.NULL_CHAR_INT

/** Consumes the JSON `null` literal. @throws GhostJsonException if the bytes don't match `null`. */
fun GhostJsonReader.consumeNull() {
    if (isStreaming) {
        skipAndValidateLiteral(C.NULL_BS)
        pathTracker.finishScalarValue()
        return
    }
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
fun GhostJsonReader.nextStringOrNull(): String? =
    nextOrNullCore(
        peekNextToken = { peekNextToken() },
        consumeNull = { consumeNull() },
        readValue = { nextString() },
    )

/** Reads a JSON int, or `null` when the next token is the `null` literal. */
fun GhostJsonReader.nextIntOrNull(): Int? =
    nextOrNullCore(
        peekNextToken = { peekNextToken() },
        consumeNull = { consumeNull() },
        readValue = { nextInt() },
    )

/** Reads a JSON long, or `null` when the next token is the `null` literal. */
fun GhostJsonReader.nextLongOrNull(): Long? =
    nextOrNullCore(
        peekNextToken = { peekNextToken() },
        consumeNull = { consumeNull() },
        readValue = { nextLong() },
    )

/** Reads a JSON unsigned long, or `null` when the next token is the `null` literal. */
fun GhostJsonReader.nextULongOrNull(): ULong? =
    nextOrNullCore(
        peekNextToken = { peekNextToken() },
        consumeNull = { consumeNull() },
        readValue = { nextULong() },
    )

/** Reads a JSON boolean, or `null` when the next token is the `null` literal. */
fun GhostJsonReader.nextBooleanOrNull(): Boolean? =
    nextOrNullCore(
        peekNextToken = { peekNextToken() },
        consumeNull = { consumeNull() },
        readValue = { nextBoolean() },
    )

/** Zero-copy boolean coercion matcher; delegates byte comparison to the shared helper in GhostParserUtils. */
private fun GhostJsonReader.matchCoerceBooleanBytes(): Boolean {
    val byteLimit = limit
    val contentStart = position + 1 // skip opening '"'
    val end = if (isStreaming) {
        source.findClosingQuote(contentStart, byteLimit)
    } else {
        findClosingQuoteImpl(contentStart, byteLimit) { getByte(it) }
    }
    if (end == -1) throwError(C.UNTERMINATED_STRING_ERROR)
    val length = end - contentStart
    position = end + 1
    nextTokenByte = C.RESET_TOKEN_BYTE
    return com.ghost.serialization.parser.common.matchCoerceBooleanBytes(
        start = contentStart,
        length = length,
        onError = { throwError(C.ERR_EXPECTED_BOOLEAN) },
        getByte = { getByte(it) },
    )
}

/**
 * Identifies the next field name via [options]'s perfect hash (no HashMap lookup or String allocation)
 * and consumes the following `:` separator.
 *
 * @return The 0-based field index, [GhostJsonConstants.MATCH_NONE] if unknown key, or `-1` if object ends.
 */
fun GhostJsonReader.selectNameAndConsume(options: JsonReaderOptions): Int {
    val index = internalSelect(options, consumeSeparator = true)
    if (index >= 0) {
        pathTracker.pushKey(options.rawStrings[index])
    }
    return index
}

/**
 * Matches a string token (e.g. an enum value) against [options], without consuming a `:` separator.
 *
 * @return The index of the matched option, or [GhostJsonConstants.MATCH_NONE] if no match.
 */
fun GhostJsonReader.selectString(options: JsonReaderOptions): Int =
    internalSelect(options, consumeSeparator = false)

/**
 * Shared perfect-hash matcher backing [selectNameAndConsume] and [selectString]. Tries the
 * in-order predicted field first, then falls back to the hash/dispatch table.
 *
 * @return The matched options index, `-1` on object closing, or [GhostJsonConstants.MATCH_NONE] if not found.
 */
private fun GhostJsonReader.internalSelect(
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
    val byteLimit = limit

    // Optimistic in-order field match: most objects list fields in declaration order,
    // so compare the key directly against the predicted candidate in a single pass.
    val predicted = predictedFieldIndex
    val rawBytes = options.rawBytes
    if (predicted < rawBytes.size) {
        val candidate = rawBytes[predicted]
        val candidateLength = candidate.size
        val keyEnd = start + candidateLength
        if (candidateLength > 0 && keyEnd < byteLimit) {
            val matched = if (!isStreaming) {
                val localData = rawData
                if ((localData[keyEnd].toInt() and C.BYTE_MASK) != C.QUOTE_INT) {
                    false
                } else if (candidateLength <= C.LONG_BYTES && start + C.LONG_BYTES <= localData.size) {
                    // Most real field names are short: a single masked read/compare beats the
                    // loop-then-scalar-tail path below, which for a short candidateLength never
                    // enters its SWAR loop body at all.
                    val inputLong = ghostReadLong8(localData, start) and ghostSWARLengthMasks[candidateLength]
                    inputLong == ghostReadLong8(options.predictedKeyPadded[predicted], 0)
                } else {
                    var matchedOffset = 0
                    while (matchedOffset + C.LONG_BYTES <= candidateLength &&
                        ghostReadLong8(localData, start + matchedOffset) ==
                        ghostReadLong8(candidate, matchedOffset)
                    ) {
                        matchedOffset += C.LONG_BYTES
                    }
                    while (matchedOffset < candidateLength &&
                        localData[start + matchedOffset] == candidate[matchedOffset]
                    ) {
                        matchedOffset++
                    }
                    matchedOffset == candidateLength
                }
            } else {
                // The stream's limit is unknown (Int.MAX_VALUE), so the bounds check above
                // cannot rule out a candidate longer than the remaining document.
                if (source.byteOrEof(keyEnd) != C.QUOTE_INT) {
                    false
                } else {
                    var matchedOffset = 0
                    while (matchedOffset < candidateLength &&
                        getByte(start + matchedOffset) ==
                        (candidate[matchedOffset].toInt() and C.BYTE_MASK)
                    ) {
                        matchedOffset++
                    }
                    matchedOffset == candidateLength
                }
            }
            if (matched) {
                predictedFieldIndex = predicted + 1
                val newPos = keyEnd + 1
                position = newPos
                nextTokenByte = C.RESET_TOKEN_BYTE
                if (consumeSeparator) {
                    val separator = when {
                        newPos >= byteLimit -> C.MATCH_END
                        isStreaming -> source.byteOrEof(newPos)
                        else -> getByte(newPos)
                    }
                    if (separator == C.COLON_INT) {
                        position = newPos + 1
                    } else {
                        consumeKeySeparator()
                    }
                }
                return predicted
            }
        }
    }

    val end = if (isStreaming) {
        source.findClosingQuote(start, byteLimit)
    } else {
        val localData = rawData
        findClosingQuoteImpl(start, byteLimit) {
            localData[it].toInt() and C.BYTE_MASK
        }
    }

    if (end == -1) {
        throwUnterminatedStringError()
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

    return handleSelectNoMatch(start, end, consumeSeparator)
}

private fun GhostJsonReader.selectValidateCommas(token: Int, consumeSeparator: Boolean): Int =
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

private fun GhostJsonReader.handleSelectNoMatch(
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
        decodeUnknownKey = { s, e -> source.decodeToString(s, e) },
        throwError = { throwError(it) },
    )

private fun GhostJsonReader.throwExpectedKeyOrStringError(consumeSeparator: Boolean) {
    throwError(if (consumeSeparator) C.ERR_EXPECTED_KEY else C.ERR_EXPECTED_STRING)
}

private fun GhostJsonReader.throwUnterminatedStringError() {
    throwError(C.UNTERMINATED_STRING_ERROR)
}

private fun GhostJsonReader.computeKeyHash(start: Int, length: Int, hasCollisions: Boolean): Int =
    computeKeyHashCore(start, length, hasCollisions) { getByte(it) }

/**
 * Confirms the dispatch-table candidate matches the actual key bytes (guards against hash collisions),
 * comparing in 4-byte blocks without allocating a String. On success, advances past the key and,
 * if [consumeSeparator], the `:`.
 */
private fun GhostJsonReader.verifyKeyMatch(
    start: Int,
    length: Int,
    expected: ByteArray,
    consumeSeparator: Boolean
): Boolean {
    if (expected.size == length) {
        var matchedOffset = 0
        if (!isStreaming) {
            val localData = rawData
            while (matchedOffset + 3 < length) {
                if (localData[start + matchedOffset] != expected[matchedOffset]) return false
                if (localData[start + matchedOffset + 1] != expected[matchedOffset + 1]) return false
                if (localData[start + matchedOffset + 2] != expected[matchedOffset + 2]) return false
                if (localData[start + matchedOffset + 3] != expected[matchedOffset + 3]) return false
                matchedOffset += 4
            }
            while (matchedOffset < length) {
                if (localData[start + matchedOffset] != expected[matchedOffset]) return false
                matchedOffset++
            }
        } else {
            val localSource = source
            while (matchedOffset + 3 < length) {
                if (localSource[start + matchedOffset].toByte() != expected[matchedOffset]) return false
                if (localSource[start + matchedOffset + 1].toByte() != expected[matchedOffset + 1]) return false
                if (localSource[start + matchedOffset + 2].toByte() != expected[matchedOffset + 2]) return false
                if (localSource[start + matchedOffset + 3].toByte() != expected[matchedOffset + 3]) return false
                matchedOffset += 4
            }
            while (matchedOffset < length) {
                if (localSource[start + matchedOffset].toByte() != expected[matchedOffset]) return false
                matchedOffset++
            }
        }
        val endPos = start + length
        val newPos = endPos + 1
        position = newPos
        nextTokenByte = C.RESET_TOKEN_BYTE
        if (consumeSeparator) {
            val byteLimit = limit
            if (newPos < byteLimit) {
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
 * Peeks a key's string value without permanently advancing the cursor. Used to read sealed class
 * type discriminators (e.g. `"type"`) before choosing the subclass deserializer.
 */
fun GhostJsonReader.peekStringField(name: String): String? {
    return peekDiscriminator(name)
}

/** Skips the next complete JSON value (object, array, string, number, boolean, null), balancing nesting. */
fun GhostJsonReader.skipValue() {
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

/**
 * Decodes a JSON array into a [List] using [itemParser]. Enforces [maxCollectionSize] to defend
 * against heap-exhaustion attacks from a maliciously large array.
 */
inline fun <T> GhostJsonReader.readList(crossinline itemParser: () -> T): List<T> {
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
inline fun <T> GhostJsonReader.readSet(crossinline itemParser: () -> T): Set<T> {
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
inline fun <K, V> GhostJsonReader.readMap(
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
            depth--
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

/**
 * Runs [block]; on [GhostJsonException] rolls back parser state and skips the invalid value instead
 * of propagating, returning `null`.
 */
@InternalGhostApi
inline fun <T> GhostJsonReader.decodeResilient(
    crossinline block: () -> T
): T? {
    val savedPos = position
    val savedToken = nextTokenByte
    val savedDepth = depth
    val savedNeedsCommaMask = needsCommaMask
    val savedCommaConsumedMask = commaConsumedMask
    val savedPathMark = pathTracker.mark()
    val streaming = source as? StreamingGhostSource
    streaming?.pin(savedPos)
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
    } finally {
        streaming?.unpin()
    }
}

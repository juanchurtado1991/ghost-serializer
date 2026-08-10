@file:Suppress("NOTHING_TO_INLINE")
@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser.streaming

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.bytes.ghostReadLong8
import com.ghost.serialization.parser.bytes.ghostUseSwarScans
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
import com.ghost.serialization.parser.common.readListCore
import com.ghost.serialization.parser.common.readSetCore
import com.ghost.serialization.parser.common.scanStringImpl
import com.ghost.serialization.parser.common.selectValidateCommasCore
import com.ghost.serialization.parser.common.skipValueCore
import com.ghost.serialization.parser.common.GhostJsonConstants as C


/**
 * Starts parsing a JSON object.
 *
 * Concept and Safety:
 * 1. Verifies that the next non-whitespace byte is the opening brace `{` ([GhostJsonConstants.OPEN_OBJ_INT]).
 * 2. Increments the recursion tracking [GhostJsonReader.depth].
 * 3. Enforces the security limit [GhostJsonReader.maxDepth] to prevent nesting overflow StackOverflowErrors.
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
}

/**
 * Finishes parsing a JSON object.
 *
 * Concept:
 * 1. Verifies that the next non-whitespace byte is the closing brace `}` ([GhostJsonConstants.CLOSE_OBJ_INT]).
 * 2. Decrements the recursion tracking [GhostJsonReader.depth].
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
}

/**
 * Starts parsing a JSON array.
 *
 * Concept and Safety:
 * 1. Verifies that the next non-whitespace byte is the opening bracket `[` ([GhostJsonConstants.OPEN_ARR_INT]).
 * 2. Increments the recursion tracking [GhostJsonReader.depth].
 * 3. Enforces the security limit [GhostJsonReader.maxDepth] to prevent stack exhaustion from nested payloads.
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
}

/**
 * Finishes parsing a JSON array.
 *
 * Concept:
 * 1. Verifies that the next non-whitespace byte is the closing bracket `]` ([GhostJsonConstants.CLOSE_ARR_INT]).
 * 2. Decrements the recursion tracking [GhostJsonReader.depth].
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
}

/**
 * Determines whether the current JSON object or array has more elements to process.
 *
 * Mechanics:
 * 1. Peeks at the next token byte without consuming it.
 * 2. Returns `false` if it encounters a closing brace `}`, closing bracket `]`, or the end of input.
 * 3. Comma separator handling: if a comma `,` ([GhostJsonConstants.COMMA_INT]) is encountered, it skips it and peeks the following token.
 * 4. Rejection of trailing commas: if the character following a comma is a closing bracket `]` or closing brace `}`, it throws a trailing comma syntax exception.
 *
 * @return `true` if there are more elements/properties, `false` otherwise.
 * @throws GhostJsonException if a trailing comma is detected or input is invalid.
 */
fun GhostJsonReader.hasNext(): Boolean =
    hasNextCore(
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

/**
 * Consumes the optional separator comma and decodes the next JSON key name.
 *
 * Mechanics:
 * 1. Peeks the next token. If object closing `}` is encountered, returns `null` to signal completion.
 * 2. If a comma `,` is found, skips it and validates that it does not precede a closing `}` (no trailing commas).
 * 3. Decodes the quoted string representing the key name.
 *
 * @return The decoded string representing the key, or `null` if the object has ended.
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
    return readQuotedString()
}

/**
 * Consumes the key-value separator character `:` ([GhostJsonConstants.COLON_INT]) from the JSON stream.
 *
 * Advances the reader past the colon character.
 *
 * @throws GhostJsonException if the next non-whitespace character is not a colon `:`.
 */
fun GhostJsonReader.consumeKeySeparator() {
    consumeKeySeparatorCore(
        nextNonWhitespace = { nextNonWhitespace() },
        throwError = { throwError(it) },
    )
}

/**
 * Consumes the array item separator `,` ([GhostJsonConstants.COMMA_INT]) if it is next in the stream.
 *
 * Advances the cursor by 1 byte if the comma is matched.
 */
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
 * Parses and returns the next boolean value.
 *
 * Features:
 * 1. Checks literal values: consumes `true` ([GhostJsonConstants.TRUE_BS]) or `false` ([GhostJsonConstants.FALSE_BS]) bytes.
 * 2. Coercion: if [GhostJsonReader.coerceBooleans] is active, translates `1`/`0` integers or string equivalents
 *    (`"true"`, `"yes"`, `"on"`, `"1"`, `"y"` / `"false"`, `"no"`, `"off"`, `"0"`, `"n"`) into their corresponding boolean states.
 *
 * @return The parsed or coerced boolean value.
 * @throws GhostJsonException if the token is not a boolean or fails to be coerced.
 */
fun GhostJsonReader.nextBoolean(): Boolean =
    nextBooleanCore(
        peekNextToken = { peekNextToken() },
        skipAndValidateLiteral = { skipAndValidateLiteral(it) },
        coerceBooleans = coerceBooleans,
        internalSkip = { internalSkip(it) },
        matchCoerceBooleanBytes = { matchCoerceBooleanBytes() },
        throwError = { throwError(it) },
    )

/**
 * Decodes and returns the next JSON string value.
 *
 * Delegates to the zero-allocation string decoder, processing Unicode and control characters.
 *
 * @return The decoded string value.
 * @throws GhostJsonException if the next token is not a string.
 */
fun GhostJsonReader.nextString(): String = readQuotedString()

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
    return decoded[0]
}

/**
 * Peeks the stream to determine if the next value is a JSON `null`.
 *
 * Does not advance the reading position, useful for parsing optional/nullable fields.
 *
 * @return `true` if the next non-whitespace character is `n` (indicating `null`), `false` otherwise.
 */
fun GhostJsonReader.isNextNullValue(): Boolean =
    peekNextToken() == C.NULL_CHAR_INT

/**
 * Validates and consumes the JSON `null` literal bytes from the stream.
 *
 * Verifies that the next 4 bytes are exactly `n-u-l-l`.
 *
 * @throws GhostJsonException if the token sequence does not match `null`.
 */
fun GhostJsonReader.consumeNull() {
    if (isStreaming) {
        skipAndValidateLiteral(C.NULL_BS)
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

/**
 * Zero-copy boolean coercion matcher for [GhostJsonReader]. Delegates byte
 * comparison to [matchCoerceBooleanBytes] in GhostParserUtils — single source of truth.
 */
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
 * High-performance field identification using pre-calculated [JsonReaderOptions] perfect hash mappings.
 *
 * Optimization:
 * - Eliminates HashMap lookups and String instantiation overhead.
 * - Hashes raw bytes directly from the stream and maps them to a candidate field index using perfect hash O(1) math.
 * - Automatically verifies matches and consumes the following colon `:` separator to minimize parser steps.
 *
 * @param options Compile-time built Perfect Hash settings for the target class.
 * @return The 0-based field index, [GhostJsonConstants.MATCH_NONE] if unknown key, or `-1` if object ends.
 */
fun GhostJsonReader.selectNameAndConsume(options: JsonReaderOptions): Int =
    internalSelect(options, consumeSeparator = true)

/**
 * Matches a string token from the stream against the given [options].
 *
 * Unlike [selectNameAndConsume], this method does not consume the colon `:` separator, as it is
 * designed to match standard string options (e.g. enum values or type descriptors) instead of keys.
 *
 * @param options The choices to match against.
 * @return The index of the matched option, or [GhostJsonConstants.MATCH_NONE] if no match.
 */
fun GhostJsonReader.selectString(options: JsonReaderOptions): Int =
    internalSelect(options, consumeSeparator = false)

/**
 * Low-level select parser helper that hashes and matches against [JsonReaderOptions] fields.
 *
 * Mechanics:
 * 1. Checks for trailing comma conditions and finds the start of the quoted string/key.
 * 2. Scans for the closing quote. Performs direct buffer reads to avoid allocations.
 * 3. Applies the Perfect Hash mathematical formula using option multiplier/shift to find the candidate index.
 * 4. Verifies candidate correctness byte-by-byte using unrolled loop checks to guard against hash collisions.
 * 5. Consumes the trailing colon `:` if [consumeSeparator] is enabled.
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
                } else {
                    var matchedOffset = 0
                    if (ghostUseSwarScans) {
                        while (matchedOffset + C.LONG_BYTES <= candidateLength &&
                            ghostReadLong8(localData, start + matchedOffset) ==
                            ghostReadLong8(candidate, matchedOffset)
                        ) {
                            matchedOffset += C.LONG_BYTES
                        }
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
 * Verifies that the candidate key matched in the dispatch table corresponds exactly to the expected key bytes.
 *
 * Optimization:
 * - Compares bytes directly in blocks of 4 using loop unrolling for hardware efficiency.
 * - Prevents hash collision false-positives without allocating a String.
 * - If verified, consumes the key and advances the cursor, optionally consuming the colon separator `:`.
 *
 * @param start The absolute starting position of the candidate key bytes in the buffer.
 * @param length The length of the candidate key.
 * @param expected The pre-cached UTF-8 byte array of the expected field name constant.
 * @param consumeSeparator Whether to consume the colon `:` separator after verification.
 * @return `true` if bytes match exactly, `false` otherwise.
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
 * Peeks ahead in the JSON stream to look for a specific key's string value without advancing the reader cursor permanently.
 *
 * Primarily used to retrieve sealed class type discriminators (e.g. `"type"`) so that the proper subclass deserializer
 * can be dynamically selected before fully parsing the object.
 *
 * @param name The target key name to look for.
 * @return The string value of the key if found, or `null` otherwise.
 */
fun GhostJsonReader.peekStringField(name: String): String? {
    return peekDiscriminator(name)
}

/**
 * Skips the next complete JSON value (object, array, string, number, boolean, null) from the source.
 *
 * Properly balances nested opening/closing brackets and braces.
 * This is used to bypass unknown properties, maintaining reader alignment.
 */
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
 * Decodes a JSON array into a [List] of elements, utilizing the provided [itemParser] lambda.
 *
 * Mechanics and Safety:
 * - Inline function to eliminate call overhead and lambda allocations.
 * - Instantiates the list using [GhostHeuristics.initialCollectionCapacity] to optimize allocations.
 * - Enforces [maxCollectionSize] constraints to defend against heap exhaustion attacks.
 *
 * @param T The item type.
 * @param itemParser The parsing lambda to invoke for each array element.
 * @return A [List] containing the parsed items.
 */
inline fun <T> GhostJsonReader.readList(crossinline itemParser: () -> T): List<T> =
    readListCore(
        beginArray = { beginArray() },
        endArray = { endArray() },
        peekNextToken = { peekNextToken() },
        nextNonWhitespace = { nextNonWhitespace() },
        getDepth = { depth },
        setDepth = { depth = it },
        initialCapacity = initialCollectionCapacity,
        maxSize = maxCollectionSize,
        itemParser = { itemParser() },
        throwError = { throwError(it) },
    )

/**
 * Reads a JSON array into a [Set] without an intermediate [List] allocation.
 */
inline fun <T> GhostJsonReader.readSet(crossinline itemParser: () -> T): Set<T> =
    readSetCore(
        beginArray = { beginArray() },
        endArray = { endArray() },
        peekNextToken = { peekNextToken() },
        nextNonWhitespace = { nextNonWhitespace() },
        getDepth = { depth },
        setDepth = { depth = it },
        initialCapacity = initialCollectionCapacity,
        maxSize = maxCollectionSize,
        itemParser = { itemParser() },
        throwError = { throwError(it) },
    )

/**
 * Decodes a JSON object into a [Map] of key-value pairs, using the provided [keyParser] and [valueParser] lambdas.
 *
 * Mechanics and Safety:
 * - Inline function to eliminate function call and closure allocations.
 * - Allocates using [GhostHeuristics.initialCollectionCapacity] to optimize allocations.
 * - Enforces [maxCollectionSize] constraints.
 *
 * @param K The key type.
 * @param V The value type.
 * @param keyParser The parsing lambda for keys.
 * @param valueParser The parsing lambda for values.
 * @return A [Map] containing the parsed key-value pairs.
 */
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
 * Safely parses a block, returning `null` and skipping the JSON value if a [GhostJsonException] is encountered.
 *
 * Resiliency Mechanics:
 * - Saves current parser state (position, token cache).
 * - Attempts to execute [block].
 * - If [GhostJsonException] occurs, rolls back to saved state and skips the invalid value using [skipValue].
 *
 * @param T The expected parsed type.
 * @param block The parsing block to attempt.
 * @return The result of [block], or `null` if parsing fails.
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
        skipValue()
        return null
    } finally {
        streaming?.unpin()
    }
}

@file:Suppress("NOTHING_TO_INLINE")
@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser


import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.GhostHeuristics.initialCollectionCapacity
import com.ghost.serialization.parser.GhostJsonConstants
import com.ghost.serialization.parser.GhostJsonConstants as C

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
    if (nextNonWhitespace() != C.OPEN_OBJ_INT) {
        throwError(C.ERR_EXPECTED_BEGIN_OBJ)
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
 * Finishes parsing a JSON object.
 *
 * Concept:
 * 1. Verifies that the next non-whitespace byte is the closing brace `}` ([GhostJsonConstants.CLOSE_OBJ_INT]).
 * 2. Decrements the recursion tracking [GhostJsonReader.depth].
 *
 * @throws GhostJsonException if the token is not `}`.
 */
fun GhostJsonReader.endObject() {
    if (nextNonWhitespace() != C.CLOSE_OBJ_INT) {
        throwError(C.ERR_EXPECTED_END_OBJ)
    }
    if (depth > 0) {
        depth--
    }
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
 * Finishes parsing a JSON array.
 *
 * Concept:
 * 1. Verifies that the next non-whitespace byte is the closing bracket `]` ([GhostJsonConstants.CLOSE_ARR_INT]).
 * 2. Decrements the recursion tracking [GhostJsonReader.depth].
 *
 * @throws GhostJsonException if the token is not `]`.
 */
fun GhostJsonReader.endArray() {
    if (nextNonWhitespace() != C.CLOSE_ARR_INT) {
        throwError(C.ERR_EXPECTED_END_ARR)
    }
    if (depth > 0) {
        depth--
    }
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
fun GhostJsonReader.hasNext(): Boolean {
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
 * Consumes the key-value separator character `:` ([GhostJsonConstants.COLON_INT]) from the JSON stream.
 *
 * Advances the reader past the colon character.
 *
 * @throws GhostJsonException if the next non-whitespace character is not a colon `:`.
 */
fun GhostJsonReader.consumeKeySeparator() {
    if (nextNonWhitespace() != C.COLON_INT) {
        throwError(C.ERR_EXPECTED_COLON)
    }
}

/**
 * Consumes the array item separator `,` ([GhostJsonConstants.COMMA_INT]) if it is next in the stream.
 *
 * Advances the cursor by 1 byte if the comma is matched.
 */
fun GhostJsonReader.consumeArraySeparator() {
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
fun GhostJsonReader.nextBoolean(): Boolean {
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
            // Zero-copy: scan the quoted string bytes directly. No String allocation.
            return matchCoerceBooleanBytes()
        }
    }
    throwError(C.ERR_EXPECTED_BOOLEAN)
}

/**
 * Decodes and returns the next JSON string value.
 *
 * Delegates to the zero-allocation string decoder, processing Unicode and control characters.
 *
 * @return The decoded string value.
 * @throws GhostJsonException if the next token is not a string.
 */
fun GhostJsonReader.nextString(): String = readQuotedString()

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
    skipAndValidateLiteral(C.NULL_BS)
}

/**
 * Zero-copy boolean coercion matcher for [GhostJsonReader]. Delegates byte
 * comparison to [matchCoerceBooleanBytes] in GhostParserUtils — single source of truth.
 */
private fun GhostJsonReader.matchCoerceBooleanBytes(): Boolean {
    val lim = limit
    val contentStart = position + 1 // skip opening '"'
    val end = if (isStreaming) {
        source.findClosingQuote(contentStart, lim)
    } else {
        findClosingQuoteImpl(contentStart, lim) { getByte(it) }
    }
    if (end == -1) throwError(C.UNTERMINATED_STRING_ERROR)
    val length = end - contentStart
    position = end + 1
    nextTokenByte = C.RESET_TOKEN_BYTE
    return matchCoerceBooleanBytes(
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
    val end = if (isStreaming) {
        source.findClosingQuote(start, limit)
    } else {
        val localData = rawData
        findClosingQuoteImpl(start, limit) {
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
            return index
        }
    }

    return handleSelectNoMatch(start, end, length, consumeSeparator)
}

private fun GhostJsonReader.selectValidateCommas(token: Int, consumeSeparator: Boolean): Int {
    var currentToken = token
    if (strictMode && depth < C.MAX_BITMASK_DEPTH) {
        val bit = C.BITMASK_UNIT shl depth
        if ((commaConsumedMask and bit) != C.RESULT_NONE) {
            commaConsumedMask = commaConsumedMask and bit.inv()
            needsCommaMask = needsCommaMask or bit
        } else {
            val required = (needsCommaMask and bit) != C.RESULT_NONE
            if (currentToken == C.COMMA_INT) {
                if (!required) {
                    throwError(C.ERR_UNEXPECTED_COMMA)
                }
                internalSkip(1)
                currentToken = peekNextToken()
                if (currentToken == C.CLOSE_OBJ_INT) {
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
        if (currentToken == C.COMMA_INT) {
            internalSkip(1)
            currentToken = peekNextToken()
            if (currentToken == C.CLOSE_OBJ_INT) {
                throwError(C.ERR_TRAILING_COMMA)
            }
        }
    }
    return currentToken
}

private fun GhostJsonReader.handleSelectNoMatch(
    start: Int,
    end: Int,
    length: Int,
    consumeSeparator: Boolean
): Int {
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

private fun GhostJsonReader.throwExpectedKeyOrStringError(consumeSeparator: Boolean) {
    throwError(if (consumeSeparator) C.ERR_EXPECTED_KEY else C.ERR_EXPECTED_STRING)
}

private fun GhostJsonReader.throwUnterminatedStringError() {
    throwError(C.UNTERMINATED_STRING_ERROR)
}

/**
 * Computes a fast, collision-reducing 32-bit hash value from a raw slice of the JSON buffer.
 *
 * Optimization:
 * - Packs the first 4 bytes directly into a single Int value using bitwise shifts and OR operations.
 * - This avoids allocating any temporary byte arrays or strings, allowing hardware-level key hashing.
 *
 * @param start The absolute 0-based byte position in the buffer.
 * @param length The length of the key.
 * @return The packed 32-bit hash key.
 */
private fun GhostJsonReader.computeKeyHash(start: Int, length: Int, hasCollisions: Boolean): Int {
    var key = 0
    if (length >= 4) {
        val byte0 = getByte(start)
        val byte1 = getByte(start + 1)
        val byte2 = getByte(start + 2)
        val byte3 = getByte(start + 3)
        key = byte0 or
                (byte1 shl C.SHIFT_8) or
                (byte2 shl C.SHIFT_16) or
                (byte3 shl C.SHIFT_24)
        if (hasCollisions) {
            key = JsonReaderOptions.collisionXor(
                key,
                getByte(start + length - 1),
                getByte(start + (length shr C.SINGLE_CHAR_SIZE))
            )
        }
    } else {
        if (length >= 1) key = key or getByte(start)
        if (length >= 2) key = key or (getByte(start + 1) shl C.SHIFT_8)
        if (length >= 3) key = key or (getByte(start + 2) shl C.SHIFT_16)
    }
    return key
}

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
        var i = 0
        if (!isStreaming) {
            val localData = rawData
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
        } else {
            val localSource = source
            while (i + 3 < length) {
                if (localSource[start + i].toByte() != expected[i]) return false
                if (localSource[start + i + 1].toByte() != expected[i + 1]) return false
                if (localSource[start + i + 2].toByte() != expected[i + 2]) return false
                if (localSource[start + i + 3].toByte() != expected[i + 3]) return false
                i += 4
            }
            while (i < length) {
                if (localSource[start + i].toByte() != expected[i]) return false
                i++
            }
        }
        val endPos = start + length
        val newPos = endPos + 1
        position = newPos
        nextTokenByte = -1
        if (consumeSeparator) {
            val lim = limit
            if (newPos < lim) {
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
inline fun <T> GhostJsonReader.readList(crossinline itemParser: () -> T): List<T> {
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
            depth--
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

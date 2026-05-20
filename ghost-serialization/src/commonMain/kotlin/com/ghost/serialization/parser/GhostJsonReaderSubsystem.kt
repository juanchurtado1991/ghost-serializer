@file:Suppress("NOTHING_TO_INLINE")
@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.GhostHeuristics.initialCollectionCapacity
import com.ghost.serialization.parser.GhostJsonConstants as C
import okio.ByteString

/**
 * Starts a new JSON object.
 * Increments [depth] and validates against [maxDepth].
 * Used by KSP-generated serializers.
 */
fun GhostJsonReader.beginObject() {
    if (nextNonWhitespace() != C.OPEN_OBJ_INT) {
        throwError(C.ERR_EXPECTED_BEGIN_OBJ)
    }
    depth++
    if (depth > maxDepth) {
        throwError(C.ERR_DEPTH_EXCEEDED)
    }
}

/**
 * Ends the current JSON object.
 * Decrements [depth].
 * Used by KSP-generated serializers.
 */
fun GhostJsonReader.endObject() {
    if (nextNonWhitespace() != C.CLOSE_OBJ_INT) {
        throwError(C.ERR_EXPECTED_END_OBJ)
    }
    depth--
}

/**
 * Starts a new JSON array.
 * Increments [depth] and validates against [maxDepth].
 * Used by KSP-generated serializers.
 */
fun GhostJsonReader.beginArray() {
    if (nextNonWhitespace() != C.OPEN_ARR_INT) {
        throwError(C.ERR_EXPECTED_BEGIN_ARR)
    }
    depth++
    if (depth > maxDepth) {
        throwError(C.ERR_DEPTH_EXCEEDED)
    }
}

/**
 * Ends the current JSON array.
 * Decrements [depth].
 * Used by KSP-generated serializers.
 */
fun GhostJsonReader.endArray() {
    if (nextNonWhitespace() != C.CLOSE_ARR_INT) {
        throwError(C.ERR_EXPECTED_END_ARR)
    }
    depth--
}

/**
 * Returns true if the current object or array has more elements.
 * Automatically handles comma consumption.
 * Used by KSP-generated serializers.
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
    if (token == C.COMMA_INT) {
        internalSkip(1)
        val next = peekNextToken()
        if (next == C.CLOSE_ARR_INT || next == C.CLOSE_OBJ_INT) {
            throwError(C.ERR_TRAILING_COMMA)
        }
    }
    return true
}

/**
 * Consumes the optional comma separator and returns the next object key. Returns null if the object ends.
 */
fun GhostJsonReader.nextKey(): String? {
    val token = peekNextToken()
    if (token == C.CLOSE_OBJ_INT) {
        return null
    }
    if (token == C.COMMA_INT) {
        internalSkip(1)
        if (peekNextToken() == C.CLOSE_OBJ_INT) {
            throwError(C.ERR_TRAILING_COMMA)
        }
    }
    return readQuotedString()
}

/**
 * Consumes the key-value separator character (':').
 */
fun GhostJsonReader.consumeKeySeparator() {
    if (nextNonWhitespace() != C.COLON_INT) {
        throwError(C.ERR_EXPECTED_COLON)
    }
}

/**
 * Consumes the array item separator (',') if it is present next in the stream.
 */
fun GhostJsonReader.consumeArraySeparator() {
    if (peekNextToken() == C.COMMA_INT) {
        internalSkip(1)
    }
}

/**
 * Reads the next boolean value.
 * Supports string coercion if [coerceBooleans] is true.
 * Used by KSP-generated serializers.
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
            val s = readQuotedString().lowercase()
            return when (s) {
                C.COERCE_TRUE_STR,
                C.COERCE_YES_STR,
                C.COERCE_ON_STR,
                C.COERCE_1_STR,
                C.COERCE_Y_STR -> true

                C.COERCE_FALSE_STR,
                C.COERCE_NO_STR,
                C.COERCE_OFF_STR,
                C.COERCE_0_STR,
                C.COERCE_N_STR -> false

                else -> throwError("${C.ERR_EXPECTED_BOOLEAN} \"$s\"")
            }
        }
    }
    throwError(C.ERR_EXPECTED_BOOLEAN)
}

/**
 * Parses and returns the next JSON string token.
 */
fun GhostJsonReader.nextString(): String = readQuotedString()

/**
 * Returns true if the next value is null.
 * Used by KSP-generated serializers for nullable properties.
 */
fun GhostJsonReader.isNextNullValue(): Boolean =
    peekNextToken() == C.NULL_CHAR_INT

/**
 * Consumes the null literal from the source.
 * Used by KSP-generated serializers.
 */
fun GhostJsonReader.consumeNull() {
    skipAndValidateLiteral(C.NULL_BS)
}

/**
 * High-performance field identification using pre-calculated [JsonReaderOptions].
 * This is the heart of the generated deserializers, using a 4-byte hash to avoid string comparisons.
 * Returns the index of the field in [options] strings, or [GhostJsonConstants.MATCH_NONE].
 */
fun GhostJsonReader.selectNameAndConsume(options: JsonReaderOptions): Int =
    internalSelect(options, consumeSeparator = true)

/**
 * Match a string token from the stream against the given [options].
 */
fun GhostJsonReader.selectString(options: JsonReaderOptions): Int =
    internalSelect(options, consumeSeparator = false)

/**
 * Low-level select parser helper that hashes and matches against [JsonReaderOptions] fields.
 */
private fun GhostJsonReader.internalSelect(
    options: JsonReaderOptions,
    consumeSeparator: Boolean
): Int {
    var token = peekNextToken()
    if (token == C.CLOSE_OBJ_INT) {
        return -1
    }
    if (token == C.COMMA_INT) {
        internalSkip(1)
        token = peekNextToken()
        if (token == C.CLOSE_OBJ_INT) {
            throwError(C.ERR_TRAILING_COMMA)
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
    val end = source.findClosingQuote(start, limit)
    if (end == -1) {
        throwError(C.UNTERMINATED_STRING_ERROR)
    }

    val length = end - start
    val key = computeKeyHash(start, length)
    val hasIndex = ((key * options.multiplier + length) shr options.shift) and C.HASH_MASK
    val index = options.dispatch[hasIndex]

    if (index != C.MATCH_END) {
        if (verifyKeyMatch(start, length, options.rawBytes[index], consumeSeparator)) {
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
 * Computes a fast 32-bit hash on the given string byte range.
 */
private fun GhostJsonReader.computeKeyHash(start: Int, length: Int): Int {
    var key = 0
    if (length >= 4) {
        val b0 = getByte(start)
        val b1 = getByte(start + 1)
        val b2 = getByte(start + 2)
        val b3 = getByte(start + 3)
        key = b0 or (b1 shl C.SHIFT_8) or (b2 shl C.SHIFT_16) or (b3 shl C.SHIFT_24)
    } else {
        if (length >= 1) {
            key = key or getByte(start)
        }
        if (length >= 2) {
            key = key or (getByte(start + 1) shl C.SHIFT_8)
        }
        if (length >= 3) {
            key = key or (getByte(start + 2) shl C.SHIFT_16)
        }
    }
    return key
}

/**
 * Verifies that the matched hash matches the expected [ByteString] value from options list.
 */
private fun GhostJsonReader.verifyKeyMatch(
    start: Int,
    length: Int,
    expected: ByteArray,
    consumeSeparator: Boolean
): Boolean {
    if (expected.size == length) {
        var i = 0
        while (i + 3 < length) {
            if (getByte(start + i) != expected[i].toInt() and C.BYTE_MASK) return false
            if (getByte(start + i + 1) != expected[i + 1].toInt() and C.BYTE_MASK) return false
            if (getByte(start + i + 2) != expected[i + 2].toInt() and C.BYTE_MASK) return false
            if (getByte(start + i + 3) != expected[i + 3].toInt() and C.BYTE_MASK) return false
            i += 4
        }
        while (i < length) {
            if (getByte(start + i) != expected[i].toInt() and C.BYTE_MASK) return false
            i++
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
 * Searches for a specific key in the current object without fully consuming it.
 * Used for sealed class discriminators.
 * Highly optimized to avoid unnecessary allocations.
 */
fun GhostJsonReader.peekStringField(name: String): String? {
    return peekDiscriminator(name)
}

/**
 * Skips the next complete JSON value (object, array, string, number, boolean, null) from the source.
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
 * Decodes a JSON array into a [List] of elements using [itemParser].
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
 * Decodes a JSON object into a [Map] of key-value pairs using [keyParser] and [valueParser].
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
        if (map.size > maxSize) {
            throwError("${C.ERR_MAX_COLLECTION_SIZE} ($maxSize)")
        }
    }
    return map
}

/**
 * Resiliently parses a block. If a parsing error occurs, recovers by skipping the value and returning null.
 */
@InternalGhostApi
inline fun <T> GhostJsonReader.decodeResilient(crossinline block: () -> T): T? {
    val savedPos = position
    val savedToken = nextTokenByte
    try {
        return block()
    } catch (_: GhostJsonException) {
        position = savedPos
        nextTokenByte = savedToken
        skipValue()
        return null
    }
}

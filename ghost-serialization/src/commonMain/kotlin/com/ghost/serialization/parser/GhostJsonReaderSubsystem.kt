@file:Suppress("NOTHING_TO_INLINE")
@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.GhostJsonConstants.CLOSE_ARR_INT
import com.ghost.serialization.parser.GhostJsonConstants.CLOSE_OBJ_INT
import com.ghost.serialization.parser.GhostJsonConstants.COLON_INT
import com.ghost.serialization.parser.GhostJsonConstants.COMMA_INT
import com.ghost.serialization.parser.GhostJsonConstants.OPEN_ARR_INT
import com.ghost.serialization.parser.GhostJsonConstants.OPEN_OBJ_INT
import com.ghost.serialization.parser.GhostJsonConstants.QUOTE_INT


/**
 * Signals the start of a JSON object '{'.
 * The compiler calls this at the beginning of each class deserialization.
 */
fun GhostJsonReader.beginObject() {
    if (nextNonWhitespace() != OPEN_OBJ_INT) throwError(GhostJsonConstants.ERR_EXPECTED_BEGIN_OBJ)
    depth++
    if (depth > maxDepth) throwError(GhostJsonConstants.ERR_DEPTH_EXCEEDED)
}


fun GhostJsonReader.endObject() {
    if (nextNonWhitespace() != CLOSE_OBJ_INT) throwError(GhostJsonConstants.ERR_EXPECTED_END_OBJ)
    depth--
}


fun GhostJsonReader.beginArray() {
    if (nextNonWhitespace() != OPEN_ARR_INT) throwError(GhostJsonConstants.ERR_EXPECTED_BEGIN_ARR)
    depth++
    if (depth > maxDepth) throwError(GhostJsonConstants.ERR_DEPTH_EXCEEDED)
}


fun GhostJsonReader.endArray() {
    if (nextNonWhitespace() != CLOSE_ARR_INT) throwError(GhostJsonConstants.ERR_EXPECTED_END_ARR)
    depth--
}


fun GhostJsonReader.hasNext(): Boolean {
    val token = peekNextToken()
    if (token == CLOSE_ARR_INT || token == CLOSE_OBJ_INT || token == GhostJsonConstants.MATCH_END) return false
    if (token == COMMA_INT) {
        internalSkip(1)
        val next = peekNextToken()
        if (next == CLOSE_ARR_INT || next == CLOSE_OBJ_INT) {
            throwError(GhostJsonConstants.ERR_TRAILING_COMMA)
        }
    }
    return true
}


fun GhostJsonReader.nextKey(): String? {
    val token = peekNextToken()
    if (token == CLOSE_OBJ_INT) return null
    if (token == COMMA_INT) {
        internalSkip(1)
        if (peekNextToken() == CLOSE_OBJ_INT) throwError(GhostJsonConstants.ERR_TRAILING_COMMA)
    }
    return readQuotedString()
}


fun GhostJsonReader.consumeKeySeparator() {
    if (nextNonWhitespace() != COLON_INT) throwError(GhostJsonConstants.ERR_EXPECTED_COLON)
}


fun GhostJsonReader.consumeArraySeparator() {
    if (peekNextToken() == COMMA_INT) {
        internalSkip(1)
    }
}


fun GhostJsonReader.nextBoolean(): Boolean {
    val token = peekNextToken()
    return when (token) {
        GhostJsonConstants.TRUE_CHAR_INT -> {
            skipAndValidateLiteral(GhostJsonConstants.TRUE_BS)
            true
        }

        GhostJsonConstants.FALSE_CHAR_INT -> {
            skipAndValidateLiteral(GhostJsonConstants.FALSE_BS)
            false
        }

        else -> throwError("${GhostJsonConstants.ERR_EXPECTED_BOOLEAN}${token.toChar()}")
    }
}


fun GhostJsonReader.nextString(): String = readQuotedString()


fun GhostJsonReader.isNextNullValue(): Boolean =
    peekNextToken() == GhostJsonConstants.NULL_CHAR_INT


fun GhostJsonReader.consumeNull() {
    skipAndValidateLiteral(GhostJsonConstants.NULL_BS)
}


/**
 * High-performance field identification using pre-calculated [JsonReaderOptions].
 * This is the heart of the generated deserializers, using a 4-byte hash to avoid string comparisons.
 * Returns the index of the field in [options.strings], or [GhostJsonConstants.MATCH_NONE].
 */
fun GhostJsonReader.selectNameAndConsume(options: JsonReaderOptions): Int {
    if (peekNextToken() == CLOSE_OBJ_INT) return -1
    if (peekNextToken() == COMMA_INT) {
        internalSkip(1)
        if (peekNextToken() == CLOSE_OBJ_INT) throwError(GhostJsonConstants.ERR_TRAILING_COMMA)
    }

    if (peekNextToken() != QUOTE_INT) throwError("${GhostJsonConstants.ERR_EXPECTED_KEY}${nextTokenByte.toChar()}")

    val start = position + 1
    val end = source.findClosingQuote(start, limit)
    if (end == -1) throwError(GhostJsonConstants.ERR_UNTERMINATED_KEY)

    val len = end - start

    // Multi-byte hashing implementation
    var key = 0
    if (len >= 1) key = key or (source[start] and GhostJsonConstants.BYTE_MASK)
    if (len >= 2) key =
        key or ((source[start + 1] and GhostJsonConstants.BYTE_MASK) shl GhostJsonConstants.SHIFT_8)
    if (len >= 3) key =
        key or ((source[start + 2] and GhostJsonConstants.BYTE_MASK) shl GhostJsonConstants.SHIFT_16)
    if (len >= 4) key =
        key or ((source[start + 3] and GhostJsonConstants.BYTE_MASK) shl GhostJsonConstants.SHIFT_24)

    val h = ((key * options.multiplier + len) shr options.shift) and GhostJsonConstants.HASH_MASK
    val index = options.dispatch[h]

    if (index != GhostJsonConstants.MATCH_END) {
        val expected = options.rawInts[index]
        if (expected.size == len) {
            var match = true
            for (i in 0 until len) {
                if (source[start + i] != expected[i]) {
                    match = false
                    break
                }
            }
            if (match) {
                position = end + 1
                nextTokenByte = -1
                consumeKeySeparator()
                return index
            }
        }
    }

    // No match found: skip the key and consume the separator
    position = end + 1
    nextTokenByte = GhostJsonConstants.MATCH_END
    consumeKeySeparator()
    return GhostJsonConstants.MATCH_NONE
}


fun GhostJsonReader.selectString(options: JsonReaderOptions): Int {
    // Handle end of object
    val tok = peekNextToken()
    if (tok == CLOSE_OBJ_INT) return -1
    // Skip leading comma between fields
    if (tok == COMMA_INT) {
        internalSkip(1)
        if (peekNextToken() == CLOSE_OBJ_INT) throwError(GhostJsonConstants.ERR_TRAILING_COMMA)
    }

    if (peekNextToken() != QUOTE_INT) throwError(GhostJsonConstants.ERR_EXPECTED_STRING)

    val start = position + 1
    val end = source.findClosingQuote(start, limit)
    if (end == -1) throwError(GhostJsonConstants.UNTERMINATED_STRING_ERROR)

    val len = end - start

    var key = 0
    if (len >= 1) key = key or (source[start] and GhostJsonConstants.BYTE_MASK)
    if (len >= 2) key =
        key or ((source[start + 1] and GhostJsonConstants.BYTE_MASK) shl GhostJsonConstants.SHIFT_8)
    if (len >= 3) key =
        key or ((source[start + 2] and GhostJsonConstants.BYTE_MASK) shl GhostJsonConstants.SHIFT_16)
    if (len >= 4) key =
        key or ((source[start + 3] and GhostJsonConstants.BYTE_MASK) shl GhostJsonConstants.SHIFT_24)

    val h = ((key * options.multiplier + len) shr options.shift) and GhostJsonConstants.HASH_MASK
    val index = options.dispatch[h]

    if (index != GhostJsonConstants.MATCH_END) {
        val expected = options.rawInts[index]
        if (expected.size == len) {
            var match = true
            for (i in 0 until len) {
                if (source[start + i] != expected[i]) {
                    match = false
                    break
                }
            }
            if (match) {
                position = end + 1
                nextTokenByte = -1
                return index
            }
        }
    }

    // Unknown field — advance past the closing quote and return -2
    position = end + 1
    nextTokenByte = GhostJsonConstants.MATCH_END
    if (strictMode) {
        val unknownKey = source.decodeToString(start, end)
        throwError("${GhostJsonConstants.STRICT_MODE_UNKNOWN_FIELD}$unknownKey")
    }
    return GhostJsonConstants.MATCH_NONE
}

/**
 * Searches for a specific key in the current object without fully consuming it.
 * Used for sealed class discriminators.
 * Highly optimized to avoid unnecessary allocations.
 */
fun GhostJsonReader.peekStringField(name: String): String? {
    val savedPos = position
    val savedToken = nextTokenByte

    try {
        if (peekNextToken() != OPEN_OBJ_INT) return null
        internalSkip(1)

        while (hasNext()) {
            val key = nextKey() ?: break
            consumeKeySeparator()
            if (key == name) {
                return if (peekNextToken() == QUOTE_INT) readQuotedString() else null
            }
            skipValue()
        }
    } catch (_: Exception) {
        // Silently fail and restore
    } finally {
        position = savedPos
        nextTokenByte = savedToken
    }
    return null
}

fun GhostJsonReader.skipValue() {
    val token = peekNextToken()
    when (token) {
        OPEN_OBJ_INT -> {
            beginObject()
            while (hasNext()) {
                nextKey().ignore()
                consumeKeySeparator()
                skipValue()
            }
            endObject()
        }

        OPEN_ARR_INT -> {
            beginArray()
            while (hasNext()) {
                skipValue()
            }
            endArray()
        }

        QUOTE_INT -> skipQuotedString()

        GhostJsonConstants.TRUE_CHAR_INT ->
            skipAndValidateLiteral(GhostJsonConstants.TRUE_BS)

        GhostJsonConstants.FALSE_CHAR_INT ->
            skipAndValidateLiteral(GhostJsonConstants.FALSE_BS)

        GhostJsonConstants.NULL_CHAR_INT ->
            skipAndValidateLiteral(GhostJsonConstants.NULL_BS)

        else -> {
            // Strictly validate and consume the number
            nextDouble().ignore()
        }
    }
}

fun GhostJsonReader.checkCollectionSize(size: Int) {
    if (size > maxCollectionSize) {
        throwError("${GhostJsonConstants.ERR_MAX_COLLECTION_SIZE} ($maxCollectionSize)")
    }
}

inline fun <T> GhostJsonReader.readList(itemParser: () -> T): List<T> {
    beginArray()
    if (peekNextToken() == CLOSE_ARR_INT) {
        endArray()
        return emptyList()
    }
    val list = ArrayList<T>(GhostHeuristics.initialCollectionCapacity)
    while (true) {
        list.add(itemParser())
        val next = peekNextToken()
        if (next == CLOSE_ARR_INT) {
            endArray()
            break
        }
        if (next != COMMA_INT) {
            throwError("${GhostJsonConstants.ERR_EXPECTED_COMMA_OR_CLOSE_ARR} but found ${next.toChar()}")
        }
        internalSkip(1)
        checkCollectionSize(list.size)
    }
    return list
}

inline fun <K, V> GhostJsonReader.readMap(
    keyParser: () -> K,
    valueParser: () -> V
): Map<K, V> {
    beginObject()
    if (peekNextToken() == CLOSE_OBJ_INT) {
        endObject()
        return emptyMap()
    }
    val map = HashMap<K, V>(GhostHeuristics.initialCollectionCapacity)
    while (true) {
        val key = keyParser()
        consumeKeySeparator()
        val value = valueParser()
        map[key] = value
        val next = peekNextToken()
        if (next == CLOSE_OBJ_INT) {
            endObject()
            break
        }
        if (next != COMMA_INT) {
            throwError("${GhostJsonConstants.ERR_EXPECTED_COMMA_OR_CLOSE_OBJ} but found ${next.toChar()}")
        }
        internalSkip(1)
        checkCollectionSize(map.size)
    }
    return map
}

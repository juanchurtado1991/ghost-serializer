@file:Suppress("NOTHING_TO_INLINE")
@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.GhostHeuristics.initialCollectionCapacity
import com.ghost.serialization.parser.GhostJsonConstants.BYTE_MASK
import com.ghost.serialization.parser.GhostJsonConstants.CLOSE_ARR_INT
import com.ghost.serialization.parser.GhostJsonConstants.CLOSE_OBJ_INT
import com.ghost.serialization.parser.GhostJsonConstants.COLON_INT
import com.ghost.serialization.parser.GhostJsonConstants.COMMA_INT
import com.ghost.serialization.parser.GhostJsonConstants.ERR_DEPTH_EXCEEDED
import com.ghost.serialization.parser.GhostJsonConstants.ERR_EXPECTED_BEGIN_ARR
import com.ghost.serialization.parser.GhostJsonConstants.ERR_EXPECTED_BEGIN_OBJ
import com.ghost.serialization.parser.GhostJsonConstants.ERR_EXPECTED_BOOLEAN
import com.ghost.serialization.parser.GhostJsonConstants.ERR_EXPECTED_COLON
import com.ghost.serialization.parser.GhostJsonConstants.ERR_EXPECTED_COMMA_OR_CLOSE_ARR
import com.ghost.serialization.parser.GhostJsonConstants.ERR_EXPECTED_COMMA_OR_CLOSE_OBJ
import com.ghost.serialization.parser.GhostJsonConstants.ERR_EXPECTED_END_ARR
import com.ghost.serialization.parser.GhostJsonConstants.ERR_EXPECTED_END_OBJ
import com.ghost.serialization.parser.GhostJsonConstants.ERR_EXPECTED_KEY
import com.ghost.serialization.parser.GhostJsonConstants.ERR_EXPECTED_STRING
import com.ghost.serialization.parser.GhostJsonConstants.ERR_MAX_COLLECTION_SIZE
import com.ghost.serialization.parser.GhostJsonConstants.ERR_TRAILING_COMMA
import com.ghost.serialization.parser.GhostJsonConstants.FALSE_BS
import com.ghost.serialization.parser.GhostJsonConstants.FALSE_CHAR_INT
import com.ghost.serialization.parser.GhostJsonConstants.HASH_MASK
import com.ghost.serialization.parser.GhostJsonConstants.MATCH_END
import com.ghost.serialization.parser.GhostJsonConstants.MATCH_NONE
import com.ghost.serialization.parser.GhostJsonConstants.NULL_BS
import com.ghost.serialization.parser.GhostJsonConstants.NULL_CHAR_INT
import com.ghost.serialization.parser.GhostJsonConstants.ONE_INT
import com.ghost.serialization.parser.GhostJsonConstants.OPEN_ARR_INT
import com.ghost.serialization.parser.GhostJsonConstants.OPEN_OBJ_INT
import com.ghost.serialization.parser.GhostJsonConstants.QUOTE_INT
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_16
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_24
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_8
import com.ghost.serialization.parser.GhostJsonConstants.STRICT_MODE_UNKNOWN_FIELD
import com.ghost.serialization.parser.GhostJsonConstants.TRUE_BS
import com.ghost.serialization.parser.GhostJsonConstants.TRUE_CHAR_INT
import com.ghost.serialization.parser.GhostJsonConstants.UNTERMINATED_STRING_ERROR
import com.ghost.serialization.parser.GhostJsonConstants.ZERO_INT

fun GhostJsonReader.beginObject() {
    if (nextNonWhitespace() != OPEN_OBJ_INT) throwError(ERR_EXPECTED_BEGIN_OBJ)
    if (++depth > maxDepth) throwError(ERR_DEPTH_EXCEEDED)
}

fun GhostJsonReader.endObject() {
    if (nextNonWhitespace() != CLOSE_OBJ_INT) throwError(ERR_EXPECTED_END_OBJ)
    depth--
}

fun GhostJsonReader.beginArray() {
    if (nextNonWhitespace() != OPEN_ARR_INT) throwError(ERR_EXPECTED_BEGIN_ARR)
    if (++depth > maxDepth) throwError(ERR_DEPTH_EXCEEDED)
}

fun GhostJsonReader.endArray() {
    if (nextNonWhitespace() != CLOSE_ARR_INT) throwError(ERR_EXPECTED_END_ARR)
    depth--
}

fun GhostJsonReader.hasNext(): Boolean {
    val token = peekNextToken()
    if (
        token == CLOSE_ARR_INT ||
        token == CLOSE_OBJ_INT ||
        token == MATCH_END
    ) {
        return false
    }
    if (token == COMMA_INT) {
        internalSkip(1)
        val next = peekNextToken()
        if (next == CLOSE_ARR_INT || next == CLOSE_OBJ_INT) {
            throwError(ERR_TRAILING_COMMA)
        }
    }
    return true
}

fun GhostJsonReader.nextKey(): String? {
    val token = peekNextToken()
    if (token == CLOSE_OBJ_INT) return null
    if (token == COMMA_INT) {
        internalSkip(1)
        if (peekNextToken() == CLOSE_OBJ_INT) {
            throwError(ERR_TRAILING_COMMA)
        }
    }
    return readQuotedString()
}

fun GhostJsonReader.consumeKeySeparator() {
    if (nextNonWhitespace() != COLON_INT) {
        throwError(ERR_EXPECTED_COLON)
    }
}

fun GhostJsonReader.consumeArraySeparator() {
    if (peekNextToken() == COMMA_INT) internalSkip(1)
}

fun GhostJsonReader.nextBoolean(): Boolean {
    val token = peekNextToken()
    if (token == TRUE_CHAR_INT) {
        skipAndValidateLiteral(TRUE_BS)
        return true
    }
    if (token == FALSE_CHAR_INT) {
        skipAndValidateLiteral(FALSE_BS)
        return false
    }
    if (coerceBooleans) {
        if (token == ONE_INT) {
            internalSkip(1)
            return true
        }
        if (token == ZERO_INT) {
            internalSkip(1)
            return false
        }
        if (token == QUOTE_INT) {
            val s = readQuotedString().lowercase()
            return when (s) {
                "true", "yes", "on", "1", "y" -> true
                "false", "no", "off", "0", "n" -> false
                else -> throwError(ERR_EXPECTED_BOOLEAN + " \"$s\"")
            }
        }
    }
    throwError(ERR_EXPECTED_BOOLEAN)
}

fun GhostJsonReader.nextString(): String = readQuotedString()

fun GhostJsonReader.isNextNullValue(): Boolean =
    peekNextToken() == NULL_CHAR_INT

fun GhostJsonReader.consumeNull() {
    skipAndValidateLiteral(NULL_BS)
}


/**
 * High-performance field identification using pre-calculated [JsonReaderOptions].
 * This is the heart of the generated deserializers, using a 4-byte hash to avoid string comparisons.
 * Returns the index of the field in [options] strings, or [GhostJsonConstants.MATCH_NONE].
 */
fun GhostJsonReader.selectNameAndConsume(options: JsonReaderOptions): Int =
    internalSelect(options, consumeSeparator = true)

fun GhostJsonReader.selectString(options: JsonReaderOptions): Int =
    internalSelect(options, consumeSeparator = false)

private fun GhostJsonReader.internalSelect(
    options: JsonReaderOptions,
    consumeSeparator: Boolean
): Int {
    var token = peekNextToken()
    if (token == CLOSE_OBJ_INT) return -1
    if (token == COMMA_INT) {
        internalSkip(1)
        token = peekNextToken()
        if (token == CLOSE_OBJ_INT) {
            throwError(ERR_TRAILING_COMMA)
        }
    }

    if (token != QUOTE_INT) {
        throwError(if (consumeSeparator) ERR_EXPECTED_KEY else ERR_EXPECTED_STRING)
    }

    val start = position + 1
    val end = source.findClosingQuote(start, limit)
    if (end == -1) throwError(UNTERMINATED_STRING_ERROR)

    val length = end - start
    val key = computeKeyHash(start, length)
    val hasIndex = ((key * options.multiplier + length) shr options.shift) and HASH_MASK
    val index = options.dispatch[hasIndex]

    if (index != MATCH_END) {
        if (verifyKeyMatch(start, length, options.rawBytes[index], consumeSeparator)) {
            return index
        }
    }

    // No match found
    position = end + 1
    nextTokenByte = MATCH_END
    if (consumeSeparator) {
        if (position < limit && getByte(position) == COLON_INT) {
            position++
        } else {
            consumeKeySeparator()
        }
    } else if (strictMode) {
        val unknownKey = source.decodeToString(start, end)
        throwError("${STRICT_MODE_UNKNOWN_FIELD}$unknownKey")
    }

    return MATCH_NONE
}

@Suppress("NOTHING_TO_INLINE")
private inline fun GhostJsonReader.computeKeyHash(start: Int, length: Int): Int {
    var key = 0
    if (isStreaming) {
        if (length >= 4) {
            val b0 = source[start]
            val b1 = source[start + 1]
            val b2 = source[start + 2]
            val b3 = source[start + 3]
            key = b0 or (b1 shl SHIFT_8) or (b2 shl SHIFT_16) or (b3 shl SHIFT_24)
        } else {
            if (length >= 1) key = key or source[start]
            if (length >= 2) key = key or (source[start + 1] shl SHIFT_8)
            if (length >= 3) key = key or (source[start + 2] shl SHIFT_16)
        }
    } else {
        val localData = rawData
        if (length >= 4) {
            val b0 = localData[start].toInt() and BYTE_MASK
            val b1 = localData[start + 1].toInt() and BYTE_MASK
            val b2 = localData[start + 2].toInt() and BYTE_MASK
            val b3 = localData[start + 3].toInt() and BYTE_MASK
            key = b0 or (b1 shl SHIFT_8) or (b2 shl SHIFT_16) or (b3 shl SHIFT_24)
        } else {
            if (length >= 1) {
                val b = localData[start].toInt() and BYTE_MASK
                key = key or b
            }
            if (length >= 2) {
                val b = localData[start + 1].toInt() and BYTE_MASK
                key = key or (b shl SHIFT_8)
            }
            if (length >= 3) {
                val b = localData[start + 2].toInt() and BYTE_MASK
                key = key or (b shl SHIFT_16)
            }
        }
    }
    return key
}

@Suppress("NOTHING_TO_INLINE")
private inline fun GhostJsonReader.verifyKeyMatch(
    start: Int,
    length: Int,
    expected: okio.ByteString,
    consumeSeparator: Boolean
): Boolean {
    if (expected.size == length && source.contentEquals(start, expected)) {
        val endPos = start + length
        position = endPos + 1
        nextTokenByte = -1
        if (consumeSeparator) {
            // Fast inline colon check — avoids findNextNonWhitespace in minified JSON
            if (position < limit) {
                val colonToken = if (isStreaming) {
                    source[position]
                } else {
                    rawData[position].toInt() and BYTE_MASK
                }
                if (colonToken == COLON_INT) {
                    position++
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

fun GhostJsonReader.skipValue() {
    val token = peekNextToken()
    when (token) {
        OPEN_OBJ_INT -> {
            beginObject()
            while (hasNext()) {
                if (peekNextToken() != QUOTE_INT) {
                    throwError(ERR_EXPECTED_KEY)
                }
                skipQuotedString()
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
        TRUE_CHAR_INT -> skipAndValidateLiteral(TRUE_BS)
        FALSE_CHAR_INT -> skipAndValidateLiteral(FALSE_BS)
        NULL_CHAR_INT -> skipAndValidateLiteral(NULL_BS)

        else -> skipNumber()
    }
}

inline fun <T> GhostJsonReader.readList(itemParser: () -> T): List<T> {
    beginArray()
    if (peekNextToken() == CLOSE_ARR_INT) {
        endArray()
        return emptyList()
    }
    val list = ArrayList<T>(initialCollectionCapacity)
    val maxSize = maxCollectionSize

    while (true) {
        list.add(itemParser())
        val next = nextNonWhitespace()
        if (next == CLOSE_ARR_INT) {
            depth--
            break
        }
        if (next != COMMA_INT) {
            throwError("$ERR_EXPECTED_COMMA_OR_CLOSE_ARR but found $next")
        }
        if (list.size > maxSize) {
            throwError("$ERR_MAX_COLLECTION_SIZE ($maxSize)")
        }
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

    val map = HashMap<K, V>(initialCollectionCapacity)
    val maxSize = maxCollectionSize

    while (true) {
        val key = keyParser()
        consumeKeySeparator()
        val value = valueParser()
        map[key] = value

        val next = nextNonWhitespace()
        if (next == CLOSE_OBJ_INT) {
            depth--
            break
        }
        if (next != COMMA_INT) {
            throwError("$ERR_EXPECTED_COMMA_OR_CLOSE_OBJ but found $next")
        }
        if (map.size > maxSize) {
            throwError("$ERR_MAX_COLLECTION_SIZE ($maxSize)")
        }
    }
    return map
}

@InternalGhostApi
inline fun <T> GhostJsonReader.decodeResilient(block: () -> T): T? {
    val savedPos = this.position
    val savedToken = this.nextTokenByte
    try {
        return block()
    } catch (_: GhostJsonException) {
        this.position = savedPos
        this.nextTokenByte = savedToken
        this.skipValue()
        return null
    }
}

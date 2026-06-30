@file:Suppress("NOTHING_TO_INLINE")
@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser

import com.ghost.serialization.parser.GhostJsonConstants as C
import com.ghost.serialization.parser.GhostHeuristics.initialCollectionCapacity
import com.ghost.serialization.InternalGhostApi

fun GhostJsonStringReader.beginObject() {
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

fun GhostJsonStringReader.endObject() {
    if (nextNonWhitespace() != C.CLOSE_OBJ_INT) {
        throwError(C.ERR_EXPECTED_END_OBJ)
    }
    if (depth > 0) depth--
}

fun GhostJsonStringReader.beginArray() {
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

fun GhostJsonStringReader.endArray() {
    if (nextNonWhitespace() != C.CLOSE_ARR_INT) {
        throwError(C.ERR_EXPECTED_END_ARR)
    }
    if (depth > 0) depth--
}

fun GhostJsonStringReader.hasNext(): Boolean {
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
                if (required) {
                    throwError(C.ERR_EXPECTED_COMMA)
                }
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

fun GhostJsonStringReader.nextKey(): String? {
    val token = peekNextToken()
    if (token == C.CLOSE_OBJ_INT) {
        return null
    }
    if (strictMode && depth < C.MAX_BITMASK_DEPTH) {
        val bit = C.BITMASK_UNIT shl depth
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

fun GhostJsonStringReader.consumeKeySeparator() {
    if (nextNonWhitespace() != C.COLON_INT) {
        throwError(C.ERR_EXPECTED_COLON)
    }
}

fun GhostJsonStringReader.consumeArraySeparator() {
    if (strictMode && depth < C.MAX_BITMASK_DEPTH) {
        val bit = C.BITMASK_UNIT shl depth
        if ((commaConsumedMask and bit) != C.RESULT_NONE) {
            commaConsumedMask = commaConsumedMask and bit.inv()
            needsCommaMask = needsCommaMask or bit
            return
        }
        val token = peekNextToken()
        val required = (needsCommaMask and bit) != C.RESULT_NONE
        if (token == C.COMMA_INT) {
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

fun GhostJsonStringReader.nextBoolean(): Boolean {
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
            return matchCoerceBooleanBytes()
        }
    }
    throwError(C.ERR_EXPECTED_BOOLEAN)
}

fun GhostJsonStringReader.nextString(): String = readQuotedString()

fun GhostJsonStringReader.isNextNullValue(): Boolean = peekNextToken() == C.NULL_CHAR_INT

fun GhostJsonStringReader.consumeNull() {
    skipAndValidateLiteral(C.NULL_BS)
}

internal inline fun GhostJsonStringReader.findClosingQuote(start: Int, lim: Int): Int {
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

    while (currentPosition + indexOffset3 < lim) {
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

    while (currentPosition < lim) {
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
    val lim = limit
    val contentStart = position + 1
    val end = findClosingQuote(contentStart, lim)
    if (end == -1) throwError(C.UNTERMINATED_STRING_ERROR)
    val length = end - contentStart
    position = end + 1
    nextTokenByte = C.RESET_TOKEN_BYTE
    return matchCoerceBooleanBytes(
        start = contentStart,
        length = length,
        onError = { throwError(C.ERR_EXPECTED_BOOLEAN) },
        getByte = { chars[it].code },
    )
}

fun GhostJsonStringReader.selectNameAndConsume(options: JsonReaderOptions): Int =
    internalSelect(options, consumeSeparator = true)

fun GhostJsonStringReader.selectString(options: JsonReaderOptions): Int =
    internalSelect(options, consumeSeparator = false)

private fun GhostJsonStringReader.internalSelect(options: JsonReaderOptions, consumeSeparator: Boolean): Int {
    var token = peekNextToken()
    if (token == C.CLOSE_OBJ_INT) {
        return -1
    }

    token = selectValidateCommas(token, consumeSeparator)

    if (token != C.QUOTE_INT) {
        throwExpectedKeyOrStringError(consumeSeparator)
    }
    val start = position + 1
    val end = findClosingQuote(start, limit)

    if (end == -1) {
        throwUnterminatedStringError()
    }

    val length = end - start
    val key = computeKeyHash(start, length, options.hasCollisions)

    val hasIndex = ((key * options.multiplier + length) shr options.shift) and (options.dispatch.size - 1)
    val index = options.dispatch[hasIndex]

    if (index != C.MATCH_END) {
        if (verifyKeyMatch(start, length, options.rawStrings[index], consumeSeparator)) {
            return index
        }
    }

    return handleSelectNoMatch(start, end, length, consumeSeparator)
}

private fun GhostJsonStringReader.selectValidateCommas(token: Int, consumeSeparator: Boolean): Int {
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

private fun GhostJsonStringReader.handleSelectNoMatch(start: Int, end: Int, length: Int, consumeSeparator: Boolean): Int {
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
        val unknownKey = rawData.substring(start, end)
        throwError("${C.STRICT_MODE_UNKNOWN_FIELD}$unknownKey")
    }
    return C.MATCH_NONE
}

private fun GhostJsonStringReader.throwExpectedKeyOrStringError(consumeSeparator: Boolean) {
    throwError(if (consumeSeparator) C.ERR_EXPECTED_KEY else C.ERR_EXPECTED_STRING)
}

private fun GhostJsonStringReader.throwUnterminatedStringError() {
    throwError(C.UNTERMINATED_STRING_ERROR)
}

private inline fun GhostJsonStringReader.computeKeyHash(start: Int, length: Int, hasCollisions: Boolean): Int {
    var key = 0
    val chars = rawChars
    if (length >= 4) {
        val byte0 = chars[start].code
        val byte1 = chars[start + 1].code
        val byte2 = chars[start + 2].code
        val byte3 = chars[start + 3].code
        key = byte0 or (byte1 shl C.SHIFT_8) or (byte2 shl C.SHIFT_16) or (byte3 shl C.SHIFT_24)
        if (hasCollisions) {
            key = key xor chars[start + length - 1].code
            key = key xor chars[start + (length shr C.SINGLE_CHAR_SIZE)].code
        }
    } else {
        if (length >= 1) {
            key = key or chars[start].code
        }
        if (length >= 2) {
            key = key or (chars[start + 1].code shl C.SHIFT_8)
        }
        if (length >= 3) {
            key = key or (chars[start + 2].code shl C.SHIFT_16)
        }
    }
    return key
}

private inline fun GhostJsonStringReader.verifyKeyMatch(
    start: Int,
    length: Int,
    expected: String,
    consumeSeparator: Boolean
): Boolean {
    if (expected.length == length) {
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
    val chars = rawChars
    val localLimit = limit
    val start = position

    val whitespaceMask = C.WHITESPACE_MASK
    var pos = start
    var isValidStart = false
    while (pos < localLimit) {
        val code = chars[pos].code
        if (code > C.SPACE_INT || (whitespaceMask shr code) and C.BYTE_SHIFT_UNIT == C.RESULT_NONE) {
            if (code == C.OPEN_OBJ_INT) {
                isValidStart = true
                pos++
            } else if (code == C.QUOTE_INT) {
                isValidStart = true
            }
            break
        }
        pos++
    }
    if (!isValidStart) return null

    val scanLimit = (pos + GhostHeuristics.maxDiscriminatorPeekDistance)
        .coerceAtMost(localLimit)

    val keySize = name.length

    while (pos < scanLimit) {
        val code = chars[pos].code
        if (code == C.QUOTE_INT) {
            val keyStart = pos + 1
            if (keyStart + keySize < scanLimit && chars[keyStart + keySize].code == C.QUOTE_INT) {
                var match = true
                for (i in 0 until keySize) {
                    if (chars[keyStart + i] != name[i]) {
                        match = false
                        break
                    }
                }
                if (match) {
                    val afterKey = keyStart + keySize + 1
                    var colonPos = afterKey
                    var foundColon = false
                    while (colonPos < scanLimit) {
                        val c = chars[colonPos].code
                        if (c > C.SPACE_INT || (whitespaceMask shr c) and C.BYTE_SHIFT_UNIT == C.RESULT_NONE) {
                            if (c == C.COLON_INT) {
                                foundColon = true
                                colonPos++
                            }
                            break
                        }
                        colonPos++
                    }
                    if (!foundColon) return null

                    var quotePos = colonPos
                    var foundQuote = false
                    while (quotePos < scanLimit) {
                        val c = chars[quotePos].code
                        if (c > C.SPACE_INT || (whitespaceMask shr c) and C.BYTE_SHIFT_UNIT == C.RESULT_NONE) {
                            if (c == C.QUOTE_INT) {
                                foundQuote = true
                                quotePos++
                            }
                            break
                        }
                        quotePos++
                    }
                    if (!foundQuote) return null

                    val valueStart = quotePos
                    var valPos = valueStart
                    while (valPos < scanLimit) {
                        val valChar = chars[valPos].code
                        if (valChar == C.QUOTE_INT) {
                            return rawData.substring(valueStart, valPos)
                        }
                        if (valChar == C.BACKSLASH_INT) {
                            return null
                        }
                        valPos++
                    }
                    return null
                }
            }
            pos = keyStart
            while (pos < scanLimit) {
                val skipChar = chars[pos].code
                if (skipChar == C.QUOTE_INT) {
                    pos++
                    break
                }
                if (skipChar == C.BACKSLASH_INT) {
                    pos++
                }
                pos++
            }
        } else if (code == C.OPEN_OBJ_INT || code == C.OPEN_ARR_INT) {
            return null
        } else {
            pos++
        }
    }
    return null
}

fun GhostJsonStringReader.skipValue() {
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

inline fun <T> GhostJsonStringReader.readList(crossinline itemParser: () -> T): List<T> {
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
            if (depth > 0) {
                depth--
            }
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

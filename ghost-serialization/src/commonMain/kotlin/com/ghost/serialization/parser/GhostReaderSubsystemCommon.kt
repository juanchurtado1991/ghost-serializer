package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.releaseScratchBuffer
import com.ghost.serialization.parser.GhostJsonConstants as C

internal inline fun beginObjectImpl(
    nextNonWhitespace: () -> Int,
    getDepth: () -> Int,
    setDepth: (Int) -> Unit,
    maxDepth: Int,
    throwError: (String) -> Nothing
) {
    if (nextNonWhitespace() != C.OPEN_OBJ_INT) throwError(C.ERR_EXPECTED_BEGIN_OBJ)
    val d = getDepth() + 1
    setDepth(d)
    if (d > maxDepth) throwError(C.ERR_DEPTH_EXCEEDED)
}

internal inline fun endObjectImpl(
    nextNonWhitespace: () -> Int,
    getDepth: () -> Int,
    setDepth: (Int) -> Unit,
    throwError: (String) -> Nothing
) {
    if (nextNonWhitespace() != C.CLOSE_OBJ_INT) throwError(C.ERR_EXPECTED_END_OBJ)
    setDepth(getDepth() - 1)
}

internal inline fun beginArrayImpl(
    nextNonWhitespace: () -> Int,
    getDepth: () -> Int,
    setDepth: (Int) -> Unit,
    maxDepth: Int,
    throwError: (String) -> Nothing
) {
    if (nextNonWhitespace() != C.OPEN_ARR_INT) throwError(C.ERR_EXPECTED_BEGIN_ARR)
    val d = getDepth() + 1
    setDepth(d)
    if (d > maxDepth) throwError(C.ERR_DEPTH_EXCEEDED)
}

internal inline fun endArrayImpl(
    nextNonWhitespace: () -> Int,
    getDepth: () -> Int,
    setDepth: (Int) -> Unit,
    throwError: (String) -> Nothing
) {
    if (nextNonWhitespace() != C.CLOSE_ARR_INT) throwError(C.ERR_EXPECTED_END_ARR)
    setDepth(getDepth() - 1)
}

internal inline fun hasNextImpl(
    peekNextToken: () -> Int,
    internalSkip: (Int) -> Unit,
    throwError: (String) -> Nothing
): Boolean {
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

internal inline fun nextKeyImpl(
    peekNextToken: () -> Int,
    internalSkip: (Int) -> Unit,
    readQuotedString: () -> String,
    throwError: (String) -> Nothing
): String? {
    val token = peekNextToken()
    if (token == C.CLOSE_OBJ_INT) return null
    if (token == C.COMMA_INT) {
        internalSkip(1)
        if (peekNextToken() == C.CLOSE_OBJ_INT) {
            throwError(C.ERR_TRAILING_COMMA)
        }
    }
    return readQuotedString()
}

internal inline fun consumeKeySeparatorImpl(
    nextNonWhitespace: () -> Int,
    throwError: (String) -> Nothing
) {
    if (nextNonWhitespace() != C.COLON_INT) {
        throwError(C.ERR_EXPECTED_COLON)
    }
}

internal inline fun consumeArraySeparatorImpl(
    peekNextToken: () -> Int,
    internalSkip: (Int) -> Unit
) {
    if (peekNextToken() == C.COMMA_INT) internalSkip(1)
}

internal inline fun nextBooleanImpl(
    peekNextToken: () -> Int,
    skipAndValidateLiteral: (okio.ByteString) -> Unit,
    coerceBooleans: Boolean,
    internalSkip: (Int) -> Unit,
    readQuotedString: () -> String,
    throwError: (String) -> Nothing
): Boolean {
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

internal inline fun internalSelectImpl(
    options: JsonReaderOptions,
    consumeSeparator: Boolean,
    peekNextToken: () -> Int,
    internalSkip: (Int) -> Unit,
    getPosition: () -> Int,
    setPosition: (Int) -> Unit,
    getLimit: () -> Int,
    findClosingQuote: (Int, Int) -> Int,
    computeKeyHash: (Int, Int) -> Int,
    verifyKeyMatch: (Int, Int, okio.ByteString, Boolean) -> Boolean,
    setNextTokenByte: (Int) -> Unit,
    getByte: (Int) -> Int,
    consumeKeySeparator: () -> Unit,
    strictMode: Boolean,
    decodeToString: (Int, Int) -> String,
    throwError: (String) -> Nothing
): Int {
    var token = peekNextToken()
    if (token == C.CLOSE_OBJ_INT) return -1
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

    val start = getPosition() + 1
    val limit = getLimit()
    val end = findClosingQuote(start, limit)
    if (end == -1) throwError(C.UNTERMINATED_STRING_ERROR)

    val length = end - start
    val key = computeKeyHash(start, length)
    val hasIndex = ((key * options.multiplier + length) shr options.shift) and C.HASH_MASK
    val index = options.dispatch[hasIndex]

    if (index != C.MATCH_END) {
        if (
            verifyKeyMatch(
                start,
                length,
                options.rawBytes[index],
                consumeSeparator
            )
        ) {
            return index
        }
    }

    // No match found
    val newPos = end + 1
    setPosition(newPos)
    setNextTokenByte(C.MATCH_END)
    if (consumeSeparator) {
        if (
            newPos < limit &&
            getByte(newPos) == C.COLON_INT
        ) {
            setPosition(newPos + 1)
        } else {
            consumeKeySeparator()
        }
    } else if (strictMode) {
        val unknownKey = decodeToString(start, end)
        throwError("${C.STRICT_MODE_UNKNOWN_FIELD}$unknownKey")
    }

    return C.MATCH_NONE
}

internal inline fun computeKeyHashImpl(
    start: Int,
    length: Int,
    getByte: (Int) -> Int
): Int {
    var key = 0
    if (length >= 4) {
        val b0 = getByte(start)
        val b1 = getByte(start + 1)
        val b2 = getByte(start + 2)
        val b3 = getByte(start + 3)
        key = b0 or (b1 shl C.SHIFT_8) or (b2 shl C.SHIFT_16) or (b3 shl C.SHIFT_24)
    } else {
        if (length >= 1) key = key or getByte(start)
        if (length >= 2) key = key or (getByte(start + 1) shl C.SHIFT_8)
        if (length >= 3) key = key or (getByte(start + 2) shl C.SHIFT_16)
    }
    return key
}

internal inline fun verifyKeyMatchImpl(
    start: Int,
    length: Int,
    expected: okio.ByteString,
    consumeSeparator: Boolean,
    setPosition: (Int) -> Unit,
    setNextTokenByte: (Int) -> Unit,
    getLimit: () -> Int,
    getByte: (Int) -> Int,
    consumeKeySeparator: () -> Unit,
    contentEquals: (Int, okio.ByteString) -> Boolean
): Boolean {
    if (expected.size == length && contentEquals(start, expected)) {
        val endPos = start + length
        val newPos = endPos + 1
        setPosition(newPos)
        setNextTokenByte(-1)
        if (consumeSeparator) {
            val limit = getLimit()
            if (newPos < limit) {
                val colonToken = getByte(newPos)
                if (colonToken == C.COLON_INT) {
                    setPosition(newPos + 1)
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

internal inline fun skipValueImpl(
    peekNextToken: () -> Int,
    beginObject: () -> Unit,
    hasNext: () -> Boolean,
    skipQuotedString: () -> Unit,
    consumeKeySeparator: () -> Unit,
    skipValue: () -> Unit,
    endObject: () -> Unit,
    beginArray: () -> Unit,
    endArray: () -> Unit,
    skipAndValidateLiteral: (okio.ByteString) -> Unit,
    skipNumber: () -> Unit,
    throwError: (String) -> Nothing
) {
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

        C.QUOTE_INT -> skipQuotedString()
        C.TRUE_CHAR_INT -> skipAndValidateLiteral(C.TRUE_BS)
        C.FALSE_CHAR_INT -> skipAndValidateLiteral(C.FALSE_BS)
        C.NULL_CHAR_INT -> skipAndValidateLiteral(C.NULL_BS)

        else -> skipNumber()
    }
}

@PublishedApi
internal inline fun <T> readListImpl(
    beginArray: () -> Unit,
    peekNextToken: () -> Int,
    endArray: () -> Unit,
    getInitialCollectionCapacity: () -> Int,
    getMaxCollectionSize: () -> Int,
    itemParser: () -> T,
    nextNonWhitespace: () -> Int,
    decrementDepth: () -> Unit,
    throwError: (String) -> Nothing
): List<T> {
    beginArray()
    if (peekNextToken() == C.CLOSE_ARR_INT) {
        endArray()
        return emptyList()
    }
    val list = ArrayList<T>(getInitialCollectionCapacity())
    val maxSize = getMaxCollectionSize()

    while (true) {
        list.add(itemParser())
        val next = nextNonWhitespace()
        if (next == C.CLOSE_ARR_INT) {
            decrementDepth()
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

@PublishedApi
internal inline fun <K, V> readMapImpl(
    beginObject: () -> Unit,
    peekNextToken: () -> Int,
    endObject: () -> Unit,
    getInitialCollectionCapacity: () -> Int,
    getMaxCollectionSize: () -> Int,
    keyParser: () -> K,
    consumeKeySeparator: () -> Unit,
    valueParser: () -> V,
    nextNonWhitespace: () -> Int,
    decrementDepth: () -> Unit,
    throwError: (String) -> Nothing
): Map<K, V> {
    beginObject()
    if (peekNextToken() == C.CLOSE_OBJ_INT) {
        endObject()
        return emptyMap()
    }

    val map = HashMap<K, V>(getInitialCollectionCapacity())
    val maxSize = getMaxCollectionSize()

    while (true) {
        val key = keyParser()
        consumeKeySeparator()
        val value = valueParser()
        map[key] = value

        val next = nextNonWhitespace()
        if (next == C.CLOSE_OBJ_INT) {
            decrementDepth()
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

@PublishedApi
internal inline fun <T> decodeResilientImpl(
    getPosition: () -> Int,
    setPosition: (Int) -> Unit,
    getNextTokenByte: () -> Int,
    setNextTokenByte: (Int) -> Unit,
    skipValue: () -> Unit,
    block: () -> T
): T? {
    val savedPos = getPosition()
    val savedToken = getNextTokenByte()
    try {
        return block()
    } catch (_: GhostJsonException) {
        setPosition(savedPos)
        setNextTokenByte(savedToken)
        skipValue()
        return null
    }
}

@PublishedApi
internal fun growBuffer(outBuffer: ByteArray, outPos: Int): ByteArray {
    val newBuffer = acquireScratchBuffer(outBuffer.size * C.BUFFER_SCALE_FACTOR)
    outBuffer.copyInto(newBuffer, 0, 0, outPos)
    releaseScratchBuffer(outBuffer)
    return newBuffer
}

@PublishedApi
internal inline fun readQuotedStringImpl(
    getPosition: () -> Int,
    setPosition: (Int) -> Unit,
    getLimit: () -> Int,
    getNextNonWhitespace: () -> Int,
    setNextTokenByte: (Int) -> Unit,
    getByte: (Int) -> Int,
    getStringPool: () -> Array<String?>,
    setLastScanContentWas7BitOnly: (Boolean) -> Unit,
    scanString: (Int, Int) -> Long,
    decodeJsonStringRange: (Int, Int, Boolean) -> String,
    contentEqualsString: (Int, Int, String) -> Boolean,
    parseUnicodeHex: (Int) -> Int,
    throwError: (String) -> Nothing
): String {
    if (getNextNonWhitespace() != C.QUOTE_INT) {
        throwError(C.ERR_EXPECTED_QUOTE)
    }

    val start = getPosition()
    val limit = getLimit()
    val scanResult = scanString(start, limit)

    if (scanResult != -1L) {
        val length = ((scanResult and C.SCAN_LENGTH_MASK) ushr C.SCAN_LENGTH_SHIFT).toInt()
        val rollingHash = scanResult.toInt()
        val only7Bit = (scanResult and C.SCAN_7BIT_BIT) != 0L
        setLastScanContentWas7BitOnly(only7Bit)
        val end = start + length
        if (length <= 0) {
            setPosition(end + 1)
            return ""
        }
        if (length > GhostHeuristics.maxStringPoolLength) {
            val result = decodeJsonStringRange(start, end, only7Bit)
            setPosition(end + 1)
            return result
        }

        val poolBucketIndex = rollingHash and (C.STR_POOL_SIZE - 1)
        val stringPool = getStringPool()
        val cachedString = stringPool[poolBucketIndex]

        if (cachedString != null && contentEqualsString(start, length, cachedString)) {
            setPosition(end + 1)
            return cachedString
        }

        val decodedString = decodeJsonStringRange(start, end, only7Bit)
        stringPool[poolBucketIndex] = decodedString
        setPosition(end + 1)
        return decodedString
    }

    // Slow path: manual string building for escapes (Bitwise & Zero-Allocation approach)
    var outBuffer = acquireScratchBuffer(C.TIER_SMALL_INT)
    var outPos = 0

    try {
        var pos = start
        while (pos < limit) {
            val byteValue = getByte(pos++)
            if (byteValue == C.QUOTE_INT) {
                setPosition(pos)
                setNextTokenByte(C.RESET_TOKEN_BYTE)
                return outBuffer.decodeToString(0, outPos)
            }

            if (byteValue in C.CONTROL_CHAR_START_INT..C.CONTROL_CHAR_LIMIT_INT) {
                setPosition(pos)
                throwError(C.UNESCAPED_CONTROL_CHAR_ERROR)
            }

            if (byteValue == C.BACKSLASH_INT) {
                if (pos >= limit) {
                    setPosition(pos)
                    throwError(C.UNTERMINATED_ESCAPE_ERROR)
                }
                val escaped = getByte(pos++)
                when (escaped) {
                    C.UNICODE_PREFIX_U_INT -> {
                        if (pos + C.UNICODE_HEX_LENGTH > limit) {
                            setPosition(pos)
                            throwError(C.UNTERMINATED_UNICODE_ERROR)
                        }

                        var code = parseUnicodeHex(pos)
                        pos += C.UNICODE_HEX_LENGTH

                        if (code in C.HIGH_SURROGATE_START..C.HIGH_SURROGATE_END) {
                            if (pos + C.SURROGATE_OFFSET > limit ||
                                getByte(pos) == C.BACKSLASH_INT &&
                                getByte(pos + C.SINGLE_CHAR_SIZE) == C.UNICODE_PREFIX_U_INT
                            ) {
                                // Valid surrogate pair check
                                pos += C.UNICODE_ESCAPE_PREFIX_SIZE
                                val lowCode = parseUnicodeHex(pos)
                                if (lowCode in C.LOW_SURROGATE_START..C.LOW_SURROGATE_END) {
                                    pos += C.UNICODE_HEX_LENGTH
                                    code = C.UNICODE_BASE +
                                            ((code - C.HIGH_SURROGATE_START) shl C.SHIFT_10) +
                                            (lowCode - C.LOW_SURROGATE_START)
                                } else {
                                    setPosition(pos)
                                    throwError(C.ERR_HIGH_SURROGATE)
                                }
                            } else {
                                setPosition(pos)
                                throwError(C.ERR_HIGH_SURROGATE)
                            }
                        }

                        // Encode code point to UTF-8 bytes in outBuffer
                        if (code <= C.UTF8_1BYTE_MAX) {
                            if (outPos + 1 > outBuffer.size) { outBuffer = growBuffer(outBuffer, outPos) }
                            outBuffer[outPos++] = code.toByte()
                        } else if (code <= C.UTF8_2BYTE_MAX) {
                            if (outPos + 2 > outBuffer.size) { outBuffer = growBuffer(outBuffer, outPos) }
                            outBuffer[outPos++] = (C.UTF8_2BYTE_PREFIX or (code shr C.UTF8_SHIFT_6)).toByte()
                            outBuffer[outPos++] = (C.UTF8_CONT_PREFIX or (code and C.UTF8_CONT_MASK)).toByte()
                        } else if (code <= C.BMP_LIMIT) {
                            if (outPos + 3 > outBuffer.size) { outBuffer = growBuffer(outBuffer, outPos) }
                            outBuffer[outPos++] = (C.UTF8_3BYTE_PREFIX or (code shr C.UTF8_SHIFT_12)).toByte()
                            outBuffer[outPos++] = (C.UTF8_CONT_PREFIX or ((code shr C.UTF8_SHIFT_6) and C.UTF8_CONT_MASK)).toByte()
                            outBuffer[outPos++] = (C.UTF8_CONT_PREFIX or (code and C.UTF8_CONT_MASK)).toByte()
                        } else {
                            if (outPos + 4 > outBuffer.size) { outBuffer = growBuffer(outBuffer, outPos) }
                            outBuffer[outPos++] = (C.UTF8_4BYTE_PREFIX or (code shr C.UTF8_SHIFT_18)).toByte()
                            outBuffer[outPos++] = (C.UTF8_CONT_PREFIX or ((code shr C.UTF8_SHIFT_12) and C.UTF8_CONT_MASK)).toByte()
                            outBuffer[outPos++] = (C.UTF8_CONT_PREFIX or ((code shr C.UTF8_SHIFT_6) and C.UTF8_CONT_MASK)).toByte()
                            outBuffer[outPos++] = (C.UTF8_CONT_PREFIX or (code and C.UTF8_CONT_MASK)).toByte()
                        }
                    }

                    C.N_BYTE_INT -> {
                        if (outPos + 1 > outBuffer.size) { outBuffer = growBuffer(outBuffer, outPos) }
                        outBuffer[outPos++] = C.LF_INT.toByte()
                    }

                    C.R_BYTE_INT -> {
                        if (outPos + 1 > outBuffer.size) { outBuffer = growBuffer(outBuffer, outPos) }
                        outBuffer[outPos++] = C.CR_INT.toByte()
                    }

                    C.T_BYTE_INT -> {
                        if (outPos + 1 > outBuffer.size) { outBuffer = growBuffer(outBuffer, outPos) }
                        outBuffer[outPos++] = C.TAB_INT.toByte()
                    }

                    C.B_BYTE_INT -> {
                        if (outPos + 1 > outBuffer.size) { outBuffer = growBuffer(outBuffer, outPos) }
                        outBuffer[outPos++] = C.BS_INT.toByte()
                    }

                    C.F_BYTE_INT -> {
                        if (outPos + 1 > outBuffer.size) { outBuffer = growBuffer(outBuffer, outPos) }
                        outBuffer[outPos++] = C.FF_INT.toByte()
                    }

                    else -> {
                        if (outPos + 1 > outBuffer.size) { outBuffer = growBuffer(outBuffer, outPos) }
                        outBuffer[outPos++] = escaped.toByte()
                    }
                }
            } else {
                if (outPos + 1 > outBuffer.size) { outBuffer = growBuffer(outBuffer, outPos) }
                outBuffer[outPos++] = byteValue.toByte()
            }
        }
        setPosition(pos)
    } finally {
        releaseScratchBuffer(outBuffer)
    }
    throwError(C.UNTERMINATED_STRING_ERROR)
}

@PublishedApi
internal inline fun skipQuotedStringImpl(
    getPosition: () -> Int,
    setPosition: (Int) -> Unit,
    getLimit: () -> Int,
    getNextNonWhitespace: () -> Int,
    setNextTokenByte: (Int) -> Unit,
    getByte: (Int) -> Int,
    findClosingQuote: (Int, Int) -> Int,
    parseUnicodeHex: (Int) -> Int,
    throwError: (String) -> Nothing
) {
    if (getNextNonWhitespace() != C.QUOTE_INT) {
        throwError(C.ERR_EXPECTED_QUOTE)
    }

    val start = getPosition()
    val limit = getLimit()
    val end = findClosingQuote(start, limit)
    if (end != -1) {
        setPosition(end + 1)
        return
    }

    var pos = start
    while (pos < limit) {
        val byteValue = getByte(pos++)
        if (byteValue == C.QUOTE_INT) {
            setPosition(pos)
            setNextTokenByte(C.RESET_TOKEN_BYTE)
            return
        }

        if (byteValue in C.CONTROL_CHAR_START_INT..C.CONTROL_CHAR_LIMIT_INT) {
            setPosition(pos)
            throwError(C.UNESCAPED_CONTROL_CHAR_ERROR)
        }

        if (byteValue == C.BACKSLASH_INT) {
            if (pos >= limit) {
                setPosition(pos)
                throwError(C.UNTERMINATED_ESCAPE_ERROR)
            }
            val escaped = getByte(pos++)

            if (escaped == C.UNICODE_PREFIX_U_INT) {
                if (pos + C.UNICODE_HEX_LENGTH > limit) {
                    setPosition(pos)
                    throwError(C.UNTERMINATED_UNICODE_ERROR)
                }
                parseUnicodeHex(pos)
                pos += C.UNICODE_HEX_LENGTH
            }
        }
    }
    setPosition(pos)
    throwError(C.UNTERMINATED_STRING_ERROR)
}

@PublishedApi
internal inline fun parseUnicodeHexImpl(
    currentPosition: Int,
    getByte: (Int) -> Int,
    throwError: (String) -> Nothing
): Int {
    val hexByte0 = getByte(currentPosition)
    val hexByte1 = getByte(currentPosition + 1)
    val hexByte2 = getByte(currentPosition + 2)
    val hexByte3 = getByte(currentPosition + 3)

    val hexLookupTable = C.HEX_LUT
    val digitValue0 = hexLookupTable[hexByte0]
    val digitValue1 = hexLookupTable[hexByte1]
    val digitValue2 = hexLookupTable[hexByte2]
    val digitValue3 = hexLookupTable[hexByte3]

    if ((digitValue0 or digitValue1 or digitValue2 or digitValue3) < 0) {
        throwError("Invalid unicode escape at $currentPosition")
    }

    return (digitValue0 shl C.SHIFT_12) or
            (digitValue1 shl C.SHIFT_8) or
            (digitValue2 shl C.SHIFT_4) or
            digitValue3
}

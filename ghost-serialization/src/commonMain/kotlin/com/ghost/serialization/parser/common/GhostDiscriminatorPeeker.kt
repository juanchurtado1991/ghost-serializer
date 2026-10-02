package com.ghost.serialization.parser.common

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.GhostDiscriminatorPeeker.peek
import com.ghost.serialization.parser.common.GhostDiscriminatorPeeker.peekChars
import com.ghost.serialization.parser.common.GhostHeuristics.maxDiscriminatorPeekDistance
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.BACKSLASH_INT
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.BYTE_MASK
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants.BYTE_SHIFT_UNIT
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.CLOSE_ARR_INT
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.CLOSE_OBJ_INT
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.COLON_INT
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.OPEN_ARR_INT
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.OPEN_OBJ_INT
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.QUOTE_INT
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants.RESULT_NONE
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.SPACE_INT
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants.WHITESPACE_MASK
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import okio.ByteString

/**
 * Peeks a JSON discriminator key's value without full parsing, for KSP-generated polymorphic
 * deserializers — returns `null` if key isn't found within [maxDiscriminatorPeekDistance]
 * bytes, or the value looks too complex to extract without a real parse. [peek] serves the
 * byte-backed channels (flat/streaming) and takes [peek]'s `isStreaming` flag to pick a direct
 * `rawData` array read over the [GhostSource] indirection whenever a raw array is actually
 * available; [peekChars] is the char-channel counterpart for [GhostJsonStringReader], same scan
 * logic, no UTF-8 bridge.
 */
@InternalGhostApi
object GhostDiscriminatorPeeker {

    @PublishedApi
    internal fun contentEqualsKey(
        chars: CharArray,
        keyStart: Int,
        key: String,
    ): Boolean {
        val keySize = key.length
        for (i in 0 until keySize) {
            if (chars[keyStart + i] != key[i]) {
                return false
            }
        }
        return true
    }

    @PublishedApi
    internal inline fun extractValue(
        start: Int,
        limit: Int,
        crossinline getByte: (Int) -> Int,
        crossinline decodeValue: (valueStart: Int, valueEnd: Int) -> String?,
    ): String? {
        var position = start
        val valueStart = position
        while (position < limit) {
            val valueByte = getByte(position)
            if (valueByte == QUOTE_INT) {
                return decodeValue(valueStart, position)
            }
            if (valueByte == BACKSLASH_INT) {
                return null
            }
            position++
        }
        return null
    }

    fun peek(
        source: GhostSource,
        rawData: ByteArray,
        isStreaming: Boolean,
        start: Int,
        limit: Int,
        key: ByteString,
    ): String? {
        val getByte = if (isStreaming) {
            { pos: Int -> source[pos] }
        } else {
            { pos: Int -> rawData[pos].toInt() and BYTE_MASK }
        }
        return peekInternal(
            start = start,
            limit = limit,
            keySize = key.size,
            getByte = getByte,
            matchesKey = { keyStart -> source.contentEquals(start = keyStart, expected = key) },
            decodeValue = { valueStart, valueEnd -> source.decodeToString(start = valueStart, end = valueEnd) },
        )
    }

    fun peekChars(
        chars: CharArray,
        rawData: String,
        start: Int,
        limit: Int,
        key: String,
    ): String? {
        return peekInternal(
            start = start,
            limit = limit,
            keySize = key.length,
            getByte = { pos -> chars[pos].code },
            matchesKey = { keyStart -> contentEqualsKey(chars = chars, keyStart = keyStart, key = key) },
            decodeValue = { valueStart, valueEnd -> rawData.substring(valueStart, valueEnd) },
        )
    }

    @PublishedApi
    internal inline fun peekInternal(
        start: Int,
        limit: Int,
        keySize: Int,
        crossinline getByte: (Int) -> Int,
        crossinline matchesKey: (keyStart: Int) -> Boolean,
        crossinline decodeValue: (valueStart: Int, valueEnd: Int) -> String?,
    ): String? {
        var position = skipLeadingWhitespace(start = start, limit = limit, getByte = getByte)
        if (position >= limit) {
            return null
        }

        when (getByte(position)) {
            OPEN_OBJ_INT -> position++
            QUOTE_INT -> {
                // Already inside an object (e.g. after beginObject() on the string channel).
            }

            else -> return null
        }

        val scanLimit = (position + maxDiscriminatorPeekDistance).coerceAtMost(limit)

        while (position < scanLimit) {
            val byte = getByte(position)

            when (byte) {
                QUOTE_INT -> {
                    val keyStart = position + 1
                    val isKeyMatch = keyStart + keySize < scanLimit &&
                            getByte(keyStart + keySize) == QUOTE_INT &&
                            matchesKey(keyStart)

                    if (isKeyMatch) {
                        return tryExtractValue(
                            start = keyStart + keySize + 1,
                            limit = scanLimit,
                            getByte = getByte,
                            decodeValue = decodeValue,
                        )
                    }

                    position = skipString(start = keyStart, limit = scanLimit, getByte = getByte)
                }

                OPEN_OBJ_INT, OPEN_ARR_INT -> position = skipBalanced(
                    start = position,
                    open = byte,
                    limit = scanLimit,
                    getByte = getByte,
                )

                else -> position++
            }
        }
        return null
    }

    @PublishedApi
    internal inline fun skipBalanced(
        start: Int,
        open: Int,
        limit: Int,
        crossinline getByte: (Int) -> Int,
    ): Int {
        if (getByte(start) != open) {
            return start + 1
        }
        var position = start + 1
        var depth = 1
        while (position < limit && depth > 0) {
            when (getByte(position)) {
                QUOTE_INT -> position =
                    skipString(start = position + 1, limit = limit, getByte = getByte)

                OPEN_OBJ_INT, OPEN_ARR_INT -> {
                    depth++
                    position++
                }

                CLOSE_OBJ_INT, CLOSE_ARR_INT -> {
                    depth--
                    position++
                }

                else -> position++
            }
        }
        return position
    }

    @PublishedApi
    internal inline fun skipLeadingWhitespace(
        start: Int,
        limit: Int,
        crossinline getByte: (Int) -> Int,
    ): Int {
        var position = start
        while (position < limit) {
            val byte = getByte(position)
            val isNonWhitespace = byte > SPACE_INT ||
                    (WHITESPACE_MASK and (BYTE_SHIFT_UNIT shl byte)) == RESULT_NONE
            if (isNonWhitespace) return position
            position++
        }
        return position
    }

    @PublishedApi
    internal inline fun skipString(
        start: Int,
        limit: Int,
        crossinline getByte: (Int) -> Int,
    ): Int {
        var position = start
        while (position < limit) {
            val skipByte = getByte(position)
            if (skipByte == QUOTE_INT) {
                return position + 1
            }
            if (skipByte == BACKSLASH_INT) {
                position++
            }
            position++
        }
        return limit
    }

    @PublishedApi
    internal inline fun skipWhitespaceAndExpect(
        start: Int,
        limit: Int,
        expected: Int,
        crossinline getByte: (Int) -> Int,
    ): Int {
        val position = skipLeadingWhitespace(start = start, limit = limit, getByte = getByte)
        if (position >= limit) {
            return -1
        }
        return if (getByte(position) == expected) {
            position + 1
        } else {
            -1
        }
    }

    @PublishedApi
    internal inline fun tryExtractValue(
        start: Int,
        limit: Int,
        crossinline getByte: (Int) -> Int,
        crossinline decodeValue: (valueStart: Int, valueEnd: Int) -> String?,
    ): String? {
        val colonPosition = skipWhitespaceAndExpect(
            start = start,
            limit = limit,
            expected = COLON_INT,
            getByte = getByte,
        )
        if (colonPosition == -1) return null

        val quotePosition = skipWhitespaceAndExpect(
            start = colonPosition,
            limit = limit,
            expected = QUOTE_INT,
            getByte = getByte,
        )
        if (quotePosition == -1) return null

        return extractValue(
            start = quotePosition,
            limit = limit,
            getByte = getByte,
            decodeValue = decodeValue,
        )
    }
}

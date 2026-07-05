package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.GhostJsonConstants.BACKSLASH_INT
import com.ghost.serialization.parser.GhostJsonConstants.BYTE_MASK
import com.ghost.serialization.parser.GhostJsonConstants.BYTE_SHIFT_UNIT
import com.ghost.serialization.parser.GhostJsonConstants.CLOSE_ARR_INT
import com.ghost.serialization.parser.GhostJsonConstants.CLOSE_OBJ_INT
import com.ghost.serialization.parser.GhostJsonConstants.COLON_INT
import com.ghost.serialization.parser.GhostJsonConstants.OPEN_ARR_INT
import com.ghost.serialization.parser.GhostJsonConstants.OPEN_OBJ_INT
import com.ghost.serialization.parser.GhostJsonConstants.QUOTE_INT
import com.ghost.serialization.parser.GhostJsonConstants.RESULT_NONE
import com.ghost.serialization.parser.GhostJsonConstants.SPACE_INT
import com.ghost.serialization.parser.GhostHeuristics.maxDiscriminatorPeekDistance
import okio.ByteString

/**
 * Internal utility to peek at a JSON discriminator value without full parsing.
 * Optimized for speed and zero allocations.
 */
@InternalGhostApi
object GhostDiscriminatorPeeker {

    /**
     * Attempts to find the value of [key] in the JSON object starting at [start].
     * Returns the value as a string, or null if not found or if the object is too complex.
     *
     * This method decides the branch (Streaming vs Array) once and enters a zero-cost
     * modular implementation.
     *
     * Used by KSP-generated serializers for polymorphic deserialization.
     */
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
            matchesKey = { keyStart -> source.contentEquals(keyStart, key) },
            decodeValue = { valueStart, valueEnd -> source.decodeToString(valueStart, valueEnd) },
        )
    }

    /**
     * Char-channel peek for [GhostJsonStringReader] — same scan logic as [peek], zero UTF-8 bridge.
     */
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
            matchesKey = { keyStart -> contentEqualsKey(chars, keyStart, key) },
            decodeValue = { valueStart, valueEnd -> rawData.substring(valueStart, valueEnd) },
        )
    }

    @Suppress("CascadeIf")
    @PublishedApi
    internal inline fun peekInternal(
        start: Int,
        limit: Int,
        keySize: Int,
        crossinline getByte: (Int) -> Int,
        crossinline matchesKey: (keyStart: Int) -> Boolean,
        crossinline decodeValue: (valueStart: Int, valueEnd: Int) -> String?,
    ): String? {
        var position = skipLeadingWhitespace(start, limit, getByte)
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

        val scanLimit = (position + maxDiscriminatorPeekDistance)
            .coerceAtMost(limit)

        while (position < scanLimit) {
            val byte = getByte(position)

            if (byte == QUOTE_INT) {
                val keyStart = position + 1

                if (
                    keyStart + keySize < scanLimit &&
                    getByte(keyStart + keySize) == QUOTE_INT &&
                    matchesKey(keyStart)
                ) {
                    return tryExtractValue(
                        keyStart + keySize + 1,
                        scanLimit,
                        getByte,
                        decodeValue,
                    )
                }

                position = skipString(keyStart, scanLimit, getByte)
            } else if (byte == OPEN_OBJ_INT) {
                position = skipBalanced(
                    start = position,
                    open = OPEN_OBJ_INT,
                    close = CLOSE_OBJ_INT,
                    limit = scanLimit,
                    getByte = getByte,
                )
            } else if (byte == OPEN_ARR_INT) {
                position = skipBalanced(
                    start = position,
                    open = OPEN_ARR_INT,
                    close = CLOSE_ARR_INT,
                    limit = scanLimit,
                    getByte = getByte,
                )
            } else {
                position++
            }
        }
        return null
    }

    @PublishedApi
    internal inline fun tryExtractValue(
        start: Int,
        limit: Int,
        crossinline getByte: (Int) -> Int,
        crossinline decodeValue: (valueStart: Int, valueEnd: Int) -> String?,
    ): String? {
        val colonPosition = skipWhitespaceAndExpect(
            start,
            limit,
            COLON_INT,
            getByte,
        )
        if (colonPosition == -1) {
            return null
        }

        val quotePosition = skipWhitespaceAndExpect(
            colonPosition,
            limit,
            QUOTE_INT,
            getByte,
        )
        if (quotePosition == -1) {
            return null
        }

        return extractValue(
            quotePosition,
            limit,
            getByte,
            decodeValue,
        )
    }

    @PublishedApi
    internal inline fun skipLeadingWhitespace(
        start: Int,
        limit: Int,
        crossinline getByte: (Int) -> Int,
    ): Int {
        val mask = GhostJsonConstants.WHITESPACE_MASK
        var position = start
        while (position < limit) {
            val byte = getByte(position)
            if (
                byte > SPACE_INT ||
                (mask and (BYTE_SHIFT_UNIT shl byte)) == RESULT_NONE
            ) {
                return position
            }
            position++
        }
        return position
    }

    @PublishedApi
    internal inline fun skipWhitespaceAndExpect(
        start: Int,
        limit: Int,
        expected: Int,
        crossinline getByte: (Int) -> Int,
    ): Int {
        val position = skipLeadingWhitespace(start, limit, getByte)
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
    internal inline fun skipBalanced(
        start: Int,
        open: Int,
        close: Int,
        limit: Int,
        crossinline getByte: (Int) -> Int,
    ): Int {
        if (getByte(start) != open) {
            return start + 1
        }
        var position = start + 1
        var depth = 1
        while (position < limit && depth > 0) {
            when (val byte = getByte(position)) {
                QUOTE_INT -> position = skipString(position + 1, limit, getByte)
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
}

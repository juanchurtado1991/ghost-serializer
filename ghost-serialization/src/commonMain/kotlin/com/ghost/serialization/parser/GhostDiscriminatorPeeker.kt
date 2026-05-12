package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.GhostHeuristics.maxDiscriminatorPeekDistance
import com.ghost.serialization.parser.GhostJsonConstants.BACKSLASH_INT
import com.ghost.serialization.parser.GhostJsonConstants.BYTE_MASK
import com.ghost.serialization.parser.GhostJsonConstants.BYTE_SHIFT_UNIT
import com.ghost.serialization.parser.GhostJsonConstants.COLON_INT
import com.ghost.serialization.parser.GhostJsonConstants.OPEN_ARR_INT
import com.ghost.serialization.parser.GhostJsonConstants.OPEN_OBJ_INT
import com.ghost.serialization.parser.GhostJsonConstants.QUOTE_INT
import com.ghost.serialization.parser.GhostJsonConstants.RESULT_NONE
import com.ghost.serialization.parser.GhostJsonConstants.SPACE_INT
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
     */
    fun peek(
        source: GhostSource,
        rawData: ByteArray,
        isStreaming: Boolean,
        start: Int,
        limit: Int,
        key: ByteString
    ): String? {
        return if (isStreaming) {
            peekInternal(source, start, limit, key) { pos -> source[pos] }
        } else {
            peekInternal(source, start, limit, key) { pos -> rawData[pos].toInt() and BYTE_MASK }
        }
    }

    @Suppress("CascadeIf")
    @PublishedApi
    internal inline fun peekInternal(
        source: GhostSource,
        start: Int,
        limit: Int,
        key: ByteString,
        crossinline getByte: (Int) -> Int
    ): String? {
        // 1. Skip leading whitespaces to find the start of the object
        var position = skipWhitespaceAndExpect(
            start, limit,
            OPEN_OBJ_INT,
            getByte
        )

        if (position == -1) return null
        if (position >= limit) return null

        val scanLimit = (position + maxDiscriminatorPeekDistance)
            .coerceAtMost(limit)

        val keySize = key.size

        // 2. Scan for the key
        while (position < scanLimit) {
            val byte = getByte(position)

            if (byte == QUOTE_INT) {
                val keyStart = position + 1

                // Fast content check: closing quote first (1 byte),
                // then content (N bytes)
                if (
                    keyStart + keySize < scanLimit &&
                    getByte(keyStart + keySize) == QUOTE_INT &&
                    source.contentEquals(keyStart, key)
                ) {
                    position = keyStart + keySize + 1

                    // 3. Fast-path colon check
                    val colonPosition = skipWhitespaceAndExpect(
                        position,
                        scanLimit,
                        COLON_INT,
                        getByte
                    )

                    if (colonPosition != -1) {
                        position = colonPosition
                        // 4. Fast-path opening quote check
                        val quotePosition = skipWhitespaceAndExpect(
                            position,
                            scanLimit,
                            QUOTE_INT,
                            getByte
                        )

                        if (quotePosition != -1) {
                            // 5. Extract value string
                            return extractValue(
                                source,
                                quotePosition,
                                scanLimit,
                                getByte
                            )
                        }
                    }
                    return null
                }

                // Not the key. Skip this string safely.
                position = skipString(keyStart, scanLimit, getByte)
            } else if (byte == OPEN_OBJ_INT || byte == OPEN_ARR_INT) {
                // nested object/array before the discriminator, give up.
                return null
            } else {
                position++
            }
        }
        return null
    }

    @PublishedApi
    internal inline fun skipWhitespaceAndExpect(
        start: Int,
        limit: Int,
        expected: Int,
        crossinline getByte: (Int) -> Int
    ): Int {
        val mask = GhostJsonConstants.WHITESPACE_MASK
        var position = start
        while (position < limit) {
            val byte = getByte(position)
            if (
                byte > SPACE_INT ||
                (mask and (BYTE_SHIFT_UNIT shl byte)) == RESULT_NONE
            ) {
                return if (byte == expected) position + 1 else -1
            }
            position++
        }
        return -1
    }

    @PublishedApi
    internal inline fun skipString(
        start: Int,
        limit: Int,
        crossinline getByte: (Int) -> Int
    ): Int {
        var position = start
        while (position < limit) {
            val skipByte = getByte(position)
            if (skipByte == QUOTE_INT) {
                return position + 1
            }
            if (skipByte == BACKSLASH_INT) {
                position++ // Skip escaped char
            }
            position++
        }
        return limit
    }

    @PublishedApi
    internal inline fun extractValue(
        source: GhostSource,
        start: Int,
        limit: Int,
        crossinline getByte: (Int) -> Int
    ): String? {
        var position = start
        val valueStart = position
        while (position < limit) {
            val valueByte = getByte(position)
            if (valueByte == QUOTE_INT) {
                return source.decodeToString(valueStart, position)
            }
            if (valueByte == BACKSLASH_INT) {
                // Escapes not supported in fast peek
                return null
            }
            position++
        }
        return null
    }
}

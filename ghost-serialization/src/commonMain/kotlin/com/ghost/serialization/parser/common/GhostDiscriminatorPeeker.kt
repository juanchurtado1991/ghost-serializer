package com.ghost.serialization.parser.common

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.GhostDiscriminatorPeeker.peek
import com.ghost.serialization.parser.common.GhostDiscriminatorPeeker.peekChars
import com.ghost.serialization.parser.common.GhostHeuristics.maxDiscriminatorPeekDistance
import com.ghost.serialization.parser.common.json.GhostJsonPathReconstruction
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.BACKSLASH_INT
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.BYTE_MASK
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants.BYTE_SHIFT_UNIT
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.CLOSE_ARR_INT
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.CLOSE_OBJ_INT
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.COLON_INT
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.COMMA_INT
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
 * deserializers — returns `null` if the key isn't found before the object closes or within
 * [maxDiscriminatorPeekDistance] bytes, or its value is not a string. The scan walks the object's
 * members (key, `:`, value) and skips each value whole, so a string value that happens to equal
 * the key name is never mistaken for the key, and it stops at the object's closing brace, so a
 * missing key never reads past the end of a stream (the streaming channel reads through
 * `byteOrEof`). Keys and values containing escapes are decoded before comparing/returning.
 * [peek] serves the byte-backed channels (flat/streaming) and takes `isStreaming` to pick a
 * direct `rawData` array read over the [GhostSource] indirection whenever a raw array is
 * available; [peekChars] is the char-channel counterpart for [GhostJsonStringReader], same scan
 * logic, no UTF-8 bridge.
 */
@InternalGhostApi
object GhostDiscriminatorPeeker {

    fun peek(
        source: GhostSource,
        rawData: ByteArray,
        isStreaming: Boolean,
        start: Int,
        limit: Int,
        key: ByteString,
    ): String? {
        val getByte = if (isStreaming) {
            { pos: Int -> source.byteOrEof(index = pos) }
        } else {
            { pos: Int -> rawData[pos].toInt() and BYTE_MASK }
        }
        return peekInternal(
            start = start,
            limit = limit,
            keySize = key.size,
            keyName = key.utf8(),
            getByte = getByte,
            matchesKey = { keyStart -> source.contentEquals(start = keyStart, expected = key) },
            decodeRange = { rangeStart, rangeEnd -> source.decodeToString(start = rangeStart, end = rangeEnd) },
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
            keyName = key,
            getByte = { pos -> chars[pos].code },
            matchesKey = { keyStart -> contentEqualsKey(chars = chars, keyStart = keyStart, key = key) },
            decodeRange = { rangeStart, rangeEnd -> rawData.substring(rangeStart, rangeEnd) },
        )
    }

    private fun contentEqualsKey(
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

    private inline fun containsBackslash(
        start: Int,
        end: Int,
        getByte: (Int) -> Int,
    ): Boolean {
        for (index in start until end) {
            if (getByte(index) == BACKSLASH_INT) {
                return true
            }
        }
        return false
    }

    private inline fun decodeStringBody(
        start: Int,
        end: Int,
        getByte: (Int) -> Int,
        decodeRange: (rangeStart: Int, rangeEnd: Int) -> String,
    ): String {
        val raw = decodeRange(start, end)
        val hasEscapes = containsBackslash(start = start, end = end, getByte = getByte)
        return if (hasEscapes) GhostJsonPathReconstruction.unescapeKey(raw = raw) else raw
    }

    private inline fun peekInternal(
        start: Int,
        limit: Int,
        keySize: Int,
        keyName: String,
        getByte: (Int) -> Int,
        matchesKey: (keyStart: Int) -> Boolean,
        decodeRange: (rangeStart: Int, rangeEnd: Int) -> String,
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

        while (true) {
            position = skipLeadingWhitespace(start = position, limit = scanLimit, getByte = getByte)
            if (position >= scanLimit) {
                return null
            }
            when (getByte(position)) {
                COMMA_INT -> {
                    position++
                    continue
                }

                QUOTE_INT -> Unit
                else -> return null
            }
            val keyStart = position + 1
            val keyEnd = skipString(start = keyStart, limit = scanLimit, getByte = getByte)
            if (keyEnd >= scanLimit) {
                return null
            }
            val keyLength = keyEnd - 1 - keyStart
            val isKeyMatch = if (keyLength == keySize) {
                matchesKey(keyStart)
            } else {
                keyLength > keySize &&
                    containsBackslash(start = keyStart, end = keyEnd - 1, getByte = getByte) &&
                    GhostJsonPathReconstruction.unescapeKey(raw = decodeRange(keyStart, keyEnd - 1)) == keyName
            }
            position = skipLeadingWhitespace(start = keyEnd, limit = scanLimit, getByte = getByte)
            if (position >= scanLimit || getByte(position) != COLON_INT) {
                return null
            }
            position = skipLeadingWhitespace(start = position + 1, limit = scanLimit, getByte = getByte)
            if (position >= scanLimit) {
                return null
            }
            val valueByte = getByte(position)
            if (isKeyMatch) {
                if (valueByte != QUOTE_INT) {
                    return null
                }
                val valueEnd = skipString(start = position + 1, limit = scanLimit, getByte = getByte)
                if (valueEnd >= scanLimit && getByte(valueEnd - 1) != QUOTE_INT) {
                    return null
                }
                return decodeStringBody(
                    start = position + 1,
                    end = valueEnd - 1,
                    getByte = getByte,
                    decodeRange = decodeRange
                )
            }
            position = when (valueByte) {
                QUOTE_INT -> skipString(start = position + 1, limit = scanLimit, getByte = getByte)
                OPEN_OBJ_INT, OPEN_ARR_INT -> skipBalanced(
                    start = position,
                    open = valueByte,
                    limit = scanLimit,
                    getByte = getByte,
                )

                else -> skipScalar(start = position, limit = scanLimit, getByte = getByte)
            }
        }
    }

    private inline fun skipBalanced(
        start: Int,
        open: Int,
        limit: Int,
        getByte: (Int) -> Int,
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

    private inline fun skipLeadingWhitespace(
        start: Int,
        limit: Int,
        getByte: (Int) -> Int,
    ): Int {
        var position = start
        while (position < limit) {
            val byte = getByte(position)
            val isNonWhitespace = byte > SPACE_INT || byte < 0 ||
                (WHITESPACE_MASK and (BYTE_SHIFT_UNIT shl byte)) == RESULT_NONE
            if (isNonWhitespace) return position
            position++
        }
        return position
    }

    private inline fun skipScalar(
        start: Int,
        limit: Int,
        getByte: (Int) -> Int,
    ): Int {
        var position = start
        while (position < limit) {
            val byte = getByte(position)
            val isScalarEnd = byte == COMMA_INT || byte == CLOSE_OBJ_INT || byte == CLOSE_ARR_INT ||
                byte <= SPACE_INT
            if (isScalarEnd) return position
            position++
        }
        return position
    }

    private inline fun skipString(
        start: Int,
        limit: Int,
        getByte: (Int) -> Int,
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
}

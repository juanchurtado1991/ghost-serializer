package com.ghost.serialization.parser.common.json

import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

/**
 * Rebuilds the JSONPath of a parse error by re-scanning the input prefix `[start, end)` instead of
 * tracking breadcrumbs on every key/value of the happy path. Used by the in-memory channels (bytes
 * and string), whose whole document stays addressable; the streaming channel releases consumed
 * segments and keeps eager tracking.
 *
 * The scan is purely structural (no schema): containers push/pop frames, object keys push a key
 * frame that stays until its value completes, and array elements advance the index — the same
 * frame lifecycle [GhostJsonPathTracker] follows when driven eagerly. A string or scalar still
 * open at `end` — or ending exactly there — means the error was raised while reading it, so its
 * owning key stays on the path. The container stack grows past [INITIAL_SCAN_DEPTH] because a
 * caller may raise the reader's `maxDepth`; this only runs once an error is already being thrown.
 */
internal object GhostJsonPathReconstruction {

    inline fun reconstruct(
        tracker: GhostJsonPathTracker,
        start: Int,
        end: Int,
        getByte: (Int) -> Int,
        decodeRange: (Int, Int) -> String
    ): GhostJsonPathTracker {
        tracker.reset()
        var containers = IntArray(INITIAL_SCAN_DEPTH)
        var depth = 0
        var index = start
        while (index < end) {
            val byte = getByte(index)
            val top = if (depth > 0) containers[depth - 1] else CONTAINER_NONE
            when (byte) {
                TOK.OPEN_OBJ_INT, TOK.OPEN_ARR_INT -> {
                    if (top == CONTAINER_ARRAY) tracker.enterArrayElement()
                    val isObject = byte == TOK.OPEN_OBJ_INT
                    if (isObject) tracker.pushObject() else tracker.pushArray()
                    if (depth == containers.size) {
                        containers = containers.copyOf(newSize = containers.size * SCAN_DEPTH_GROWTH)
                    }
                    containers[depth] = if (isObject) CONTAINER_OBJECT_KEY else CONTAINER_ARRAY
                    depth++
                    index++
                }
                TOK.CLOSE_OBJ_INT, TOK.CLOSE_ARR_INT -> {
                    if (byte == TOK.CLOSE_OBJ_INT) tracker.finishObjectValue() else tracker.finishArrayValue()
                    if (depth > 0) depth--
                    index++
                }
                TOK.COMMA_INT -> {
                    if (top == CONTAINER_OBJECT_VALUE) containers[depth - 1] = CONTAINER_OBJECT_KEY
                    index++
                }
                TOK.COLON_INT -> {
                    if (top == CONTAINER_OBJECT_KEY) containers[depth - 1] = CONTAINER_OBJECT_VALUE
                    index++
                }
                TOK.QUOTE_INT -> {
                    val closingQuote = findClosingQuote(
                        from = index + 1,
                        end = end,
                        getByte = getByte
                    )
                    val isKey = top == CONTAINER_OBJECT_KEY
                    if (!isKey && top == CONTAINER_ARRAY) tracker.enterArrayElement()
                    val endsAtError = closingQuote < 0 || closingQuote + 1 >= end
                    if (!isKey && endsAtError) return tracker
                    if (closingQuote < 0) return tracker
                    if (isKey) {
                        tracker.pushKey(name = unescapeKey(raw = decodeRange(index + 1, closingQuote)))
                    } else {
                        tracker.finishScalarValue()
                    }
                    index = closingQuote + 1
                }
                TOK.SPACE_INT, TOK.LF_INT, TOK.CR_INT, TOK.TAB_INT -> index++
                else -> {
                    if (top == CONTAINER_ARRAY) tracker.enterArrayElement()
                    val scalarEnd = findScalarEnd(
                        from = index,
                        end = end,
                        getByte = getByte
                    )
                    if (scalarEnd >= end) return tracker
                    tracker.finishScalarValue()
                    index = scalarEnd
                }
            }
        }
        return tracker
    }

    /** Resolves `\\x` and `\\uXXXX` escapes in a raw key; keys without a backslash are returned as-is. */
    @PublishedApi
    internal fun unescapeKey(
        raw: String
    ): String {
        if (raw.indexOf(BACKSLASH_CHAR) < 0) return raw
        val builder = StringBuilder(raw.length)
        var index = 0
        while (index < raw.length) {
            val char = raw[index]
            val hasUnicodeEscape = char == BACKSLASH_CHAR &&
                index + UNICODE_ESCAPE_LENGTH < raw.length &&
                raw[index + 1] == UNICODE_ESCAPE_MARKER
            when {
                hasUnicodeEscape -> {
                    val hex = raw.substring(index + 2, index + 2 + UNICODE_HEX_DIGITS)
                    builder.append(hex.toInt(radix = HEX_RADIX).toChar())
                    index += UNICODE_ESCAPE_LENGTH + 1
                }
                char == BACKSLASH_CHAR && index + 1 < raw.length -> {
                    builder.append(raw[index + 1])
                    index += ESCAPE_PAIR_LENGTH
                }
                else -> {
                    builder.append(char)
                    index++
                }
            }
        }
        return builder.toString()
    }

    /** Index of the unescaped closing quote at or after [from], or `-1` if [end] comes first. */
    @PublishedApi
    internal inline fun findClosingQuote(
        from: Int,
        end: Int,
        getByte: (Int) -> Int
    ): Int {
        var index = from
        while (index < end) {
            when (getByte(index)) {
                TOK.BACKSLASH_INT -> index += ESCAPE_PAIR_LENGTH
                TOK.QUOTE_INT -> return index
                else -> index++
            }
        }
        return -1
    }

    @PublishedApi
    internal inline fun findScalarEnd(
        from: Int,
        end: Int,
        getByte: (Int) -> Int
    ): Int {
        var index = from
        while (index < end) {
            val isDelimiter = when (getByte(index)) {
                TOK.COMMA_INT, TOK.CLOSE_OBJ_INT, TOK.CLOSE_ARR_INT,
                TOK.SPACE_INT, TOK.LF_INT, TOK.CR_INT, TOK.TAB_INT -> true
                else -> false
            }
            if (isDelimiter) return index
            index++
        }
        return end
    }

    @PublishedApi
    internal const val CONTAINER_NONE = 0

    @PublishedApi
    internal const val CONTAINER_OBJECT_KEY = 1

    @PublishedApi
    internal const val CONTAINER_OBJECT_VALUE = 2

    @PublishedApi
    internal const val CONTAINER_ARRAY = 3

    @PublishedApi
    internal const val ESCAPE_PAIR_LENGTH = 2

    @PublishedApi
    internal const val INITIAL_SCAN_DEPTH = 256

    @PublishedApi
    internal const val SCAN_DEPTH_GROWTH = 2

    private const val BACKSLASH_CHAR = '\\'
    private const val HEX_RADIX = 16
    private const val UNICODE_ESCAPE_LENGTH = 5
    private const val UNICODE_ESCAPE_MARKER = 'u'
    private const val UNICODE_HEX_DIGITS = 4
}

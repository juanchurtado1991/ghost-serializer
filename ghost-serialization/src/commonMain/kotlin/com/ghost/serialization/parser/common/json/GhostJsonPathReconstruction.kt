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
        limit: Int,
        getByte: (Int) -> Int,
        decodeRange: (Int, Int) -> String
    ): GhostJsonPathTracker {
        tracker.reset()
        var states = IntArray(INITIAL_SCAN_DEPTH)
        var depth = 0
        var index = start
        while (index < end) {
            val byte = getByte(index)
            val state = if (depth > 0) states[depth - 1] else STATE_ROOT
            when (byte) {
                TOK.SPACE_INT, TOK.LF_INT, TOK.CR_INT, TOK.TAB_INT -> {
                    index++
                    continue
                }
                TOK.OPEN_OBJ_INT, TOK.OPEN_ARR_INT -> {
                    if (!acceptsValue(state = state)) break
                    if (isArrayState(state = state)) tracker.enterArrayElement()
                    if (depth > 0) states[depth - 1] = stateAfterValue(state = state)
                    val isObject = byte == TOK.OPEN_OBJ_INT
                    if (isObject) tracker.pushObject() else tracker.pushArray()
                    if (depth == states.size) {
                        states = states.copyOf(newSize = states.size * SCAN_DEPTH_GROWTH)
                    }
                    states[depth] = if (isObject) STATE_OBJECT_START else STATE_ARRAY_START
                    depth++
                    index++
                }
                TOK.CLOSE_OBJ_INT, TOK.CLOSE_ARR_INT -> {
                    if (!acceptsClose(state = state, closeByte = byte)) break
                    if (byte == TOK.CLOSE_OBJ_INT) tracker.finishObjectValue() else tracker.finishArrayValue()
                    depth--
                    index++
                }
                TOK.COMMA_INT -> {
                    if (!acceptsComma(state = state)) break
                    states[depth - 1] = stateAfterComma(state = state)
                    index++
                }
                TOK.COLON_INT -> {
                    if (state != STATE_OBJECT_COLON) break
                    states[depth - 1] = STATE_OBJECT_VALUE
                    index++
                }
                TOK.QUOTE_INT -> {
                    val isKey = acceptsKey(state = state)
                    if (!isKey && !acceptsValue(state = state)) break
                    if (isArrayState(state = state)) tracker.enterArrayElement()
                    val closingQuote = findClosingQuote(
                        from = index + 1,
                        end = end,
                        getByte = getByte
                    )
                    val endsAtError = closingQuote < 0 || closingQuote + 1 >= end
                    if (!isKey && endsAtError) return tracker
                    if (closingQuote < 0) return tracker
                    if (isKey) {
                        tracker.pushKey(name = unescapeKey(raw = decodeRange(index + 1, closingQuote)))
                        states[depth - 1] = STATE_OBJECT_COLON
                    } else {
                        tracker.finishScalarValue()
                        if (depth > 0) states[depth - 1] = stateAfterValue(state = state)
                    }
                    index = closingQuote + 1
                }
                else -> {
                    if (!acceptsValue(state = state)) break
                    if (isArrayState(state = state)) tracker.enterArrayElement()
                    val scalarEnd = findScalarEnd(
                        from = index,
                        end = end,
                        getByte = getByte
                    )
                    if (scalarEnd >= end) return tracker
                    tracker.finishScalarValue()
                    if (depth > 0) states[depth - 1] = stateAfterValue(state = state)
                    index = scalarEnd
                }
            }
        }
        val pendingState = if (depth > 0) states[depth - 1] else STATE_ROOT
        val tokenAtStop = if (index < limit) getByte(index) else TOK.CLOSE_ARR_INT
        if (isElementPending(state = pendingState, tokenAtStop = tokenAtStop)) tracker.enterArrayElement()
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

    /** Whether a value (scalar, string, `{`, `[`) may start in [state]. Array elements need a comma
     * between them (`readList` rejects a missing one), unlike object keys — see [acceptsKey]. */
    @PublishedApi
    internal fun acceptsValue(
        state: Int
    ): Boolean = state == STATE_ROOT ||
        state == STATE_OBJECT_VALUE ||
        state == STATE_ARRAY_START ||
        state == STATE_ARRAY_AFTER_COMMA

    /** Whether a key may start in [state]; a key right after a value (missing comma) is accepted,
     * as the readers do outside strict mode. */
    @PublishedApi
    internal fun acceptsKey(
        state: Int
    ): Boolean = state == STATE_OBJECT_START ||
        state == STATE_OBJECT_AFTER_COMMA ||
        state == STATE_OBJECT_AFTER_VALUE

    @PublishedApi
    internal fun acceptsClose(
        state: Int,
        closeByte: Int
    ): Boolean = if (closeByte == TOK.CLOSE_OBJ_INT) {
        state == STATE_OBJECT_START || state == STATE_OBJECT_AFTER_VALUE
    } else {
        state == STATE_ARRAY_START || state == STATE_ARRAY_AFTER_VALUE
    }

    @PublishedApi
    internal fun acceptsComma(
        state: Int
    ): Boolean = state == STATE_OBJECT_AFTER_VALUE || state == STATE_ARRAY_AFTER_VALUE

    @PublishedApi
    internal fun isArrayState(
        state: Int
    ): Boolean = state == STATE_ARRAY_START ||
        state == STATE_ARRAY_AFTER_COMMA ||
        state == STATE_ARRAY_AFTER_VALUE

    /** An element the readers would already have entered (`[` or `,` consumed, value not started):
     * readList/hasNext advance the index before parsing it, so the path must name it too. A `]`
     * right after `[` is an empty array, not a pending element. */
    @PublishedApi
    internal fun isElementPending(
        state: Int,
        tokenAtStop: Int
    ): Boolean = state == STATE_ARRAY_AFTER_COMMA ||
        (state == STATE_ARRAY_START && tokenAtStop != TOK.CLOSE_ARR_INT)

    @PublishedApi
    internal fun stateAfterComma(
        state: Int
    ): Int = if (state == STATE_OBJECT_AFTER_VALUE) STATE_OBJECT_AFTER_COMMA else STATE_ARRAY_AFTER_COMMA

    @PublishedApi
    internal fun stateAfterValue(
        state: Int
    ): Int = if (isArrayState(state = state)) STATE_ARRAY_AFTER_VALUE else STATE_OBJECT_AFTER_VALUE

    @PublishedApi
    internal const val STATE_ROOT = 0

    @PublishedApi
    internal const val STATE_OBJECT_START = 1

    @PublishedApi
    internal const val STATE_OBJECT_COLON = 2

    @PublishedApi
    internal const val STATE_OBJECT_VALUE = 3

    @PublishedApi
    internal const val STATE_OBJECT_AFTER_VALUE = 4

    @PublishedApi
    internal const val STATE_OBJECT_AFTER_COMMA = 5

    @PublishedApi
    internal const val STATE_ARRAY_START = 6

    @PublishedApi
    internal const val STATE_ARRAY_AFTER_VALUE = 7

    @PublishedApi
    internal const val STATE_ARRAY_AFTER_COMMA = 8

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

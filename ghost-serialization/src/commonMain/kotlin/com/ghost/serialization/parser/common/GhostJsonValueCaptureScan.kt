@file:Suppress("NOTHING_TO_INLINE")

package com.ghost.serialization.parser.common

import com.ghost.serialization.parser.common.GhostJsonConstants as C

/**
 * Pure byte/char-unit scan of one complete JSON value.
 *
 * Shared by flat (UTF-8), streaming (UTF-8 via [getUnit]), and string (UTF-16 `.code`) readers.
 * Opening structural brackets and string quotes are consumed by the caller before this returns
 * past the value; number scan stops at structural delimiters or whitespace.
 *
 * @param startPosition Index of the first unit of the value (already past leading whitespace).
 * @param limit Exclusive end of the readable range.
 * @param getUnit Reads an unsigned byte or char code at an absolute index.
 * @return Position immediately after the scanned value.
 */
internal inline fun captureJsonValueScan(
    startPosition: Int,
    limit: Int,
    getUnit: (Int) -> Int,
): Int {
    var position = startPosition
    val first = getUnit(position++)
    when (first) {
        C.OPEN_OBJ_INT, C.OPEN_ARR_INT -> {
            var depth = 1
            while (position < limit && depth > 0) {
                when (getUnit(position++)) {
                    C.QUOTE_INT -> position = captureSkipQuotedStringScan(position, limit, getUnit)
                    C.OPEN_OBJ_INT, C.OPEN_ARR_INT -> depth++
                    C.CLOSE_OBJ_INT, C.CLOSE_ARR_INT -> depth--
                }
            }
        }

        C.QUOTE_INT -> position = captureSkipQuotedStringScan(position, limit, getUnit)
        C.TRUE_CHAR_INT -> position += C.TRUE_TAIL_LEN
        C.FALSE_CHAR_INT -> position += C.FALSE_TAIL_LEN
        C.NULL_CHAR_INT -> position += C.NULL_TAIL_LEN
        else -> {
            while (position < limit) {
                val tokenByte = getUnit(position)
                if (tokenByte == C.COMMA_INT ||
                    tokenByte == C.CLOSE_OBJ_INT ||
                    tokenByte == C.CLOSE_ARR_INT ||
                    tokenByte <= C.SPACE_INT
                ) {
                    break
                }
                position++
            }
        }
    }
    return position
}

/**
 * Advances past the content of a quoted JSON string whose opening '"' has already been consumed.
 * Handles backslash escapes by skipping the escaped unit.
 *
 * @return Position immediately after the closing quote, or [limit] if unterminated.
 */
internal inline fun captureSkipQuotedStringScan(
    startPosition: Int,
    limit: Int,
    getUnit: (Int) -> Int,
): Int {
    var position = startPosition
    while (position < limit) {
        when (getUnit(position++)) {
            C.QUOTE_INT -> return position
            C.BACKSLASH_INT -> if (position < limit) position++
        }
    }
    return position
}

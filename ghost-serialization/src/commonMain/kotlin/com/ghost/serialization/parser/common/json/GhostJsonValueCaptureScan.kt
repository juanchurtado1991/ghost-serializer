@file:Suppress("NOTHING_TO_INLINE")

package com.ghost.serialization.parser.common.json

import com.ghost.serialization.parser.common.isDigit
import com.ghost.serialization.parser.common.isExponentMarker
import com.ghost.serialization.parser.common.constants.GhostJsonErrorMessages as EM
import com.ghost.serialization.parser.common.constants.GhostJsonNumericLimits as NUM
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants as SCN
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

private const val CONTAINER_LEVEL_WORDS = (NUM.MAX_DEPTH + Long.SIZE_BITS - 1) / Long.SIZE_BITS
private const val LEVEL_BIT_MASK = Long.SIZE_BITS - 1
private const val LEVEL_WORD_SHIFT = 6

/**
 * Byte/char-unit scan of one complete JSON value for `RawJson` capture, shared by the flat
 * (UTF-8), streaming (UTF-8) and string (UTF-16 `.code`) readers.
 *
 * The scan validates the value's grammar (structure, literals, number syntax, string escapes)
 * without decoding it, so a captured `RawJson` is always well-formed JSON and truncated input
 * fails with the reader's own error instead of reading past the end. Every read goes through
 * `captureUnitAt`, which yields [SCN.MATCH_END] at or past [limit]; [getUnit] may also return
 * [SCN.MATCH_END] itself, which the streaming reader does when the stream ends before [limit].
 * The scan is iterative, with the object/array kind of each open level kept in a bitset of
 * [NUM.MAX_DEPTH] levels, because an inline kernel cannot recurse. Escapes are checked for
 * syntax only: surrogate pairing is applied when the captured value is decoded. String scans
 * start after the opening quote and return the index after the closing one.
 *
 * [onError] receives the message and the position of the offending unit.
 */
internal inline fun captureJsonValueScan(
    startPosition: Int,
    limit: Int,
    getUnit: (Int) -> Int,
    onError: (message: String, position: Int) -> Nothing,
): Int {
    var objectLevels: LongArray? = null
    var depth = 0
    var position = startPosition
    var expectsValue = true
    while (true) {
        if (expectsValue) {
            position = skipCaptureWhitespace(
                position = position,
                limit = limit,
                getUnit = getUnit
            )
            val unit = requireCaptureUnit(
                position = position,
                limit = limit,
                getUnit = getUnit,
                onError = onError
            )
            val isContainerStart = unit == TOK.OPEN_OBJ_INT || unit == TOK.OPEN_ARR_INT
            if (isContainerStart) {
                if (depth >= NUM.MAX_DEPTH) {
                    onError(EM.ERR_DEPTH_EXCEEDED, position)
                }
                val levels = objectLevels ?: LongArray(size = CONTAINER_LEVEL_WORDS).also { objectLevels = it }
                val isObject = unit == TOK.OPEN_OBJ_INT
                setCaptureLevelKind(
                    levels = levels,
                    depth = depth,
                    isObject = isObject
                )
                depth++
                position = skipCaptureWhitespace(
                    position = position + 1,
                    limit = limit,
                    getUnit = getUnit
                )
                val closing = if (isObject) TOK.CLOSE_OBJ_INT else TOK.CLOSE_ARR_INT
                val isEmptyContainer = captureUnitAt(
                    position = position,
                    limit = limit,
                    getUnit = getUnit
                ) == closing
                if (isEmptyContainer) {
                    position++
                    depth--
                    expectsValue = false
                } else if (isObject) {
                    position = scanCaptureObjectKey(
                        position = position,
                        limit = limit,
                        getUnit = getUnit,
                        onError = onError
                    )
                }
                continue
            }
            position = scanCaptureScalar(
                position = position,
                unit = unit,
                limit = limit,
                getUnit = getUnit,
                onError = onError
            )
            expectsValue = false
        }
        if (depth == 0) {
            return position
        }
        position = skipCaptureWhitespace(
            position = position,
            limit = limit,
            getUnit = getUnit
        )
        val unit = requireCaptureUnit(
            position = position,
            limit = limit,
            getUnit = getUnit,
            onError = onError
        )
        val isObject = isCaptureObjectLevel(
            levels = objectLevels!!,
            depth = depth - 1
        )
        when (unit) {
            TOK.COMMA_INT -> {
                position++
                if (isObject) {
                    position = scanCaptureObjectKey(
                        position = skipCaptureWhitespace(
                            position = position,
                            limit = limit,
                            getUnit = getUnit
                        ),
                        limit = limit,
                        getUnit = getUnit,
                        onError = onError
                    )
                }
                expectsValue = true
            }

            if (isObject) TOK.CLOSE_OBJ_INT else TOK.CLOSE_ARR_INT -> {
                position++
                depth--
            }

            else -> onError(
                if (isObject) EM.ERR_EXPECTED_COMMA_OR_CLOSE_OBJ else EM.ERR_EXPECTED_COMMA_OR_CLOSE_ARR,
                position
            )
        }
    }
}

private inline fun captureUnitAt(
    position: Int,
    limit: Int,
    getUnit: (Int) -> Int,
): Int = if (position < limit) getUnit(position) else SCN.MATCH_END

private inline fun isCaptureDigitAt(
    position: Int,
    limit: Int,
    getUnit: (Int) -> Int,
): Boolean {
    val unit = captureUnitAt(
        position = position,
        limit = limit,
        getUnit = getUnit
    )
    return unit != SCN.MATCH_END && isDigit(byteCode = unit)
}

private inline fun isCaptureObjectLevel(
    levels: LongArray,
    depth: Int
): Boolean = (levels[depth ushr LEVEL_WORD_SHIFT] ushr (depth and LEVEL_BIT_MASK)) and 1L != 0L

private inline fun matchCaptureLiteral(
    position: Int,
    limit: Int,
    literal: String,
    getUnit: (Int) -> Int,
    onError: (message: String, position: Int) -> Nothing,
): Int {
    for (offset in literal.indices) {
        val index = position + offset
        val unit = requireCaptureUnit(
            position = index,
            limit = limit,
            getUnit = getUnit,
            onError = onError
        )
        if (unit != literal[offset].code) {
            onError(EM.ERR_EXPECTED_LITERAL + literal, index)
        }
    }
    return position + literal.length
}

private inline fun requireCaptureUnit(
    position: Int,
    limit: Int,
    getUnit: (Int) -> Int,
    onError: (message: String, position: Int) -> Nothing,
): Int {
    val unit = captureUnitAt(
        position = position,
        limit = limit,
        getUnit = getUnit
    )
    if (unit == SCN.MATCH_END) {
        onError(EM.ERR_UNEXPECTED_EOF, position)
    }
    return unit
}

private inline fun scanCaptureDigits(
    position: Int,
    limit: Int,
    missingDigitsMessage: String,
    getUnit: (Int) -> Int,
    onError: (message: String, position: Int) -> Nothing,
): Int {
    var current = position
    while (
        isCaptureDigitAt(
            position = current,
            limit = limit,
            getUnit = getUnit
        )
    ) {
        current++
    }
    if (current == position) {
        onError(missingDigitsMessage, position)
    }
    return current
}

private inline fun scanCaptureEscape(
    position: Int,
    limit: Int,
    getUnit: (Int) -> Int,
    onError: (message: String, position: Int) -> Nothing,
): Int {
    val escaped = requireCaptureUnit(
        position = position,
        limit = limit,
        getUnit = getUnit,
        onError = onError
    )
    return when (escaped) {
        TOK.QUOTE_INT, TOK.BACKSLASH_INT, TOK.SLASH_INT,
        TOK.B_BYTE_INT, TOK.F_BYTE_INT, TOK.N_BYTE_INT, TOK.R_BYTE_INT, TOK.T_BYTE_INT -> position + 1

        TOK.UNICODE_PREFIX_U_INT -> {
            for (offset in 1..TOK.UNICODE_HEX_LENGTH) {
                val index = position + offset
                val unit = requireCaptureUnit(
                    position = index,
                    limit = limit,
                    getUnit = getUnit,
                    onError = onError
                )
                val isHexDigit = unit < TOK.HEX_LUT.size && TOK.HEX_LUT[unit] >= 0
                if (!isHexDigit) {
                    onError(EM.ERR_INVALID_UNICODE_AT + index, index)
                }
            }
            position + 1 + TOK.UNICODE_HEX_LENGTH
        }

        else -> onError(EM.ERR_INVALID_ESCAPE, position)
    }
}

private inline fun scanCaptureNumber(
    position: Int,
    unit: Int,
    limit: Int,
    getUnit: (Int) -> Int,
    onError: (message: String, position: Int) -> Nothing,
): Int {
    val integerStart = if (unit == TOK.MINUS_INT) position + 1 else position
    var current = scanCaptureDigits(
        position = integerStart,
        limit = limit,
        missingDigitsMessage = if (integerStart == position) EM.ERR_EXPECTED_JSON_VALUE else EM.ERR_ISOLATED_MINUS,
        getUnit = getUnit,
        onError = onError
    )
    val hasLeadingZero = getUnit(integerStart) == TOK.ZERO_INT && current - integerStart > 1
    if (hasLeadingZero) {
        onError(EM.ERR_LEADING_ZEROS, integerStart)
    }
    val hasFraction = captureUnitAt(
        position = current,
        limit = limit,
        getUnit = getUnit
    ) == TOK.DOT_INT
    if (hasFraction) {
        current = scanCaptureDigits(
            position = current + 1,
            limit = limit,
            missingDigitsMessage = EM.ERR_EXPECTED_DECIMAL_DIGITS,
            getUnit = getUnit,
            onError = onError
        )
    }
    val hasExponent = isExponentMarker(
        markerByte = captureUnitAt(
            position = current,
            limit = limit,
            getUnit = getUnit
        )
    )
    if (hasExponent) {
        current++
        val exponentSign = captureUnitAt(
            position = current,
            limit = limit,
            getUnit = getUnit
        )
        if (exponentSign == TOK.PLUS_INT || exponentSign == TOK.MINUS_INT) {
            current++
        }
        current = scanCaptureDigits(
            position = current,
            limit = limit,
            missingDigitsMessage = EM.ERR_EXPECTED_EXPONENT_DIGITS,
            getUnit = getUnit,
            onError = onError
        )
    }
    return current
}

private inline fun scanCaptureObjectKey(
    position: Int,
    limit: Int,
    getUnit: (Int) -> Int,
    onError: (message: String, position: Int) -> Nothing,
): Int {
    val unit = requireCaptureUnit(
        position = position,
        limit = limit,
        getUnit = getUnit,
        onError = onError
    )
    if (unit != TOK.QUOTE_INT) {
        onError(EM.ERR_EXPECTED_KEY + unit.toChar(), position)
    }
    val afterKey = skipCaptureWhitespace(
        position = scanCaptureString(
            position = position + 1,
            limit = limit,
            getUnit = getUnit,
            onError = onError
        ),
        limit = limit,
        getUnit = getUnit
    )
    val separator = requireCaptureUnit(
        position = afterKey,
        limit = limit,
        getUnit = getUnit,
        onError = onError
    )
    if (separator != TOK.COLON_INT) {
        onError(EM.ERR_EXPECTED_COLON, afterKey)
    }
    return afterKey + 1
}

private inline fun scanCaptureScalar(
    position: Int,
    unit: Int,
    limit: Int,
    getUnit: (Int) -> Int,
    onError: (message: String, position: Int) -> Nothing,
): Int = when (unit) {
    TOK.QUOTE_INT -> scanCaptureString(
        position = position + 1,
        limit = limit,
        getUnit = getUnit,
        onError = onError
    )

    TOK.TRUE_CHAR_INT -> matchCaptureLiteral(
        position = position,
        limit = limit,
        literal = TOK.LITERAL_TRUE,
        getUnit = getUnit,
        onError = onError
    )

    TOK.FALSE_CHAR_INT -> matchCaptureLiteral(
        position = position,
        limit = limit,
        literal = TOK.LITERAL_FALSE,
        getUnit = getUnit,
        onError = onError
    )

    TOK.NULL_CHAR_INT -> matchCaptureLiteral(
        position = position,
        limit = limit,
        literal = TOK.LITERAL_NULL,
        getUnit = getUnit,
        onError = onError
    )

    else -> scanCaptureNumber(
        position = position,
        unit = unit,
        limit = limit,
        getUnit = getUnit,
        onError = onError
    )
}

private inline fun scanCaptureString(
    position: Int,
    limit: Int,
    getUnit: (Int) -> Int,
    onError: (message: String, position: Int) -> Nothing,
): Int {
    var current = position
    while (true) {
        val unit = requireCaptureUnit(
            position = current,
            limit = limit,
            getUnit = getUnit,
            onError = onError
        )
        current = when (unit) {
            TOK.QUOTE_INT -> return current + 1
            TOK.BACKSLASH_INT -> scanCaptureEscape(
                position = current + 1,
                limit = limit,
                getUnit = getUnit,
                onError = onError
            )

            else -> current + 1
        }
    }
}

private inline fun setCaptureLevelKind(
    levels: LongArray,
    depth: Int,
    isObject: Boolean
) {
    val word = depth ushr LEVEL_WORD_SHIFT
    val bit = 1L shl (depth and LEVEL_BIT_MASK)
    levels[word] = if (isObject) levels[word] or bit else levels[word] and bit.inv()
}

private inline fun skipCaptureWhitespace(
    position: Int,
    limit: Int,
    getUnit: (Int) -> Int,
): Int {
    var current = position
    while (true) {
        val unit = captureUnitAt(
            position = current,
            limit = limit,
            getUnit = getUnit
        )
        val isWhitespace = unit == TOK.SPACE_INT || unit == TOK.LF_INT || unit == TOK.CR_INT || unit == TOK.TAB_INT
        if (!isWhitespace) {
            return current
        }
        current++
    }
}

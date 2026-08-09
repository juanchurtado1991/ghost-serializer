@file:Suppress("NOTHING_TO_INLINE")

package com.ghost.serialization.parser.common

import okio.ByteString
import com.ghost.serialization.parser.common.GhostJsonConstants as C

/**
 * Shared structure / comma kernels for streaming and string JSON readers.
 *
 * Reader-specific state (depth, comma masks, peek/skip) is supplied via inlined
 * adapters so each call site stays monomorphic after inlining.
 */

internal inline fun beginObjectCore(
    nextNonWhitespace: () -> Int,
    getDepth: () -> Int,
    setDepth: (Int) -> Unit,
    maxDepth: Int,
    getNeedsCommaMask: () -> Long,
    setNeedsCommaMask: (Long) -> Unit,
    getCommaConsumedMask: () -> Long,
    setCommaConsumedMask: (Long) -> Unit,
    setPredictedFieldIndex: (Int) -> Unit,
    throwError: (String) -> Nothing,
) {
    if (nextNonWhitespace() != C.OPEN_OBJ_INT) {
        throwError(C.ERR_EXPECTED_BEGIN_OBJ)
    }
    setPredictedFieldIndex(C.FIELD_PREDICTION_START)
    enterCollectionDepthCore(
        getDepth = getDepth,
        setDepth = setDepth,
        maxDepth = maxDepth,
        getNeedsCommaMask = getNeedsCommaMask,
        setNeedsCommaMask = setNeedsCommaMask,
        getCommaConsumedMask = getCommaConsumedMask,
        setCommaConsumedMask = setCommaConsumedMask,
        throwError = throwError,
    )
}

internal inline fun beginArrayCore(
    nextNonWhitespace: () -> Int,
    getDepth: () -> Int,
    setDepth: (Int) -> Unit,
    maxDepth: Int,
    getNeedsCommaMask: () -> Long,
    setNeedsCommaMask: (Long) -> Unit,
    getCommaConsumedMask: () -> Long,
    setCommaConsumedMask: (Long) -> Unit,
    throwError: (String) -> Nothing,
) {
    if (nextNonWhitespace() != C.OPEN_ARR_INT) {
        throwError(C.ERR_EXPECTED_BEGIN_ARR)
    }
    enterCollectionDepthCore(
        getDepth = getDepth,
        setDepth = setDepth,
        maxDepth = maxDepth,
        getNeedsCommaMask = getNeedsCommaMask,
        setNeedsCommaMask = setNeedsCommaMask,
        getCommaConsumedMask = getCommaConsumedMask,
        setCommaConsumedMask = setCommaConsumedMask,
        throwError = throwError,
    )
}

internal inline fun endObjectCore(
    nextNonWhitespace: () -> Int,
    getDepth: () -> Int,
    setDepth: (Int) -> Unit,
    throwError: (String) -> Nothing,
) {
    if (nextNonWhitespace() != C.CLOSE_OBJ_INT) {
        throwError(C.ERR_EXPECTED_END_OBJ)
    }
    val depth = getDepth()
    if (depth > 0) {
        setDepth(depth - 1)
    }
}

internal inline fun endArrayCore(
    nextNonWhitespace: () -> Int,
    getDepth: () -> Int,
    setDepth: (Int) -> Unit,
    throwError: (String) -> Nothing,
) {
    if (nextNonWhitespace() != C.CLOSE_ARR_INT) {
        throwError(C.ERR_EXPECTED_END_ARR)
    }
    val depth = getDepth()
    if (depth > 0) {
        setDepth(depth - 1)
    }
}

internal inline fun consumeKeySeparatorCore(
    nextNonWhitespace: () -> Int,
    throwError: (String) -> Nothing,
) {
    if (nextNonWhitespace() != C.COLON_INT) {
        throwError(C.ERR_EXPECTED_COLON)
    }
}

/**
 * Shared depth increment + comma-mask clear used by [beginObjectCore] / [beginArrayCore].
 */
private inline fun enterCollectionDepthCore(
    getDepth: () -> Int,
    setDepth: (Int) -> Unit,
    maxDepth: Int,
    getNeedsCommaMask: () -> Long,
    setNeedsCommaMask: (Long) -> Unit,
    getCommaConsumedMask: () -> Long,
    setCommaConsumedMask: (Long) -> Unit,
    throwError: (String) -> Nothing,
) {
    val depth = getDepth() + 1
    setDepth(depth)
    if (depth > maxDepth) {
        throwError(C.ERR_DEPTH_EXCEEDED)
    }
    if (depth < C.MAX_BITMASK_DEPTH) {
        val bit = C.BITMASK_UNIT shl depth
        setNeedsCommaMask(getNeedsCommaMask() and bit.inv())
        setCommaConsumedMask(getCommaConsumedMask() and bit.inv())
    }
}

/**
 * Comma / trailing-comma validation for `hasNext`.
 *
 * @return `false` when the container is closed or input ended; `true` when another element follows.
 */
internal inline fun hasNextCore(
    peekNextToken: () -> Int,
    strictMode: Boolean,
    depth: Int,
    getNeedsCommaMask: () -> Long,
    setNeedsCommaMask: (Long) -> Unit,
    getCommaConsumedMask: () -> Long,
    setCommaConsumedMask: (Long) -> Unit,
    internalSkip: (Int) -> Unit,
    throwError: (String) -> Nothing,
): Boolean {
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
        if ((getCommaConsumedMask() and bit) != C.RESULT_NONE) {
            if (token == C.COMMA_INT) {
                setCommaConsumedMask(getCommaConsumedMask() and bit.inv())
                setNeedsCommaMask(getNeedsCommaMask() or bit)
            }
        }
        if ((getCommaConsumedMask() and bit) != C.RESULT_NONE) {
            setCommaConsumedMask(getCommaConsumedMask() and bit.inv())
            setNeedsCommaMask(getNeedsCommaMask() or bit)
        } else {
            val required = (getNeedsCommaMask() and bit) != C.RESULT_NONE
            if (token == C.COMMA_INT) {
                if (!required) {
                    throwError(C.ERR_UNEXPECTED_COMMA)
                }
                internalSkip(1)
                val next = peekNextToken()
                if (next == C.CLOSE_ARR_INT || next == C.CLOSE_OBJ_INT) {
                    throwError(C.ERR_TRAILING_COMMA)
                }
                setCommaConsumedMask(getCommaConsumedMask() or bit)
                setNeedsCommaMask(getNeedsCommaMask() and bit.inv())
            } else {
                if (required) throwError(C.ERR_EXPECTED_COMMA)
                setNeedsCommaMask(getNeedsCommaMask() or bit)
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

/**
 * Comma preamble shared by [selectValidateCommasCore] callers (selectName / selectString).
 *
 * @return The token after any consumed comma (original [token] if no comma).
 */
internal inline fun selectValidateCommasCore(
    token: Int,
    consumeSeparator: Boolean,
    strictMode: Boolean,
    depth: Int,
    getNeedsCommaMask: () -> Long,
    setNeedsCommaMask: (Long) -> Unit,
    getCommaConsumedMask: () -> Long,
    setCommaConsumedMask: (Long) -> Unit,
    peekNextToken: () -> Int,
    internalSkip: (Int) -> Unit,
    throwError: (String) -> Nothing,
): Int {
    var currentToken = token
    if (strictMode && depth < C.MAX_BITMASK_DEPTH) {
        val bit = C.BITMASK_UNIT shl depth
        if ((getCommaConsumedMask() and bit) != C.RESULT_NONE) {
            setCommaConsumedMask(getCommaConsumedMask() and bit.inv())
            setNeedsCommaMask(getNeedsCommaMask() or bit)
        } else {
            val required = (getNeedsCommaMask() and bit) != C.RESULT_NONE
            if (currentToken == C.COMMA_INT) {
                if (!required) {
                    throwError(C.ERR_UNEXPECTED_COMMA)
                }
                internalSkip(1)
                currentToken = peekNextToken()
                if (currentToken == C.CLOSE_OBJ_INT) {
                    throwError(C.ERR_TRAILING_COMMA)
                }
                setCommaConsumedMask(getCommaConsumedMask() and bit.inv())
                setNeedsCommaMask(getNeedsCommaMask() or bit)
            } else {
                if (required && consumeSeparator) {
                    throwError(C.ERR_EXPECTED_COMMA_OR_CLOSE_OBJ)
                }
                setNeedsCommaMask(getNeedsCommaMask() or bit)
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

internal inline fun consumeArraySeparatorCore(
    strictMode: Boolean,
    depth: Int,
    getNeedsCommaMask: () -> Long,
    setNeedsCommaMask: (Long) -> Unit,
    getCommaConsumedMask: () -> Long,
    setCommaConsumedMask: (Long) -> Unit,
    peekNextToken: () -> Int,
    internalSkip: (Int) -> Unit,
    throwError: (String) -> Nothing,
) {
    if (strictMode && depth < C.MAX_BITMASK_DEPTH) {
        val bit = C.BITMASK_UNIT shl depth
        if ((getCommaConsumedMask() and bit) != C.RESULT_NONE) {
            setCommaConsumedMask(getCommaConsumedMask() and bit.inv())
            setNeedsCommaMask(getNeedsCommaMask() or bit)
            return
        }
        val token = peekNextToken()
        val required = (getNeedsCommaMask() and bit) != C.RESULT_NONE
        if (token == C.COMMA_INT) {
            internalSkip(1)
            val next = peekNextToken()
            if (next == C.CLOSE_ARR_INT || next == C.CLOSE_OBJ_INT) {
                throwError(C.ERR_TRAILING_COMMA)
            }
            setCommaConsumedMask(getCommaConsumedMask() or bit)
        } else if (required) {
            if (token != C.CLOSE_ARR_INT && token != C.CLOSE_OBJ_INT) {
                throwError(C.ERR_EXPECTED_COMMA_OR_CLOSE_ARR)
            }
        } else {
            if (token != C.CLOSE_ARR_INT && token != C.CLOSE_OBJ_INT) {
                throwError(C.ERR_EXPECTED_COMMA_OR_CLOSE_ARR)
            }
        }
        setNeedsCommaMask(getNeedsCommaMask() or bit)
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

/**
 * Comma preamble for `nextKey`.
 *
 * @return `false` when the object is closed (`}` peeked); `true` when a key follows.
 */
internal inline fun nextKeyCommaPreambleCore(
    peekNextToken: () -> Int,
    strictMode: Boolean,
    depth: Int,
    getNeedsCommaMask: () -> Long,
    setNeedsCommaMask: (Long) -> Unit,
    getCommaConsumedMask: () -> Long,
    setCommaConsumedMask: (Long) -> Unit,
    internalSkip: (Int) -> Unit,
    throwError: (String) -> Nothing,
): Boolean {
    val token = peekNextToken()
    if (token == C.CLOSE_OBJ_INT) {
        return false
    }
    if (strictMode && depth < C.MAX_BITMASK_DEPTH) {
        val bit = C.BITMASK_UNIT shl depth
        if ((getCommaConsumedMask() and bit) != C.RESULT_NONE) {
            setCommaConsumedMask(getCommaConsumedMask() and bit.inv())
            setNeedsCommaMask(getNeedsCommaMask() or bit)
        } else {
            val required = (getNeedsCommaMask() and bit) != C.RESULT_NONE
            if (token == C.COMMA_INT) {
                if (!required) {
                    throwError(C.ERR_UNEXPECTED_COMMA)
                }
                internalSkip(1)
                if (peekNextToken() == C.CLOSE_OBJ_INT) {
                    throwError(C.ERR_TRAILING_COMMA)
                }
                setNeedsCommaMask(getNeedsCommaMask() or bit)
            } else {
                if (required) {
                    throwError(C.ERR_EXPECTED_COMMA_OR_CLOSE_OBJ)
                }
                setNeedsCommaMask(getNeedsCommaMask() or bit)
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
    return true
}

internal inline fun nextBooleanCore(
    peekNextToken: () -> Int,
    skipAndValidateLiteral: (ByteString) -> Unit,
    coerceBooleans: Boolean,
    internalSkip: (Int) -> Unit,
    matchCoerceBooleanBytes: () -> Boolean,
    throwError: (String) -> Nothing,
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
            return matchCoerceBooleanBytes()
        }
    }
    throwError(C.ERR_EXPECTED_BOOLEAN)
}

/**
 * Shared null-or-value preamble for `next*OrNull` wrappers.
 */
internal inline fun <T> nextOrNullCore(
    peekNextToken: () -> Int,
    consumeNull: () -> Unit,
    readValue: () -> T,
): T? {
    if (peekNextToken() == C.NULL_CHAR_INT) {
        consumeNull()
        return null
    }
    return readValue()
}

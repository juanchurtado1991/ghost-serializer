@file:Suppress("NOTHING_TO_INLINE")

package com.ghost.serialization.parser.common.json

import com.ghost.serialization.parser.common.constants.GhostJsonErrorMessages as EM
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants as SCN
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

/**
 * Shared comma / trailing-comma validation kernels for streaming and string JSON readers.
 *
 * [hasNextCore], [selectValidateCommasCore], [consumeArraySeparatorCore] and
 * [nextKeyCommaPreambleCore] all drive the same `needsCommaMask`/`commaConsumedMask` bit-per-depth
 * state machine, one per call site (array iteration, object field select, array separator, object
 * key iteration). They are kept as separate kernels rather than unified behind one generic
 * function — their terminal-token checks and return contracts differ per caller, and this
 * project's hot-path DRY exception requires measured `benchmarkTwitter` evidence before merging
 * kernels like these, not just textual similarity.
 *
 * Reader-specific state (depth, comma masks, peek/skip) is supplied via inlined adapters so each
 * call site stays monomorphic after inlining.
 */

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
    if (strictMode && depth < SCN.MAX_BITMASK_DEPTH) {
        val bit = SCN.BITMASK_UNIT shl depth
        if ((getCommaConsumedMask() and bit) != SCN.RESULT_NONE) {
            setCommaConsumedMask(getCommaConsumedMask() and bit.inv())
            setNeedsCommaMask(getNeedsCommaMask() or bit)
        } else {
            val required = (getNeedsCommaMask() and bit) != SCN.RESULT_NONE
            if (currentToken == TOK.COMMA_INT) {
                if (!required) {
                    throwError(EM.ERR_UNEXPECTED_COMMA)
                }
                internalSkip(1)
                currentToken = peekNextToken()
                if (currentToken == TOK.CLOSE_OBJ_INT) {
                    throwError(EM.ERR_TRAILING_COMMA)
                }
                setCommaConsumedMask(getCommaConsumedMask() and bit.inv())
                setNeedsCommaMask(getNeedsCommaMask() or bit)
            } else {
                if (required && consumeSeparator) {
                    throwError(EM.ERR_EXPECTED_COMMA_OR_CLOSE_OBJ)
                }
                setNeedsCommaMask(getNeedsCommaMask() or bit)
            }
        }
    } else {
        if (currentToken == TOK.COMMA_INT) {
            internalSkip(1)
            currentToken = peekNextToken()
            if (currentToken == TOK.CLOSE_OBJ_INT) {
                throwError(EM.ERR_TRAILING_COMMA)
            }
        }
    }
    return currentToken
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
    val isContainerCloseOrEnd = token == TOK.CLOSE_ARR_INT || token == TOK.CLOSE_OBJ_INT || token == SCN.MATCH_END
    if (isContainerCloseOrEnd) {
        return false
    }
    if (strictMode && depth < SCN.MAX_BITMASK_DEPTH) {
        val bit = SCN.BITMASK_UNIT shl depth
        if ((getCommaConsumedMask() and bit) != SCN.RESULT_NONE) {
            if (token == TOK.COMMA_INT) {
                setCommaConsumedMask(getCommaConsumedMask() and bit.inv())
                setNeedsCommaMask(getNeedsCommaMask() or bit)
            }
        }
        if ((getCommaConsumedMask() and bit) != SCN.RESULT_NONE) {
            setCommaConsumedMask(getCommaConsumedMask() and bit.inv())
            setNeedsCommaMask(getNeedsCommaMask() or bit)
        } else {
            val required = (getNeedsCommaMask() and bit) != SCN.RESULT_NONE
            if (token == TOK.COMMA_INT) {
                if (!required) {
                    throwError(EM.ERR_UNEXPECTED_COMMA)
                }
                internalSkip(1)
                val next = peekNextToken()
                if (next == TOK.CLOSE_ARR_INT || next == TOK.CLOSE_OBJ_INT) {
                    throwError(EM.ERR_TRAILING_COMMA)
                }
                setCommaConsumedMask(getCommaConsumedMask() or bit)
                setNeedsCommaMask(getNeedsCommaMask() and bit.inv())
            } else {
                if (required) throwError(EM.ERR_EXPECTED_COMMA)
                setNeedsCommaMask(getNeedsCommaMask() or bit)
            }
        }
    } else {
        if (token == TOK.COMMA_INT) {
            internalSkip(1)
            val next = peekNextToken()
            if (next == TOK.CLOSE_ARR_INT || next == TOK.CLOSE_OBJ_INT) {
                throwError(EM.ERR_TRAILING_COMMA)
            }
        }
    }
    return true
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
    if (token == TOK.CLOSE_OBJ_INT) {
        return false
    }
    if (strictMode && depth < SCN.MAX_BITMASK_DEPTH) {
        val bit = SCN.BITMASK_UNIT shl depth
        if ((getCommaConsumedMask() and bit) != SCN.RESULT_NONE) {
            setCommaConsumedMask(getCommaConsumedMask() and bit.inv())
            setNeedsCommaMask(getNeedsCommaMask() or bit)
        } else {
            val required = (getNeedsCommaMask() and bit) != SCN.RESULT_NONE
            if (token == TOK.COMMA_INT) {
                if (!required) {
                    throwError(EM.ERR_UNEXPECTED_COMMA)
                }
                internalSkip(1)
                if (peekNextToken() == TOK.CLOSE_OBJ_INT) {
                    throwError(EM.ERR_TRAILING_COMMA)
                }
                setNeedsCommaMask(getNeedsCommaMask() or bit)
            } else {
                if (required) {
                    throwError(EM.ERR_EXPECTED_COMMA_OR_CLOSE_OBJ)
                }
                setNeedsCommaMask(getNeedsCommaMask() or bit)
            }
        }
    } else {
        if (token == TOK.COMMA_INT) {
            internalSkip(1)
            if (peekNextToken() == TOK.CLOSE_OBJ_INT) {
                throwError(EM.ERR_TRAILING_COMMA)
            }
        }
    }
    return true
}

/**
 * Comma preamble for array elements between `nextX` calls.
 *
 * The `required`/`!required` cases below both validate the same closing tokens, so unlike
 * [nextKeyCommaPreambleCore] there is no behavioral branch on `required` here (see the merged
 * validation below) — this matches the pre-existing behavior, not a change to it.
 */
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
    if (strictMode && depth < SCN.MAX_BITMASK_DEPTH) {
        val bit = SCN.BITMASK_UNIT shl depth
        if ((getCommaConsumedMask() and bit) != SCN.RESULT_NONE) {
            setCommaConsumedMask(getCommaConsumedMask() and bit.inv())
            setNeedsCommaMask(getNeedsCommaMask() or bit)
            return
        }
        val token = peekNextToken()
        if (token == TOK.COMMA_INT) {
            internalSkip(1)
            val next = peekNextToken()
            if (next == TOK.CLOSE_ARR_INT || next == TOK.CLOSE_OBJ_INT) {
                throwError(EM.ERR_TRAILING_COMMA)
            }
            setCommaConsumedMask(getCommaConsumedMask() or bit)
        } else if (token != TOK.CLOSE_ARR_INT && token != TOK.CLOSE_OBJ_INT) {
            throwError(EM.ERR_EXPECTED_COMMA_OR_CLOSE_ARR)
        }
        setNeedsCommaMask(getNeedsCommaMask() or bit)
    } else {
        val token = peekNextToken()
        if (token == TOK.COMMA_INT) {
            internalSkip(1)
            if (peekNextToken() == TOK.CLOSE_ARR_INT) {
                throwError(EM.ERR_TRAILING_COMMA)
            }
        }
    }
}

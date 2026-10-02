@file:Suppress("NOTHING_TO_INLINE")

package com.ghost.serialization.parser.common.json

import okio.ByteString
import com.ghost.serialization.parser.common.constants.GhostJsonErrorMessages as EM
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants as WR

/**
 * Shared boolean-coercion and null-wrapping kernels for streaming and string JSON readers.
 *
 * Reader-specific state is supplied via inlined adapters so each call site stays monomorphic
 * after inlining.
 */

internal inline fun nextBooleanCore(
    peekNextToken: () -> Int,
    skipAndValidateLiteral: (ByteString) -> Unit,
    coerceBooleans: Boolean,
    internalSkip: (Int) -> Unit,
    matchCoerceBooleanBytes: () -> Boolean,
    throwError: (String) -> Nothing,
): Boolean {
    val token = peekNextToken()
    if (token == TOK.TRUE_CHAR_INT) {
        skipAndValidateLiteral(WR.TRUE_BS)
        return true
    }
    if (token == TOK.FALSE_CHAR_INT) {
        skipAndValidateLiteral(WR.FALSE_BS)
        return false
    }
    if (coerceBooleans) {
        if (token == TOK.ONE_INT) {
            internalSkip(1)
            return true
        }
        if (token == TOK.ZERO_INT) {
            internalSkip(1)
            return false
        }
        if (token == TOK.QUOTE_INT) {
            return matchCoerceBooleanBytes()
        }
    }
    throwError(EM.ERR_EXPECTED_BOOLEAN)
}

/** Shared null-or-value preamble for `next*OrNull` wrappers. */
internal inline fun <T> nextOrNullCore(
    peekNextToken: () -> Int,
    consumeNull: () -> Unit,
    readValue: () -> T,
): T? {
    if (peekNextToken() == TOK.NULL_CHAR_INT) {
        consumeNull()
        return null
    }
    return readValue()
}

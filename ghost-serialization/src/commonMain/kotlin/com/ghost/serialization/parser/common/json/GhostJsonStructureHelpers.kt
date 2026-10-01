@file:Suppress("NOTHING_TO_INLINE")

package com.ghost.serialization.parser.common.json

import okio.ByteString
import com.ghost.serialization.parser.common.constants.GhostJsonErrorMessages as EM
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants as SCN
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants as WR

/**
 * Shared structural kernels (begin/end object/array, `:` separator, generic skip) for
 * streaming and string JSON readers.
 *
 * Reader-specific state (depth, comma masks, peek/skip) is supplied via inlined
 * adapters so each call site stays monomorphic after inlining.
 */

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
    if (nextNonWhitespace() != TOK.OPEN_ARR_INT) {
        throwError(EM.ERR_EXPECTED_BEGIN_ARR)
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
    if (nextNonWhitespace() != TOK.OPEN_OBJ_INT) {
        throwError(EM.ERR_EXPECTED_BEGIN_OBJ)
    }
    setPredictedFieldIndex(SCN.FIELD_PREDICTION_START)
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

internal inline fun consumeKeySeparatorCore(
    nextNonWhitespace: () -> Int,
    throwError: (String) -> Nothing,
) {
    if (nextNonWhitespace() != TOK.COLON_INT) {
        throwError(EM.ERR_EXPECTED_COLON)
    }
}

internal inline fun endArrayCore(
    nextNonWhitespace: () -> Int,
    getDepth: () -> Int,
    setDepth: (Int) -> Unit,
    throwError: (String) -> Nothing,
) {
    if (nextNonWhitespace() != TOK.CLOSE_ARR_INT) {
        throwError(EM.ERR_EXPECTED_END_ARR)
    }
    val depth = getDepth()
    if (depth > 0) {
        setDepth(depth - 1)
    }
}

internal inline fun endObjectCore(
    nextNonWhitespace: () -> Int,
    getDepth: () -> Int,
    setDepth: (Int) -> Unit,
    throwError: (String) -> Nothing,
) {
    if (nextNonWhitespace() != TOK.CLOSE_OBJ_INT) {
        throwError(EM.ERR_EXPECTED_END_OBJ)
    }
    val depth = getDepth()
    if (depth > 0) {
        setDepth(depth - 1)
    }
}

/**
 * Shared skip-value orchestration for streaming and string JSON readers.
 *
 * Structural recursion goes through `skipValue` so each reader keeps a single
 * entry point; adapters stay monomorphic after inlining into that entry point.
 */
internal inline fun skipValueCore(
    peekNextToken: () -> Int,
    beginObject: () -> Unit,
    endObject: () -> Unit,
    beginArray: () -> Unit,
    endArray: () -> Unit,
    hasNext: () -> Boolean,
    skipQuotedString: () -> Unit,
    consumeKeySeparator: () -> Unit,
    skipValue: () -> Unit,
    skipAndValidateLiteral: (ByteString) -> Unit,
    skipNumber: () -> Unit,
    throwError: (String) -> Nothing,
) {
    val token = peekNextToken()
    when (token) {
        TOK.OPEN_OBJ_INT -> {
            beginObject()
            while (hasNext()) {
                if (peekNextToken() != TOK.QUOTE_INT) {
                    throwError(EM.ERR_EXPECTED_KEY)
                }
                skipQuotedString()
                consumeKeySeparator()
                skipValue()
            }
            endObject()
        }

        TOK.OPEN_ARR_INT -> {
            beginArray()
            while (hasNext()) {
                skipValue()
            }
            endArray()
        }

        TOK.QUOTE_INT -> {
            skipQuotedString()
        }

        TOK.TRUE_CHAR_INT -> {
            skipAndValidateLiteral(WR.TRUE_BS)
        }

        TOK.FALSE_CHAR_INT -> {
            skipAndValidateLiteral(WR.FALSE_BS)
        }

        TOK.NULL_CHAR_INT -> {
            skipAndValidateLiteral(WR.NULL_BS)
        }

        else -> {
            skipNumber()
        }
    }
}

/** Shared depth increment + comma-mask clear used by [beginObjectCore] / [beginArrayCore]. */
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
        throwError(EM.ERR_DEPTH_EXCEEDED)
    }
    if (depth < SCN.MAX_BITMASK_DEPTH) {
        val bit = SCN.BITMASK_UNIT shl depth
        setNeedsCommaMask(getNeedsCommaMask() and bit.inv())
        setCommaConsumedMask(getCommaConsumedMask() and bit.inv())
    }
}

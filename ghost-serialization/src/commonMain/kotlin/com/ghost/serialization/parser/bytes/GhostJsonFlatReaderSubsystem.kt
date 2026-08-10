@file:Suppress("NOTHING_TO_INLINE")
@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser.bytes

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.JsonReaderOptions
import com.ghost.serialization.parser.common.computeKeyHashCore
import com.ghost.serialization.parser.common.findClosingQuoteImpl
import com.ghost.serialization.parser.common.handleSelectNoMatchCore
import com.ghost.serialization.parser.common.selectValidateCommasCore
import com.ghost.serialization.parser.common.GhostJsonConstants as C

/**
 * Low-level select parser helper that hashes and matches against [JsonReaderOptions] fields.
 *
 * Mechanics:
 * 1. Checks for trailing comma conditions and finds the start of the quoted string/key.
 * 2. Optimistic in-order predicted-key compare via [ghostReadLong8] (byte-flat fast path).
 * 3. Falls back to closing-quote scan + perfect-hash dispatch + [verifyKeyMatch].
 * 4. Consumes the trailing colon `:` if [consumeSeparator] is enabled.
 *
 * @return The matched options index, `-1` on object closing, or [C.MATCH_NONE] if not found.
 */
internal fun GhostJsonFlatReader.internalSelect(
    options: JsonReaderOptions,
    consumeSeparator: Boolean,
): Int {
    var token = peekNextToken()
    if (token == C.CLOSE_OBJ_INT) {
        return -1
    }

    token = selectValidateCommas(token, consumeSeparator)

    if (token != C.QUOTE_INT) {
        throwExpectedKeyOrStringError(consumeSeparator)
    }
    val start = position + 1
    val localData = rawData
    val lim = limit

    // Optimistic in-order field match: most objects list fields in declaration order,
    // so compare the key directly against the predicted candidate in a single pass. On a
    // hit this avoids the separate closing-quote scan, hash, and verify passes entirely.
    val predicted = predictedFieldIndex
    val rawBytes = options.rawBytes
    if (predicted < rawBytes.size) {
        val candidate = rawBytes[predicted]
        val candLen = candidate.size
        val keyEnd = start + candLen
        if (candLen > 0 && keyEnd < lim &&
            (localData[keyEnd].toInt() and C.BYTE_MASK) == C.QUOTE_INT
        ) {
            var i = 0
            // Compare LONG_BYTES at a time for longer field names when SWAR is enabled.
            // Comparing two ghostReadLong8 results is byte-order independent (equality is
            // symmetric). Wasm skips the wide compare (ghostUseSwarScans=false).
            if (ghostUseSwarScans) {
                while (i + C.LONG_BYTES <= candLen &&
                    ghostReadLong8(localData, start + i) == ghostReadLong8(candidate, i)
                ) {
                    i += C.LONG_BYTES
                }
            }
            while (i < candLen && localData[start + i] == candidate[i]) {
                i++
            }
            if (i == candLen) {
                predictedFieldIndex = predicted + 1
                val newPos = keyEnd + 1
                position = newPos
                nextTokenByte = C.RESET_TOKEN_BYTE
                if (consumeSeparator) {
                    if (newPos < lim && (localData[newPos].toInt() and C.BYTE_MASK) == C.COLON_INT) {
                        position = newPos + 1
                    } else {
                        consumeKeySeparator()
                    }
                }
                return predicted
            }
        }
    }

    val end = findClosingQuoteImpl(start, lim) {
        localData[it].toInt() and C.BYTE_MASK
    }

    if (end == -1) {
        throwUnterminatedStringError()
    }

    val length = end - start
    val key = computeKeyHash(start, length, options.hasCollisions)

    val hasIndex =
        ((key * options.multiplier + length) shr options.shift) and (options.dispatch.size - 1)
    val index = options.dispatch[hasIndex]

    if (index != C.MATCH_END) {
        if (verifyKeyMatch(start, length, options.rawBytes[index], consumeSeparator)) {
            predictedFieldIndex = index + 1
            return index
        }
    }

    return handleSelectNoMatch(start, end, consumeSeparator)
}

private fun GhostJsonFlatReader.selectValidateCommas(token: Int, consumeSeparator: Boolean): Int =
    selectValidateCommasCore(
        token = token,
        consumeSeparator = consumeSeparator,
        strictMode = strictMode,
        depth = depth,
        getNeedsCommaMask = { needsCommaMask },
        setNeedsCommaMask = { needsCommaMask = it },
        getCommaConsumedMask = { commaConsumedMask },
        setCommaConsumedMask = { commaConsumedMask = it },
        peekNextToken = { peekNextToken() },
        internalSkip = { internalSkip(it) },
        throwError = { throwError(it) },
    )

private fun GhostJsonFlatReader.handleSelectNoMatch(
    start: Int,
    end: Int,
    consumeSeparator: Boolean,
): Int =
    handleSelectNoMatchCore(
        start = start,
        end = end,
        consumeSeparator = consumeSeparator,
        strictMode = strictMode,
        limit = limit,
        getByte = { getByte(it) },
        setPosition = { position = it },
        setNextTokenByte = { nextTokenByte = it },
        consumeKeySeparator = { consumeKeySeparator() },
        decodeUnknownKey = { s, e -> source.decodeToString(s, e) },
        throwError = { throwError(it) },
    )

private fun GhostJsonFlatReader.throwExpectedKeyOrStringError(consumeSeparator: Boolean) {
    throwError(if (consumeSeparator) C.ERR_EXPECTED_KEY else C.ERR_EXPECTED_STRING)
}

private fun GhostJsonFlatReader.throwUnterminatedStringError() {
    throwError(C.UNTERMINATED_STRING_ERROR)
}

private fun GhostJsonFlatReader.computeKeyHash(start: Int, length: Int, hasCollisions: Boolean): Int =
    computeKeyHashCore(start, length, hasCollisions) { getByte(it) }

/**
 * Performs a fast comparison of the parsed string against expected bytes to verify matches.
 *
 * Byte-flat path: unrolled x4 over [rawData] plus optional colon consume. Kept here (not shared
 * with CharArray string select) so the predicted-key / [ghostReadLong8] sibling stays monomorphic.
 */
private inline fun GhostJsonFlatReader.verifyKeyMatch(
    start: Int,
    length: Int,
    expected: ByteArray,
    consumeSeparator: Boolean,
): Boolean {
    // Length is already guaranteed equal by the dispatch table (same hash slot).
    if (expected.size == length) {
        val localData = rawData
        var i = 0
        // Unrolled x4 for typical ASCII field name lengths (4–20 chars).
        while (i + 3 < length) {
            if (localData[start + i] != expected[i]) return false
            if (localData[start + i + 1] != expected[i + 1]) return false
            if (localData[start + i + 2] != expected[i + 2]) return false
            if (localData[start + i + 3] != expected[i + 3]) return false
            i += 4
        }
        while (i < length) {
            if (localData[start + i] != expected[i]) return false
            i++
        }
        val endPos = start + length
        val newPos = endPos + 1
        position = newPos
        nextTokenByte = C.RESET_TOKEN_BYTE
        if (consumeSeparator) {
            if (newPos < limit) {
                val colonToken = getByte(newPos)
                if (colonToken == C.COLON_INT) {
                    position = newPos + 1
                } else {
                    consumeKeySeparator()
                }
            } else {
                consumeKeySeparator()
            }
        }
        return true
    }
    return false
}

@file:Suppress("NOTHING_TO_INLINE")
@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser.bytes.extensions

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.bytes.ghostReadLong8
import com.ghost.serialization.parser.bytes.ghostSWARLengthMasks
import com.ghost.serialization.parser.common.json.JsonReaderOptions
import com.ghost.serialization.parser.common.json.computeKeyHashCore
import com.ghost.serialization.parser.common.findClosingQuoteImpl
import com.ghost.serialization.parser.common.json.handleSelectNoMatchCore
import com.ghost.serialization.parser.common.json.selectValidateCommasCore
import com.ghost.serialization.parser.common.constants.GhostJsonErrorMessages as EM
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants as SCN
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

/**
 * Perfect-hash field matcher against [JsonReaderOptions]. Tries the in-order predicted key via
 * [ghostReadLong8] first, then falls back to closing-quote scan + hash dispatch + [verifyKeyMatch].
 *
 * @return The matched options index, `-1` on object closing, or [SCN.MATCH_NONE] if not found.
 */
internal fun GhostJsonFlatReader.internalSelect(
    options: JsonReaderOptions,
    consumeSeparator: Boolean,
): Int {
    var token = peekNextToken()
    if (token == TOK.CLOSE_OBJ_INT) {
        return -1
    }

    token = selectValidateCommas(token = token, consumeSeparator = consumeSeparator)

    if (token != TOK.QUOTE_INT) {
        throwError(if (consumeSeparator) EM.ERR_EXPECTED_KEY else EM.ERR_EXPECTED_STRING)
    }
    val start = position + 1
    val localData = rawData
    val byteLimit = limit

    // Optimistic in-order field match: most objects list fields in declaration order,
    // so compare the key directly against the predicted candidate in a single pass. On a
    // hit this avoids the separate closing-quote scan, hash, and verify passes entirely.
    val predicted = predictedFieldIndex
    val rawBytes = options.rawBytes
    if (predicted < rawBytes.size) {
        val candidate = rawBytes[predicted]
        val candidateLength = candidate.size
        val keyEnd = start + candidateLength
        val hasClosingQuoteAfterKey = candidateLength > 0 &&
            keyEnd < byteLimit &&
            (localData[keyEnd].toInt() and TOK.BYTE_MASK) == TOK.QUOTE_INT
        if (hasClosingQuoteAfterKey) {
            // Most real field names are short (well under LONG_BYTES): a single masked
            // ghostReadLong8 read/compare beats the loop-then-scalar-tail path below, which
            // for a short candidateLength never enters its SWAR loop body at all. The masked
            // compare is byte-order independent for the same reason plain equality of two
            // ghostReadLong8 reads is: see ghostSWARLengthMasks's doc comment.
            val matched = if (candidateLength <= SCN.LONG_BYTES && start + SCN.LONG_BYTES <= localData.size) {
                val inputLong = ghostReadLong8(
                    data = localData,
                    index = start
                ) and ghostSWARLengthMasks[candidateLength]
                inputLong == ghostReadLong8(data = options.predictedKeyPadded[predicted], index = 0)
            } else {
                var matchedOffset = 0
                // Compare LONG_BYTES at a time for longer field names.
                // Comparing two ghostReadLong8 results is byte-order independent (equality is
                // symmetric).
                while (matchedOffset + SCN.LONG_BYTES <= candidateLength &&
                    ghostReadLong8(data = localData, index = start + matchedOffset) ==
                    ghostReadLong8(data = candidate, index = matchedOffset)
                ) {
                    matchedOffset += SCN.LONG_BYTES
                }
                while (matchedOffset < candidateLength &&
                    localData[start + matchedOffset] == candidate[matchedOffset]
                ) {
                    matchedOffset++
                }
                matchedOffset == candidateLength
            }
            if (matched) {
                predictedFieldIndex = predicted + 1
                val newPos = keyEnd + 1
                position = newPos
                nextTokenByte = SCN.RESET_TOKEN_BYTE
                if (consumeSeparator) {
                    val hasColonAfterKey = newPos < byteLimit &&
                        (localData[newPos].toInt() and TOK.BYTE_MASK) == TOK.COLON_INT
                    if (hasColonAfterKey) {
                        position = newPos + 1
                    } else {
                        consumeKeySeparator()
                    }
                }
                return predicted
            }
        }
    }

    val end = findClosingQuoteImpl(position = start, limit = byteLimit) {
        localData[it].toInt() and TOK.BYTE_MASK
    }

    if (end == -1) {
        throwError(EM.UNTERMINATED_STRING_ERROR)
    }

    val length = end - start
    val key = computeKeyHash(start = start, length = length, hasCollisions = options.hasCollisions)

    val hasIndex =
        ((key * options.multiplier + length) shr options.shift) and (options.dispatch.size - 1)
    val index = options.dispatch[hasIndex]

    if (index != SCN.MATCH_END) {
        if (verifyKeyMatch(
            start = start,
            length = length,
            expected = options.rawBytes[index],
            consumeSeparator = consumeSeparator
        )) {
            predictedFieldIndex = index + 1
            return index
        }
    }

    return handleSelectNoMatch(start = start, end = end, consumeSeparator = consumeSeparator)
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
        decodeUnknownKey = { s, e -> source.decodeToString(start = s, end = e) },
        throwError = { throwError(it) },
    )

private fun GhostJsonFlatReader.computeKeyHash(start: Int, length: Int, hasCollisions: Boolean): Int =
    computeKeyHashCore(start = start, length = length, hasCollisions = hasCollisions) { getByte(it) }

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
        var matchedOffset = 0
        // Unrolled x4 for typical ASCII field name lengths (4–20 chars).
        while (matchedOffset + 3 < length) {
            if (localData[start + matchedOffset] != expected[matchedOffset]) return false
            if (localData[start + matchedOffset + 1] != expected[matchedOffset + 1]) return false
            if (localData[start + matchedOffset + 2] != expected[matchedOffset + 2]) return false
            if (localData[start + matchedOffset + 3] != expected[matchedOffset + 3]) return false
            matchedOffset += 4
        }
        while (matchedOffset < length) {
            if (localData[start + matchedOffset] != expected[matchedOffset]) return false
            matchedOffset++
        }
        val endPos = start + length
        val newPos = endPos + 1
        position = newPos
        nextTokenByte = SCN.RESET_TOKEN_BYTE
        if (consumeSeparator) {
            if (newPos < limit) {
                val colonToken = getByte(newPos)
                if (colonToken == TOK.COLON_INT) {
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

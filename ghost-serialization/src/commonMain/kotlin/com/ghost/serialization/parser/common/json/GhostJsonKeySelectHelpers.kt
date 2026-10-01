@file:Suppress("NOTHING_TO_INLINE")

package com.ghost.serialization.parser.common.json

import com.ghost.serialization.parser.common.constants.GhostJsonErrorMessages as EM
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants as SCN
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

/**
 * Shared field-key hashing and unmatched-select-key handling for the perfect-hash dispatch used
 * by flat, streaming, and string JSON readers.
 *
 * Reader-specific state is supplied via inlined adapters so each call site stays monomorphic
 * after inlining.
 */

/**
 * Packs up to [SCN.KEY_HASH_WORD_BYTES] key bytes into a 32-bit hash (optional collision extension).
 *
 * [getByte] must return an unsigned byte as Int (0..255 / char code).
 */
internal inline fun computeKeyHashCore(
    start: Int,
    length: Int,
    hasCollisions: Boolean,
    getByte: (Int) -> Int,
): Int {
    var key = 0
    if (length >= SCN.KEY_HASH_WORD_BYTES) {
        val byte0 = getByte(start)
        val byte1 = getByte(start + 1)
        val byte2 = getByte(start + 2)
        val byte3 = getByte(start + 3)
        key = byte0 or
                (byte1 shl SCN.SHIFT_8) or
                (byte2 shl SCN.SHIFT_16) or
                (byte3 shl SCN.SHIFT_24)
        if (hasCollisions) {
            var ci = SCN.KEY_HASH_WORD_BYTES
            while (ci < length) {
                key = key * SCN.COLLISION_HASH_MULTIPLIER + getByte(start + ci); ci++
            }
        }
    } else {
        if (length >= 1) key = key or getByte(start)
        if (length >= 2) key = key or (getByte(start + 1) shl SCN.SHIFT_8)
        if (length >= 3) key = key or (getByte(start + 2) shl SCN.SHIFT_16)
    }
    return key
}

/** Advances past an unmatched select key; optionally consumes `:` or throws in strict mode. */
internal inline fun handleSelectNoMatchCore(
    start: Int,
    end: Int,
    consumeSeparator: Boolean,
    strictMode: Boolean,
    limit: Int,
    getByte: (Int) -> Int,
    setPosition: (Int) -> Unit,
    setNextTokenByte: (Int) -> Unit,
    consumeKeySeparator: () -> Unit,
    decodeUnknownKey: (Int, Int) -> String,
    throwError: (String) -> Nothing,
): Int {
    val newPos = end + 1
    setPosition(newPos)
    setNextTokenByte(SCN.MATCH_END)
    if (consumeSeparator) {
        if (newPos < limit && getByte(newPos) == TOK.COLON_INT) {
            setPosition(newPos + 1)
        } else {
            consumeKeySeparator()
        }
    } else if (strictMode) {
        val unknownKey = decodeUnknownKey(start, end)
        throwError("${EM.STRICT_MODE_UNKNOWN_FIELD}$unknownKey")
    }
    return SCN.MATCH_NONE
}

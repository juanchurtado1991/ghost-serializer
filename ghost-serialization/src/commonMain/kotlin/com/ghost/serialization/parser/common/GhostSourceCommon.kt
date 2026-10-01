@file:Suppress("NOTHING_TO_INLINE", "SameParameterValue")

package com.ghost.serialization.parser.common

import com.ghost.serialization.parser.bytes.ghostReadLong8
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants as SCN
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants as WR

/**
 * Whether [byte] is anything other than JSON whitespace (space, tab, LF, CR). Shared with
 * [com.ghost.serialization.parser.streaming.StreamingGhostSource] — a pure predicate on an
 * already-read byte, so sharing it (unlike the reader-level streaming/flat logic) is zero-cost:
 * being `inline`, it fuses into each call site identically either way.
 */
internal inline fun isNonWhitespace(byte: Int, whitespaceMask: Long): Boolean =
    byte > TOK.SPACE_INT || (whitespaceMask shr byte) and SCN.BYTE_SHIFT_UNIT == SCN.RESULT_NONE

/**
 * Whether [byte] is a quote, backslash, or control character requiring the slow escape path.
 * Shared with [com.ghost.serialization.parser.streaming.StreamingGhostSource] for the same
 * reason as [isNonWhitespace].
 */
internal inline fun isEscapeOrControlByte(byte: Int, escapeMasks: LongArray): Boolean =
    byte < TOK.ASCII_LIMIT &&
        (escapeMasks[byte shr SCN.BITMASK_SHIFT] shr (byte and SCN.BITMASK_INDEX_MASK)) and SCN.BITMASK_UNIT != SCN.RESULT_NONE

/**
 * Rolling-hash step shared by [scanStringImpl] and [rollingHashImpl] — the two MUST stay
 * bit-for-bit identical, so both call this instead of duplicating the formula.
 */
private inline fun accumulateHash(hash: Int, byte: Int): Int = (hash shl SCN.HASH_SHIFT) - hash + byte

internal inline fun findNextNonWhitespaceImpl(
    position: Int,
    limit: Int,
    getByte: (Int) -> Int
): Int {
    var currentPosition = position
    val whitespaceMask = SCN.WHITESPACE_MASK

    while (currentPosition + SCN.UNROLL_OFFSET_3 < limit) {
        val byte0 = getByte(currentPosition)
        if (isNonWhitespace(byte = byte0, whitespaceMask = whitespaceMask)) {
            return currentPosition
        }

        val byte1 = getByte(currentPosition + SCN.UNROLL_OFFSET_1)
        if (isNonWhitespace(byte = byte1, whitespaceMask = whitespaceMask)) {
            return currentPosition + SCN.UNROLL_OFFSET_1
        }

        val byte2 = getByte(currentPosition + SCN.UNROLL_OFFSET_2)
        if (isNonWhitespace(byte = byte2, whitespaceMask = whitespaceMask)) {
            return currentPosition + SCN.UNROLL_OFFSET_2
        }

        val byte3 = getByte(currentPosition + SCN.UNROLL_OFFSET_3)
        if (isNonWhitespace(byte = byte3, whitespaceMask = whitespaceMask)) {
            return currentPosition + SCN.UNROLL_OFFSET_3
        }

        currentPosition += SCN.UNROLL_STEP
    }
    while (currentPosition < limit) {
        val singleByte = getByte(currentPosition)
        if (isNonWhitespace(byte = singleByte, whitespaceMask = whitespaceMask)) {
            return currentPosition
        }
        currentPosition++
    }
    return SCN.MATCH_END
}

internal inline fun findClosingQuoteImpl(
    position: Int,
    limit: Int,
    getByte: (Int) -> Int
): Int {
    var currentPosition = position
    val escapeMasks = WR.ESCAPE_MASKS

    while (currentPosition + SCN.UNROLL_OFFSET_3 < limit) {
        val byte0 = getByte(currentPosition)
        if (isEscapeOrControlByte(byte = byte0, escapeMasks = escapeMasks)) {
            if (byte0 == TOK.QUOTE_INT) {
                return currentPosition
            }
            return SCN.MATCH_END
        }
        val byte1 = getByte(currentPosition + SCN.UNROLL_OFFSET_1)
        if (isEscapeOrControlByte(byte = byte1, escapeMasks = escapeMasks)) {
            if (byte1 == TOK.QUOTE_INT) {
                return currentPosition + SCN.UNROLL_OFFSET_1
            }
            return SCN.MATCH_END
        }
        val byte2 = getByte(currentPosition + SCN.UNROLL_OFFSET_2)
        if (isEscapeOrControlByte(byte = byte2, escapeMasks = escapeMasks)) {
            if (byte2 == TOK.QUOTE_INT) {
                return currentPosition + SCN.UNROLL_OFFSET_2
            }
            return SCN.MATCH_END
        }
        val byte3 = getByte(currentPosition + SCN.UNROLL_OFFSET_3)
        if (isEscapeOrControlByte(byte = byte3, escapeMasks = escapeMasks)) {
            if (byte3 == TOK.QUOTE_INT) {
                return currentPosition + SCN.UNROLL_OFFSET_3
            }
            return SCN.MATCH_END
        }
        currentPosition += SCN.UNROLL_STEP
    }

    while (currentPosition < limit) {
        val singleByte = getByte(currentPosition)
        if (isEscapeOrControlByte(byte = singleByte, escapeMasks = escapeMasks)) {
            if (singleByte == TOK.QUOTE_INT) {
                return currentPosition
            }
            return SCN.MATCH_END
        }
        currentPosition++
    }
    return SCN.MATCH_END
}

internal inline fun scanStringImpl(
    start: Int,
    limit: Int,
    getByte: (Int) -> Int
): Long {
    var currentPosition = start
    var accumulatedHash = 0
    var isPureAscii = true
    val escapeMasks = WR.ESCAPE_MASKS
    val asciiLimit = TOK.ASCII_LIMIT
    val matchEndLong = SCN.MATCH_END.toLong()

    while (currentPosition + SCN.UNROLL_OFFSET_3 < limit) {
        val byte0 = getByte(currentPosition)
        if (isEscapeOrControlByte(byte = byte0, escapeMasks = escapeMasks)) {
            if (byte0 == TOK.QUOTE_INT) {
                return SCN.packScanResult(
                    length = currentPosition - start,
                    hash = accumulatedHash,
                    is7Bit = isPureAscii
                )
            }
            return matchEndLong
        } else if (byte0 >= asciiLimit) {
            isPureAscii = false
        }

        accumulatedHash = accumulateHash(hash = accumulatedHash, byte = byte0)

        val byte1 = getByte(currentPosition + SCN.UNROLL_OFFSET_1)
        if (isEscapeOrControlByte(byte = byte1, escapeMasks = escapeMasks)) {
            if (byte1 == TOK.QUOTE_INT) {
                return SCN.packScanResult(
                    length = currentPosition + SCN.UNROLL_OFFSET_1 - start,
                    hash = accumulatedHash,
                    is7Bit = isPureAscii
                )
            }
            return matchEndLong
        } else if (byte1 >= asciiLimit) {
            isPureAscii = false
        }

        accumulatedHash = accumulateHash(hash = accumulatedHash, byte = byte1)

        val byte2 = getByte(currentPosition + SCN.UNROLL_OFFSET_2)
        if (isEscapeOrControlByte(byte = byte2, escapeMasks = escapeMasks)) {
            if (byte2 == TOK.QUOTE_INT) {
                return SCN.packScanResult(
                    length = currentPosition + SCN.UNROLL_OFFSET_2 - start,
                    hash = accumulatedHash,
                    is7Bit = isPureAscii
                )
            }
            return matchEndLong
        } else if (byte2 >= asciiLimit) {
            isPureAscii = false
        }

        accumulatedHash = accumulateHash(hash = accumulatedHash, byte = byte2)

        val byte3 = getByte(currentPosition + SCN.UNROLL_OFFSET_3)
        if (isEscapeOrControlByte(byte = byte3, escapeMasks = escapeMasks)) {
            if (byte3 == TOK.QUOTE_INT) {
                return SCN.packScanResult(
                    length = currentPosition + SCN.UNROLL_OFFSET_3 - start,
                    hash = accumulatedHash,
                    is7Bit = isPureAscii
                )
            }
            return matchEndLong
        } else if (byte3 >= asciiLimit) {
            isPureAscii = false
        }

        accumulatedHash = accumulateHash(hash = accumulatedHash, byte = byte3)
        currentPosition += SCN.UNROLL_STEP
    }

    while (currentPosition < limit) {
        val singleByte = getByte(currentPosition)
        if (isEscapeOrControlByte(byte = singleByte, escapeMasks = escapeMasks)) {
            if (singleByte == TOK.QUOTE_INT) {
                return SCN.packScanResult(
                    length = currentPosition - start,
                    hash = accumulatedHash,
                    is7Bit = isPureAscii
                )
            }
            return matchEndLong
        } else if (singleByte >= asciiLimit) {
            isPureAscii = false
        }

        accumulatedHash = accumulateHash(hash = accumulatedHash, byte = singleByte)
        currentPosition++
    }

    return matchEndLong
}

/** Branch-free "does any byte of [v] equal zero?" (McIlroy). Non-zero result ⇒ yes. */
@Suppress("NOTHING_TO_INLINE")
internal inline fun swarHasZeroByte(
    v: Long
): Long = (v - SCN.SWAR_ONES) and v.inv() and SCN.SWAR_HIGHS

/**
 * SWAR variant of [scanStringImpl] that does NOT accumulate the pool hash. Detects the closing
 * quote, escapes/control bytes, and non-ASCII content eight bytes at a time using branch-free
 * bit tricks, falling back to a byte scan only for the word that contains a boundary byte.
 *
 * Returns [SCN.packScanResult] with [SCN.SCAN_HASH_NONE] hash bits on success, or [SCN.MATCH_END]
 * as a Long when an escape or control byte requires the slow path. The rolling hash — needed only
 * for the small string pool — is recomputed cheaply by [rollingHashImpl] over short spans, so the
 * bulk of the byte volume (long, never-pooled values) is never hashed.
 */
internal fun scanStringSwarNoHash(
    data: ByteArray,
    start: Int,
    limit: Int
): Long {
    var cursor = start
    var isPureAscii = true
    val matchEndLong = SCN.MATCH_END.toLong()

    // SWAR fast path: consume LONG_BYTES windows with no quote, backslash, or control byte.
    while (cursor + SCN.LONG_BYTES <= limit) {
        val packedWindow = ghostReadLong8(
            data = data,
            index = cursor
        )
        val hasQuote = swarHasZeroByte(v = packedWindow xor SCN.SWAR_QUOTES)
        val hasBackslash = swarHasZeroByte(v = packedWindow xor SCN.SWAR_BACKSLASHES)
        // Bytes strictly below SPACE_INT (control chars); space itself is intentionally excluded.
        val hasControl =
            (packedWindow - SCN.SPACE_RUN_LONG) and packedWindow.inv() and SCN.SWAR_HIGHS
        if ((hasQuote or hasBackslash or hasControl) != SCN.RESULT_NONE) {
            break
        }
        if ((packedWindow and SCN.SWAR_HIGHS) != SCN.RESULT_NONE) {
            isPureAscii = false
        }
        cursor += SCN.LONG_BYTES
    }

    // Byte tail: boundary window + remainder.
    val escapeMasks = WR.ESCAPE_MASKS
    val asciiLimit = TOK.ASCII_LIMIT
    while (cursor < limit) {
        val tokenByte = data[cursor].toInt() and TOK.BYTE_MASK
        if (isEscapeOrControlByte(byte = tokenByte, escapeMasks = escapeMasks)) {
            if (tokenByte == TOK.QUOTE_INT) {
                return SCN.packScanResult(
                    length = cursor - start,
                    hash = SCN.SCAN_HASH_NONE,
                    is7Bit = isPureAscii
                )
            }
            return matchEndLong
        } else if (tokenByte >= asciiLimit) {
            isPureAscii = false
        }
        cursor++
    }
    return matchEndLong
}

/**
 * Recomputes the small-string-pool rolling hash over `[start, start+length)`. Must stay
 * bit-for-bit identical to the accumulation in [scanStringImpl] — both call [accumulateHash].
 */
internal fun rollingHashImpl(
    data: ByteArray,
    start: Int,
    length: Int
): Int {
    var accumulatedHash = SCN.SCAN_HASH_NONE
    var byteOffset = 0
    while (byteOffset < length) {
        accumulatedHash = accumulateHash(
            hash = accumulatedHash,
            byte = data[start + byteOffset].toInt() and TOK.BYTE_MASK
        )
        byteOffset++
    }
    return accumulatedHash
}

internal inline fun contentEqualsStringImpl(
    start: Int,
    length: Int,
    targetString: String,
    getByte: (Int) -> Int
): Boolean {
    if (targetString.length != length) return false
    var currentIndex = 0

    while (currentIndex + SCN.UNROLL_OFFSET_3 < length) {
        if (targetString[currentIndex].code != getByte(start + currentIndex)) {
            return false
        }
        if (targetString[currentIndex + SCN.UNROLL_OFFSET_1].code != getByte(start + currentIndex + SCN.UNROLL_OFFSET_1)) {
            return false
        }
        if (targetString[currentIndex + SCN.UNROLL_OFFSET_2].code != getByte(start + currentIndex + SCN.UNROLL_OFFSET_2)) {
            return false
        }
        if (targetString[currentIndex + SCN.UNROLL_OFFSET_3].code != getByte(start + currentIndex + SCN.UNROLL_OFFSET_3)) {
            return false
        }
        currentIndex += SCN.UNROLL_STEP
    }

    while (currentIndex < length) {
        if (targetString[currentIndex].code != getByte(start + currentIndex)) {
            return false
        }
        currentIndex++
    }

    return true
}

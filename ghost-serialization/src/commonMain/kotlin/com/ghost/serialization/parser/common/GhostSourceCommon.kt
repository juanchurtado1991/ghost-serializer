package com.ghost.serialization.parser.common

import com.ghost.serialization.parser.bytes.ghostReadLong8
import com.ghost.serialization.parser.bytes.ghostUseSwarScans
import com.ghost.serialization.parser.common.GhostJsonConstants.ASCII_LIMIT
import com.ghost.serialization.parser.common.GhostJsonConstants.BITMASK_INDEX_MASK
import com.ghost.serialization.parser.common.GhostJsonConstants.BITMASK_SHIFT
import com.ghost.serialization.parser.common.GhostJsonConstants.BITMASK_UNIT
import com.ghost.serialization.parser.common.GhostJsonConstants.BYTE_MASK
import com.ghost.serialization.parser.common.GhostJsonConstants.BYTE_SHIFT_UNIT
import com.ghost.serialization.parser.common.GhostJsonConstants.HASH_SHIFT
import com.ghost.serialization.parser.common.GhostJsonConstants.LONG_BYTES
import com.ghost.serialization.parser.common.GhostJsonConstants.MATCH_END
import com.ghost.serialization.parser.common.GhostJsonConstants.QUOTE_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.RESULT_NONE
import com.ghost.serialization.parser.common.GhostJsonConstants.SCAN_HASH_NONE
import com.ghost.serialization.parser.common.GhostJsonConstants.SPACE_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.SPACE_RUN_LONG
import com.ghost.serialization.parser.common.GhostJsonConstants.SWAR_BACKSLASHES
import com.ghost.serialization.parser.common.GhostJsonConstants.SWAR_HIGHS
import com.ghost.serialization.parser.common.GhostJsonConstants.SWAR_ONES
import com.ghost.serialization.parser.common.GhostJsonConstants.SWAR_QUOTES
import com.ghost.serialization.parser.common.GhostJsonConstants.WHITESPACE_MASK
import com.ghost.serialization.parser.common.GhostJsonConstants.packScanResult


private const val UNROLL_STEP = 4
private const val INDEX_OFFSET_1 = 1
private const val INDEX_OFFSET_2 = 2
private const val INDEX_OFFSET_3 = 3

internal inline fun findNextNonWhitespaceImpl(
    position: Int,
    limit: Int,
    getByte: (Int) -> Int
): Int {
    var currentPosition = position
    val whitespaceMask = WHITESPACE_MASK

    while (currentPosition + INDEX_OFFSET_3 < limit) {
        val byte0 = getByte(currentPosition)
        if (byte0 > SPACE_INT || (whitespaceMask shr byte0) and BYTE_SHIFT_UNIT == RESULT_NONE) {
            return currentPosition
        }

        val byte1 = getByte(currentPosition + INDEX_OFFSET_1)
        if (byte1 > SPACE_INT || (whitespaceMask shr byte1) and BYTE_SHIFT_UNIT == RESULT_NONE) {
            return currentPosition + INDEX_OFFSET_1
        }

        val byte2 = getByte(currentPosition + INDEX_OFFSET_2)
        if (byte2 > SPACE_INT || (whitespaceMask shr byte2) and BYTE_SHIFT_UNIT == RESULT_NONE) {
            return currentPosition + INDEX_OFFSET_2
        }

        val byte3 = getByte(currentPosition + INDEX_OFFSET_3)
        if (byte3 > SPACE_INT || (whitespaceMask shr byte3) and BYTE_SHIFT_UNIT == RESULT_NONE) {
            return currentPosition + INDEX_OFFSET_3
        }

        currentPosition += UNROLL_STEP
    }
    while (currentPosition < limit) {
        val singleByte = getByte(currentPosition)
        if (singleByte > SPACE_INT || (whitespaceMask shr singleByte) and BYTE_SHIFT_UNIT == RESULT_NONE) {
            return currentPosition
        }
        currentPosition++
    }
    return MATCH_END
}

internal inline fun findClosingQuoteImpl(
    position: Int,
    limit: Int,
    getByte: (Int) -> Int
): Int {
    var currentPosition = position
    val escapeMasks = GhostJsonConstants.ESCAPE_MASKS

    while (currentPosition + INDEX_OFFSET_3 < limit) {
        val byte0 = getByte(currentPosition)
        if (byte0 < ASCII_LIMIT &&
            ((escapeMasks[byte0 shr BITMASK_SHIFT] shr
                    (byte0 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != RESULT_NONE)
        ) {
            if (byte0 == QUOTE_INT) {
                return currentPosition
            }
            return MATCH_END
        }
        val byte1 = getByte(currentPosition + INDEX_OFFSET_1)
        if (byte1 < ASCII_LIMIT &&
            ((escapeMasks[byte1 shr BITMASK_SHIFT] shr
                    (byte1 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != RESULT_NONE)
        ) {
            if (byte1 == QUOTE_INT) {
                return currentPosition + INDEX_OFFSET_1
            }
            return MATCH_END
        }
        val byte2 = getByte(currentPosition + INDEX_OFFSET_2)
        if (byte2 < ASCII_LIMIT &&
            ((escapeMasks[byte2 shr BITMASK_SHIFT] shr
                    (byte2 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != RESULT_NONE)
        ) {
            if (byte2 == QUOTE_INT) {
                return currentPosition + INDEX_OFFSET_2
            }
            return MATCH_END
        }
        val byte3 = getByte(currentPosition + INDEX_OFFSET_3)
        if (byte3 < ASCII_LIMIT &&
            ((escapeMasks[byte3 shr BITMASK_SHIFT] shr
                    (byte3 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != RESULT_NONE)
        ) {
            if (byte3 == QUOTE_INT) {
                return currentPosition + INDEX_OFFSET_3
            }
            return MATCH_END
        }
        currentPosition += UNROLL_STEP
    }

    while (currentPosition < limit) {
        val singleByte = getByte(currentPosition)
        if (singleByte < ASCII_LIMIT &&
            ((escapeMasks[singleByte shr BITMASK_SHIFT] shr
                    (singleByte and BITMASK_INDEX_MASK)) and BITMASK_UNIT != RESULT_NONE)
        ) {
            if (singleByte == QUOTE_INT) {
                return currentPosition
            }
            return MATCH_END
        }
        currentPosition++
    }
    return MATCH_END
}

internal inline fun scanStringImpl(
    start: Int,
    limit: Int,
    getByte: (Int) -> Int
): Long {
    var currentPosition = start
    var accumulatedHash = 0
    var isPureAscii = true
    val escapeMasks = GhostJsonConstants.ESCAPE_MASKS
    val hashShift = HASH_SHIFT
    val asciiLimit = ASCII_LIMIT
    val matchEndLong = MATCH_END.toLong()

    while (currentPosition + INDEX_OFFSET_3 < limit) {
        val byte0 = getByte(currentPosition)
        if (byte0 < asciiLimit &&
            ((escapeMasks[byte0 shr BITMASK_SHIFT] shr
                    (byte0 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != RESULT_NONE)
        ) {
            if (byte0 == QUOTE_INT) {
                return packScanResult(currentPosition - start, accumulatedHash, isPureAscii)
            }
            return matchEndLong
        } else if (byte0 >= asciiLimit) {
            isPureAscii = false
        }

        accumulatedHash = (accumulatedHash shl hashShift) - accumulatedHash + byte0

        val byte1 = getByte(currentPosition + INDEX_OFFSET_1)
        if (byte1 < asciiLimit &&
            ((escapeMasks[byte1 shr BITMASK_SHIFT] shr
                    (byte1 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != RESULT_NONE)
        ) {
            if (byte1 == QUOTE_INT) {
                return packScanResult(
                    currentPosition + INDEX_OFFSET_1 - start,
                    accumulatedHash,
                    isPureAscii
                )
            }
            return matchEndLong
        } else if (byte1 >= asciiLimit) {
            isPureAscii = false
        }

        accumulatedHash = (accumulatedHash shl hashShift) - accumulatedHash + byte1

        val byte2 = getByte(currentPosition + INDEX_OFFSET_2)
        if (byte2 < asciiLimit &&
            ((escapeMasks[byte2 shr BITMASK_SHIFT] shr
                    (byte2 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != RESULT_NONE)
        ) {
            if (byte2 == QUOTE_INT) {
                return packScanResult(
                    currentPosition + INDEX_OFFSET_2 - start,
                    accumulatedHash,
                    isPureAscii
                )
            }
            return matchEndLong
        } else if (byte2 >= asciiLimit) {
            isPureAscii = false
        }

        accumulatedHash = (accumulatedHash shl hashShift) - accumulatedHash + byte2

        val byte3 = getByte(currentPosition + INDEX_OFFSET_3)
        if (byte3 < asciiLimit &&
            ((escapeMasks[byte3 shr BITMASK_SHIFT] shr
                    (byte3 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != RESULT_NONE)
        ) {
            if (byte3 == QUOTE_INT) {
                return packScanResult(
                    currentPosition + INDEX_OFFSET_3 - start,
                    accumulatedHash,
                    isPureAscii
                )
            }
            return matchEndLong
        } else if (byte3 >= asciiLimit) {
            isPureAscii = false
        }

        accumulatedHash = (accumulatedHash shl hashShift) - accumulatedHash + byte3
        currentPosition += UNROLL_STEP
    }

    while (currentPosition < limit) {
        val singleByte = getByte(currentPosition)
        if (singleByte < asciiLimit &&
            ((escapeMasks[singleByte shr BITMASK_SHIFT] shr
                    (singleByte and BITMASK_INDEX_MASK)) and BITMASK_UNIT != RESULT_NONE)
        ) {
            if (singleByte == QUOTE_INT) {
                return packScanResult(currentPosition - start, accumulatedHash, isPureAscii)
            }
            return matchEndLong
        } else if (singleByte >= asciiLimit) {
            isPureAscii = false
        }

        accumulatedHash = (accumulatedHash shl hashShift) - accumulatedHash + singleByte
        currentPosition++
    }

    return matchEndLong
}

/** Branch-free "does any byte of [v] equal zero?" (McIlroy). Non-zero result ⇒ yes. */
internal inline fun swarHasZeroByte(v: Long): Long =
    (v - SWAR_ONES) and v.inv() and SWAR_HIGHS

/**
 * SWAR variant of [scanStringImpl] that does NOT accumulate the pool hash. Detects the closing
 * quote, escapes/control bytes, and non-ASCII content eight bytes at a time using branch-free
 * bit tricks, falling back to a byte scan only for the word that contains a boundary byte.
 *
 * Returns [packScanResult] with [SCAN_HASH_NONE] hash bits on success, or [MATCH_END] as a Long
 * when an escape or control byte requires the slow path. The rolling hash — needed only for the
 * small string pool — is recomputed cheaply by [rollingHashImpl] over short spans, so the bulk
 * of the byte volume (long, never-pooled values) is never hashed.
 */
internal fun scanStringSwarNoHash(data: ByteArray, start: Int, limit: Int): Long {
    var cursor = start
    var isPureAscii = true
    val matchEndLong = MATCH_END.toLong()

    // SWAR fast path: consume LONG_BYTES windows with no quote, backslash, or control byte.
    // Skipped on Wasm (ghostUseSwarScans=false) — i64 pack+SWAR loses on JavaScriptCore (#16).
    if (ghostUseSwarScans) {
        while (cursor + LONG_BYTES <= limit) {
            val packedWindow = ghostReadLong8(data, cursor)
            val hasQuote = swarHasZeroByte(packedWindow xor SWAR_QUOTES)
            val hasBackslash = swarHasZeroByte(packedWindow xor SWAR_BACKSLASHES)
            // Bytes strictly below SPACE_INT (control chars); space itself is intentionally excluded.
            val hasControl =
                (packedWindow - SPACE_RUN_LONG) and packedWindow.inv() and SWAR_HIGHS
            if ((hasQuote or hasBackslash or hasControl) != RESULT_NONE) {
                break
            }
            if ((packedWindow and SWAR_HIGHS) != RESULT_NONE) {
                isPureAscii = false
            }
            cursor += LONG_BYTES
        }
    }

    // Byte tail (or full scan when SWAR is disabled): boundary window + remainder.
    val escapeMasks = GhostJsonConstants.ESCAPE_MASKS
    val asciiLimit = ASCII_LIMIT
    while (cursor < limit) {
        val tokenByte = data[cursor].toInt() and BYTE_MASK
        if (tokenByte < asciiLimit &&
            ((escapeMasks[tokenByte shr BITMASK_SHIFT] shr (tokenByte and BITMASK_INDEX_MASK)) and BITMASK_UNIT != RESULT_NONE)
        ) {
            if (tokenByte == QUOTE_INT) {
                return packScanResult(cursor - start, SCAN_HASH_NONE, isPureAscii)
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
 * bit-for-bit identical to the accumulation in [scanStringImpl].
 */
internal fun rollingHashImpl(data: ByteArray, start: Int, length: Int): Int {
    var accumulatedHash = SCAN_HASH_NONE
    var byteOffset = 0
    while (byteOffset < length) {
        accumulatedHash =
            (accumulatedHash shl HASH_SHIFT) - accumulatedHash +
                (data[start + byteOffset].toInt() and BYTE_MASK)
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
    if (targetString.length != length) {
        return false
    }
    var currentIndex = 0

    while (currentIndex + 3 < length) {
        if (targetString[currentIndex].code != getByte(start + currentIndex)) {
            return false
        }
        if (targetString[currentIndex + 1].code != getByte(start + currentIndex + 1)) {
            return false
        }
        if (targetString[currentIndex + 2].code != getByte(start + currentIndex + 2)) {
            return false
        }
        if (targetString[currentIndex + 3].code != getByte(start + currentIndex + 3)) {
            return false
        }
        currentIndex += 4
    }

    while (currentIndex < length) {
        if (targetString[currentIndex].code != getByte(start + currentIndex)) {
            return false
        }
        currentIndex++
    }

    return true
}

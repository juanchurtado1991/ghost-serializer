package com.ghost.serialization.parser

import com.ghost.serialization.parser.GhostJsonConstants.ASCII_LIMIT
import com.ghost.serialization.parser.GhostJsonConstants.BITMASK_INDEX_MASK
import com.ghost.serialization.parser.GhostJsonConstants.BITMASK_SHIFT
import com.ghost.serialization.parser.GhostJsonConstants.BITMASK_UNIT
import com.ghost.serialization.parser.GhostJsonConstants.BYTE_MASK
import com.ghost.serialization.parser.GhostJsonConstants.BYTE_SHIFT_UNIT
import com.ghost.serialization.parser.GhostJsonConstants.HASH_SHIFT
import com.ghost.serialization.parser.GhostJsonConstants.MATCH_END
import com.ghost.serialization.parser.GhostJsonConstants.QUOTE_INT
import com.ghost.serialization.parser.GhostJsonConstants.RESULT_NONE
import com.ghost.serialization.parser.GhostJsonConstants.SCAN_HASH_MASK
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_32
import com.ghost.serialization.parser.GhostJsonConstants.WHITESPACE_MASK
import com.ghost.serialization.parser.GhostJsonConstants.packScanResult

private const val UNROLL_STEP = 4
private const val INDEX_OFFSET_1 = 1
private const val INDEX_OFFSET_2 = 2
private const val INDEX_OFFSET_3 = 3

internal inline fun findNextNonWhitespaceImpl(
    position: Int,
    limit: Int,
    getByte: (Int) -> Int
): Int {
    var currentPos = position
    val mask = WHITESPACE_MASK

    while (currentPos + INDEX_OFFSET_3 < limit) {
        val b0 = getByte(currentPos)
        if ((mask shr b0) and BYTE_SHIFT_UNIT == RESULT_NONE) return currentPos

        val b1 = getByte(currentPos + INDEX_OFFSET_1)
        if ((mask shr b1) and BYTE_SHIFT_UNIT == RESULT_NONE) return currentPos + INDEX_OFFSET_1

        val b2 = getByte(currentPos + INDEX_OFFSET_2)
        if ((mask shr b2) and BYTE_SHIFT_UNIT == RESULT_NONE) return currentPos + INDEX_OFFSET_2

        val b3 = getByte(currentPos + INDEX_OFFSET_3)
        if ((mask shr b3) and BYTE_SHIFT_UNIT == RESULT_NONE) return currentPos + INDEX_OFFSET_3

        currentPos += UNROLL_STEP
    }
    while (currentPos < limit) {
        val b = getByte(currentPos)
        if ((mask shr b) and BYTE_SHIFT_UNIT == RESULT_NONE) return currentPos
        currentPos++
    }
    return MATCH_END
}

internal inline fun findClosingQuoteImpl(
    position: Int,
    limit: Int,
    getByte: (Int) -> Int
): Int {
    var currentPos = position
    val masks = GhostJsonConstants.ESCAPE_MASKS
    while (currentPos + INDEX_OFFSET_3 < limit) {
        val b0 = getByte(currentPos)
        if (b0 < ASCII_LIMIT && (masks[b0 shr BITMASK_SHIFT] shr
                    (b0 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
            if (b0 == QUOTE_INT) return currentPos
            return MATCH_END
        }
        val b1 = getByte(currentPos + INDEX_OFFSET_1)
        if (b1 < ASCII_LIMIT && (masks[b1 shr BITMASK_SHIFT] shr
                    (b1 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
            if (b1 == QUOTE_INT) return currentPos + INDEX_OFFSET_1
            return MATCH_END
        }
        val b2 = getByte(currentPos + INDEX_OFFSET_2)
        if (b2 < ASCII_LIMIT && (masks[b2 shr BITMASK_SHIFT] shr
                    (b2 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
            if (b2 == QUOTE_INT) return currentPos + INDEX_OFFSET_2
            return MATCH_END
        }
        val b3 = getByte(currentPos + INDEX_OFFSET_3)
        if (b3 < ASCII_LIMIT && (masks[b3 shr BITMASK_SHIFT] shr
                    (b3 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
            if (b3 == QUOTE_INT) return currentPos + INDEX_OFFSET_3
            return MATCH_END
        }
        currentPos += UNROLL_STEP
    }
    while (currentPos < limit) {
        val b = getByte(currentPos)
        if (b < ASCII_LIMIT && (masks[b shr BITMASK_SHIFT] shr
                    (b and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
            if (b == QUOTE_INT) return currentPos
            return MATCH_END
        }
        currentPos++
    }
    return MATCH_END
}

/**
 * Combined scan: finds the closing `"` of a JSON key AND builds the 4-byte
 * dispatch hash in a **single pass**, eliminating the redundant [computeKeyHash]
 * re-read of the first 4 bytes.
 *
 * Returns a packed [Long]: high 32 bits = end position, low 32 bits = dispatch hash.
 * Returns `-1L` on escape, non-ASCII byte, or unterminated string — callers must
 * fall back to the standard [findClosingQuoteImpl] + [computeKeyHash] path.
 */
internal inline fun scanKeyWithHashImpl(
    position: Int,
    limit: Int,
    getByte: (Int) -> Int
): Long {
    var currentPos = position
    var key = 0
    var keyBytes = 0
    val masks = GhostJsonConstants.ESCAPE_MASKS
    while (currentPos < limit) {
        val b = getByte(currentPos)
        // Non-ASCII in a key is very rare but valid — fall back to slow path.
        if (b >= ASCII_LIMIT) return -1L
        if ((masks[b shr BITMASK_SHIFT] shr (b and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
            if (b == QUOTE_INT) {
                // Pack end position (high 32) + dispatch hash (low 32).
                return (currentPos.toLong() shl SHIFT_32) or (key.toLong() and SCAN_HASH_MASK)
            }
            return -1L // escape character — fall back
        }
        // Accumulate first 4 bytes for the dispatch hash (little-endian).
        if (keyBytes < 4) {
            key = key or (b shl (keyBytes shl 3))
            keyBytes++
        }
        currentPos++
    }
    return -1L // unterminated
}


internal inline fun scanStringImpl(
    start: Int,
    limit: Int,
    getByte: (Int) -> Int
): Long {
    var pos = start
    var hash = 0
    var is7Bit = true
    val masks = GhostJsonConstants.ESCAPE_MASKS
    val shift = HASH_SHIFT
    val asciiLimit = ASCII_LIMIT
    val matchEndLong = MATCH_END.toLong()

    while (pos + INDEX_OFFSET_3 < limit) {
        val b0 = getByte(pos)
        if (b0 < asciiLimit && (masks[b0 shr BITMASK_SHIFT] shr
                    (b0 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
            if (b0 == QUOTE_INT) {
                return packScanResult(pos - start, hash, is7Bit)
            }
            return matchEndLong
        } else if (b0 >= asciiLimit) is7Bit = false
        hash = (hash shl shift) - hash + b0

        val b1 = getByte(pos + INDEX_OFFSET_1)
        if (b1 < asciiLimit && (masks[b1 shr BITMASK_SHIFT] shr
                    (b1 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
            if (b1 == QUOTE_INT) {
                return packScanResult(pos + INDEX_OFFSET_1 - start, hash, is7Bit)
            }
            return matchEndLong
        } else if (b1 >= asciiLimit) is7Bit = false
        hash = (hash shl shift) - hash + b1

        val b2 = getByte(pos + INDEX_OFFSET_2)
        if (b2 < asciiLimit && (masks[b2 shr BITMASK_SHIFT] shr
                    (b2 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
            if (b2 == QUOTE_INT) {
                return packScanResult(pos + INDEX_OFFSET_2 - start, hash, is7Bit)
            }
            return matchEndLong
        } else if (b2 >= asciiLimit) is7Bit = false
        hash = (hash shl shift) - hash + b2

        val b3 = getByte(pos + INDEX_OFFSET_3)
        if (b3 < asciiLimit && (masks[b3 shr BITMASK_SHIFT] shr
                    (b3 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
            if (b3 == QUOTE_INT) {
                return packScanResult(pos + INDEX_OFFSET_3 - start, hash, is7Bit)
            }
            return matchEndLong
        } else if (b3 >= asciiLimit) is7Bit = false
        hash = (hash shl shift) - hash + b3

        pos += UNROLL_STEP
    }
    while (pos < limit) {
        val b = getByte(pos)
        if (b < asciiLimit && (masks[b shr BITMASK_SHIFT] shr
                    (b and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
            if (b == QUOTE_INT) {
                return packScanResult(pos - start, hash, is7Bit)
            }
            return matchEndLong
        } else if (b >= asciiLimit) is7Bit = false
        hash = (hash shl shift) - hash + b
        pos++
    }
    return matchEndLong
}

internal inline fun contentEqualsStringImpl(
    start: Int,
    length: Int,
    str: String,
    getByte: (Int) -> Int
): Boolean {
    if (str.length != length) return false
    var index = 0
    // Unrolled x4 for instruction-level parallelism on typical field-name lengths.
    while (index + 3 < length) {
        if (str[index].code != getByte(start + index)) return false
        if (str[index + 1].code != getByte(start + index + 1)) return false
        if (str[index + 2].code != getByte(start + index + 2)) return false
        if (str[index + 3].code != getByte(start + index + 3)) return false
        index += 4
    }
    while (index < length) {
        if (str[index].code != getByte(start + index)) return false
        index++
    }
    return true
}

package com.ghost.serialization.parser

import com.ghost.serialization.parser.GhostJsonConstants.ASCII_LIMIT
import com.ghost.serialization.parser.GhostJsonConstants.BITMASK_INDEX_MASK
import com.ghost.serialization.parser.GhostJsonConstants.BITMASK_SHIFT
import com.ghost.serialization.parser.GhostJsonConstants.BITMASK_UNIT
import com.ghost.serialization.parser.GhostJsonConstants.BYTE_SHIFT_UNIT
import com.ghost.serialization.parser.GhostJsonConstants.HASH_SHIFT
import com.ghost.serialization.parser.GhostJsonConstants.MATCH_END
import com.ghost.serialization.parser.GhostJsonConstants.QUOTE_INT
import com.ghost.serialization.parser.GhostJsonConstants.RESULT_NONE
import com.ghost.serialization.parser.GhostJsonConstants.SPACE_INT
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

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
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
import com.ghost.serialization.parser.GhostJsonConstants.WHITESPACE_MASK

private const val UNROLL_STEP = 4
private const val INDEX_OFFSET_1 = 1
private const val INDEX_OFFSET_2 = 2
private const val INDEX_OFFSET_3 = 3

/**
 * High-performance [GhostSource] backed by an in-memory [ByteArray].
 *
 * Contains all loop-unrolled hot-path scanning logic shared across every platform.
 * JVM and Android subclass this to override [decodeJsonStringRange] with a faster
 * ASCII decoder; all other platforms use this class directly.
 */
@InternalGhostApi
open class ByteArrayGhostSource(var data: ByteArray) : GhostSource {

    override val size: Int get() = data.size

    override fun get(index: Int): Int = data[index].toInt() and BYTE_MASK

    override val rawSourceData: ByteArray get() = data

    override fun decodeToString(start: Int, end: Int): String =
        data.decodeToString(start, end)

    override fun contentEquals(start: Int, expected: okio.ByteString): Boolean {
        if (start + expected.size > size) return false
        return expected.rangeEquals(0, data, start, expected.size)
    }

    override fun findNextNonWhitespace(position: Int, limit: Int): Int {
        val sourceData = data
        var currentPos = position
        val mask = WHITESPACE_MASK

        while (currentPos + INDEX_OFFSET_3 < limit) {
            val b0 = sourceData[currentPos].toInt() and BYTE_MASK
            if ((mask shr b0) and BYTE_SHIFT_UNIT == RESULT_NONE) return currentPos

            val b1 = sourceData[currentPos + INDEX_OFFSET_1].toInt() and BYTE_MASK
            if ((mask shr b1) and BYTE_SHIFT_UNIT == RESULT_NONE) return currentPos + INDEX_OFFSET_1

            val b2 = sourceData[currentPos + INDEX_OFFSET_2].toInt() and BYTE_MASK
            if ((mask shr b2) and BYTE_SHIFT_UNIT == RESULT_NONE) return currentPos + INDEX_OFFSET_2

            val b3 = sourceData[currentPos + INDEX_OFFSET_3].toInt() and BYTE_MASK
            if ((mask shr b3) and BYTE_SHIFT_UNIT == RESULT_NONE) return currentPos + INDEX_OFFSET_3

            currentPos += UNROLL_STEP
        }
        while (currentPos < limit) {
            val b = sourceData[currentPos].toInt() and BYTE_MASK
            if ((mask shr b) and BYTE_SHIFT_UNIT == RESULT_NONE) return currentPos
            currentPos++
        }
        return MATCH_END
    }

    override fun findClosingQuote(position: Int, limit: Int): Int {
        val sourceData = data
        var currentPos = position
        val masks = GhostJsonConstants.ESCAPE_MASKS
        while (currentPos + INDEX_OFFSET_3 < limit) {
            val b0 = sourceData[currentPos].toInt() and BYTE_MASK
            if (b0 < ASCII_LIMIT && (masks[b0 shr BITMASK_SHIFT] shr
                        (b0 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
                if (b0 == QUOTE_INT) return currentPos
                return MATCH_END
            }
            val b1 = sourceData[currentPos + INDEX_OFFSET_1].toInt() and BYTE_MASK
            if (b1 < ASCII_LIMIT && (masks[b1 shr BITMASK_SHIFT] shr
                        (b1 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
                if (b1 == QUOTE_INT) return currentPos + INDEX_OFFSET_1
                return MATCH_END
            }
            val b2 = sourceData[currentPos + INDEX_OFFSET_2].toInt() and BYTE_MASK
            if (b2 < ASCII_LIMIT && (masks[b2 shr BITMASK_SHIFT] shr
                        (b2 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
                if (b2 == QUOTE_INT) return currentPos + INDEX_OFFSET_2
                return MATCH_END
            }
            val b3 = sourceData[currentPos + INDEX_OFFSET_3].toInt() and BYTE_MASK
            if (b3 < ASCII_LIMIT && (masks[b3 shr BITMASK_SHIFT] shr
                        (b3 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
                if (b3 == QUOTE_INT) return currentPos + INDEX_OFFSET_3
                return MATCH_END
            }
            currentPos += UNROLL_STEP
        }
        while (currentPos < limit) {
            val b = sourceData[currentPos].toInt() and BYTE_MASK
            if (b < ASCII_LIMIT && (masks[b shr BITMASK_SHIFT] shr
                        (b and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
                if (b == QUOTE_INT) return currentPos
                return MATCH_END
            }
            currentPos++
        }
        return MATCH_END
    }

    override fun scanString(start: Int, limit: Int): Long {
        val localData = data
        var pos = start
        var hash = 0
        var is7Bit = true
        val masks = GhostJsonConstants.ESCAPE_MASKS
        val shift = HASH_SHIFT
        val asciiLimit = ASCII_LIMIT
        val matchEndLong = MATCH_END.toLong()

        while (pos + INDEX_OFFSET_3 < limit) {
            val b0 = localData[pos].toInt() and BYTE_MASK
            if (b0 < asciiLimit && (masks[b0 shr BITMASK_SHIFT] shr
                        (b0 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
                if (b0 == QUOTE_INT) return GhostJsonConstants
                    .packScanResult(pos - start, hash, is7Bit)
                return matchEndLong
            } else if (b0 >= asciiLimit) is7Bit = false
            hash = (hash shl shift) - hash + b0

            val b1 = localData[pos + INDEX_OFFSET_1].toInt() and BYTE_MASK
            if (b1 < asciiLimit && (masks[b1 shr BITMASK_SHIFT] shr
                        (b1 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
                if (b1 == QUOTE_INT) return GhostJsonConstants
                    .packScanResult(pos + INDEX_OFFSET_1 - start, hash, is7Bit)
                return matchEndLong
            } else if (b1 >= asciiLimit) is7Bit = false
            hash = (hash shl shift) - hash + b1

            val b2 = localData[pos + INDEX_OFFSET_2].toInt() and BYTE_MASK
            if (b2 < asciiLimit && (masks[b2 shr BITMASK_SHIFT] shr
                        (b2 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
                if (b2 == QUOTE_INT) return GhostJsonConstants
                    .packScanResult(pos + INDEX_OFFSET_2 - start, hash, is7Bit)
                return matchEndLong
            } else if (b2 >= asciiLimit) is7Bit = false
            hash = (hash shl shift) - hash + b2

            val b3 = localData[pos + INDEX_OFFSET_3].toInt() and BYTE_MASK
            if (b3 < asciiLimit && (masks[b3 shr BITMASK_SHIFT] shr
                        (b3 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
                if (b3 == QUOTE_INT) return GhostJsonConstants
                    .packScanResult(pos + INDEX_OFFSET_3 - start, hash, is7Bit)
                return matchEndLong
            } else if (b3 >= asciiLimit) is7Bit = false
            hash = (hash shl shift) - hash + b3

            pos += UNROLL_STEP
        }
        while (pos < limit) {
            val b = localData[pos].toInt() and BYTE_MASK
            if (b < asciiLimit && (masks[b shr BITMASK_SHIFT] shr
                        (b and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
                if (b == QUOTE_INT) return GhostJsonConstants
                    .packScanResult(pos - start, hash, is7Bit)
                return matchEndLong
            } else if (b >= asciiLimit) is7Bit = false
            hash = (hash shl shift) - hash + b
            pos++
        }
        return matchEndLong
    }

    override fun contentEqualsString(start: Int, length: Int, str: String): Boolean {
        if (str.length != length) return false
        val localData = data
        var index = 0
        while (index < length) {
            if (str[index].code != (localData[start + index].toInt() and BYTE_MASK)) {
                return false
            }
            index++
        }
        return true
    }
}

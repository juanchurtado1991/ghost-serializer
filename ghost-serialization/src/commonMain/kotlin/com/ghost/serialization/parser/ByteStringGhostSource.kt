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
import okio.ByteString

private const val UNROLL_STEP = 4
private const val INDEX_OFFSET_1 = 1
private const val INDEX_OFFSET_2 = 2
private const val INDEX_OFFSET_3 = 3

/**
 * [GhostSource] backed by an Okio [ByteString] (e.g. [okio.Buffer.snapshot]) without
 * copying into a [ByteArray]. Used by Retrofit/Ktor network adapters.
 */
@InternalGhostApi
class ByteStringGhostSource(val bytes: ByteString) : GhostSource {

    override val size: Int get() = bytes.size

    override fun get(index: Int): Int = bytes[index].toInt() and BYTE_MASK

    override fun decodeToString(start: Int, end: Int): String =
        bytes.substring(start, end).utf8()

    override fun contentEquals(start: Int, expected: ByteString): Boolean {
        if (start + expected.size > size) return false
        return bytes.rangeEquals(start, expected, 0, expected.size)
    }

    override fun findNextNonWhitespace(position: Int, limit: Int): Int {
        var currentPos = position
        val mask = WHITESPACE_MASK

        while (currentPos + INDEX_OFFSET_3 < limit) {
            val b0 = bytes[currentPos].toInt() and BYTE_MASK
            if ((mask shr b0) and BYTE_SHIFT_UNIT == RESULT_NONE) return currentPos

            val b1 = bytes[currentPos + INDEX_OFFSET_1].toInt() and BYTE_MASK
            if ((mask shr b1) and BYTE_SHIFT_UNIT == RESULT_NONE) return currentPos + INDEX_OFFSET_1

            val b2 = bytes[currentPos + INDEX_OFFSET_2].toInt() and BYTE_MASK
            if ((mask shr b2) and BYTE_SHIFT_UNIT == RESULT_NONE) return currentPos + INDEX_OFFSET_2

            val b3 = bytes[currentPos + INDEX_OFFSET_3].toInt() and BYTE_MASK
            if ((mask shr b3) and BYTE_SHIFT_UNIT == RESULT_NONE) return currentPos + INDEX_OFFSET_3

            currentPos += UNROLL_STEP
        }
        while (currentPos < limit) {
            val b = bytes[currentPos].toInt() and BYTE_MASK
            if ((mask shr b) and BYTE_SHIFT_UNIT == RESULT_NONE) return currentPos
            currentPos++
        }
        return MATCH_END
    }

    override fun findClosingQuote(position: Int, limit: Int): Int {
        var currentPos = position
        val masks = GhostJsonConstants.ESCAPE_MASKS
        while (currentPos + INDEX_OFFSET_3 < limit) {
            val b0 = bytes[currentPos].toInt() and BYTE_MASK
            if (b0 < ASCII_LIMIT && (masks[b0 shr BITMASK_SHIFT] shr
                        (b0 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
                if (b0 == QUOTE_INT) return currentPos
                return MATCH_END
            }
            val b1 = bytes[currentPos + INDEX_OFFSET_1].toInt() and BYTE_MASK
            if (b1 < ASCII_LIMIT && (masks[b1 shr BITMASK_SHIFT] shr
                        (b1 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
                if (b1 == QUOTE_INT) return currentPos + INDEX_OFFSET_1
                return MATCH_END
            }
            val b2 = bytes[currentPos + INDEX_OFFSET_2].toInt() and BYTE_MASK
            if (b2 < ASCII_LIMIT && (masks[b2 shr BITMASK_SHIFT] shr
                        (b2 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
                if (b2 == QUOTE_INT) return currentPos + INDEX_OFFSET_2
                return MATCH_END
            }
            val b3 = bytes[currentPos + INDEX_OFFSET_3].toInt() and BYTE_MASK
            if (b3 < ASCII_LIMIT && (masks[b3 shr BITMASK_SHIFT] shr
                        (b3 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
                if (b3 == QUOTE_INT) return currentPos + INDEX_OFFSET_3
                return MATCH_END
            }
            currentPos += UNROLL_STEP
        }
        while (currentPos < limit) {
            val b = bytes[currentPos].toInt() and BYTE_MASK
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
        var pos = start
        var hash = 0
        var is7Bit = true
        val masks = GhostJsonConstants.ESCAPE_MASKS
        val shift = HASH_SHIFT
        val asciiLimit = ASCII_LIMIT
        val matchEndLong = MATCH_END.toLong()

        while (pos + INDEX_OFFSET_3 < limit) {
            val b0 = bytes[pos].toInt() and BYTE_MASK
            if (b0 < asciiLimit && (masks[b0 shr BITMASK_SHIFT] shr
                        (b0 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
                if (b0 == QUOTE_INT) return GhostJsonConstants
                    .packScanResult(pos - start, hash, is7Bit)
                return matchEndLong
            } else if (b0 >= asciiLimit) is7Bit = false
            hash = (hash shl shift) - hash + b0

            val b1 = bytes[pos + INDEX_OFFSET_1].toInt() and BYTE_MASK
            if (b1 < asciiLimit && (masks[b1 shr BITMASK_SHIFT] shr
                        (b1 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
                if (b1 == QUOTE_INT) return GhostJsonConstants
                    .packScanResult(pos + INDEX_OFFSET_1 - start, hash, is7Bit)
                return matchEndLong
            } else if (b1 >= asciiLimit) is7Bit = false
            hash = (hash shl shift) - hash + b1

            val b2 = bytes[pos + INDEX_OFFSET_2].toInt() and BYTE_MASK
            if (b2 < asciiLimit && (masks[b2 shr BITMASK_SHIFT] shr
                        (b2 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
                if (b2 == QUOTE_INT) return GhostJsonConstants
                    .packScanResult(pos + INDEX_OFFSET_2 - start, hash, is7Bit)
                return matchEndLong
            } else if (b2 >= asciiLimit) is7Bit = false
            hash = (hash shl shift) - hash + b2

            val b3 = bytes[pos + INDEX_OFFSET_3].toInt() and BYTE_MASK
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
            val b = bytes[pos].toInt() and BYTE_MASK
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
        var index = 0
        while (index < length) {
            if (str[index].code != (bytes[start + index].toInt() and BYTE_MASK)) {
                return false
            }
            index++
        }
        return true
    }
}

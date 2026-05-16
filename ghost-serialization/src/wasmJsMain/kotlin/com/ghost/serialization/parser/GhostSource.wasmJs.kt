@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.GhostJsonConstants.BYTE_MASK
import com.ghost.serialization.parser.GhostJsonConstants.HASH_SHIFT
import com.ghost.serialization.parser.GhostJsonConstants.QUOTE_INT
import com.ghost.serialization.parser.GhostJsonConstants.ASCII_LIMIT
import com.ghost.serialization.parser.GhostJsonConstants.BITMASK_SHIFT
import com.ghost.serialization.parser.GhostJsonConstants.BITMASK_UNIT
import com.ghost.serialization.parser.GhostJsonConstants.BITMASK_INDEX_MASK
import com.ghost.serialization.parser.GhostJsonConstants.WHITESPACE_MASK

@InternalGhostApi
class WasmByteArraySource(val data: ByteArray) : GhostSource {

    override val size: Int get() = data.size

    override fun get(index: Int): Int = data[index].toInt() and BYTE_MASK

    override val rawSourceData: ByteArray get() = data

    override fun decodeToString(start: Int, end: Int): String = data.decodeToString(start, end)

    override fun contentEquals(start: Int, expected: okio.ByteString): Boolean {
        if (start + expected.size > size) return false
        return expected.rangeEquals(0, data, start, expected.size)
    }


    override fun findNextNonWhitespace(position: Int, limit: Int): Int {
        val sourceData = data
        var currentPos = position
        val mask = WHITESPACE_MASK

        while (currentPos + 3 < limit) {
            val b0 = sourceData[currentPos].toInt() and BYTE_MASK
            if ((mask shr b0) and 1L == 0L) return currentPos

            val b1 = sourceData[currentPos + 1].toInt() and BYTE_MASK
            if ((mask shr b1) and 1L == 0L) return currentPos + 1

            val b2 = sourceData[currentPos + 2].toInt() and BYTE_MASK
            if ((mask shr b2) and 1L == 0L) return currentPos + 2

            val b3 = sourceData[currentPos + 3].toInt() and BYTE_MASK
            if ((mask shr b3) and 1L == 0L) return currentPos + 3

            currentPos += 4
        }
        while (currentPos < limit) {
            val b = sourceData[currentPos].toInt() and BYTE_MASK
            if ((mask shr b) and 1L == 0L) return currentPos
            currentPos++
        }
        return -1
    }

    override fun findClosingQuote(position: Int, limit: Int): Int {
        val sourceData = data
        var currentPos = position
        val masks = GhostJsonConstants.ESCAPE_MASKS
        while (currentPos + 3 < limit) {
            val b0 = sourceData[currentPos].toInt() and BYTE_MASK
            if (b0 < ASCII_LIMIT && (masks[b0 shr BITMASK_SHIFT] shr (b0 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
                if (b0 == QUOTE_INT) return currentPos
                return -1
            }
            val b1 = sourceData[currentPos + 1].toInt() and BYTE_MASK
            if (b1 < ASCII_LIMIT && (masks[b1 shr BITMASK_SHIFT] shr (b1 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
                if (b1 == QUOTE_INT) return currentPos + 1
                return -1
            }
            val b2 = sourceData[currentPos + 2].toInt() and BYTE_MASK
            if (b2 < ASCII_LIMIT && (masks[b2 shr BITMASK_SHIFT] shr (b2 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
                if (b2 == QUOTE_INT) return currentPos + 2
                return -1
            }
            val b3 = sourceData[currentPos + 3].toInt() and BYTE_MASK
            if (b3 < ASCII_LIMIT && (masks[b3 shr BITMASK_SHIFT] shr (b3 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
                if (b3 == QUOTE_INT) return currentPos + 3
                return -1
            }
            currentPos += 4
        }
        while (currentPos < limit) {
            val b = sourceData[currentPos].toInt() and BYTE_MASK
            if (b < ASCII_LIMIT && (masks[b shr BITMASK_SHIFT] shr (b and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
                if (b == QUOTE_INT) return currentPos
                return -1
            }
            currentPos++
        }
        return -1
    }

    override fun scanString(start: Int, limit: Int): Long {
        val localData = data
        var pos = start
        var hash = 0
        var is7Bit = true
        val masks = GhostJsonConstants.ESCAPE_MASKS
        val shift = HASH_SHIFT
        val asciiLimit = ASCII_LIMIT
        while (pos + 3 < limit) {
            val b0 = localData[pos].toInt() and BYTE_MASK
            if (b0 < asciiLimit && (masks[b0 shr BITMASK_SHIFT] shr (b0 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
                if (b0 == QUOTE_INT) return GhostJsonConstants.packScanResult(pos - start, hash, is7Bit)
                return -1L
            } else if (b0 >= asciiLimit) is7Bit = false
            hash = (hash shl shift) - hash + b0

            val b1 = localData[pos + 1].toInt() and BYTE_MASK
            if (b1 < asciiLimit && (masks[b1 shr BITMASK_SHIFT] shr (b1 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
                if (b1 == QUOTE_INT) return GhostJsonConstants.packScanResult(pos + 1 - start, hash, is7Bit)
                return -1L
            } else if (b1 >= asciiLimit) is7Bit = false
            hash = (hash shl shift) - hash + b1

            val b2 = localData[pos + 2].toInt() and BYTE_MASK
            if (b2 < asciiLimit && (masks[b2 shr BITMASK_SHIFT] shr (b2 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
                if (b2 == QUOTE_INT) return GhostJsonConstants.packScanResult(pos + 2 - start, hash, is7Bit)
                return -1L
            } else if (b2 >= asciiLimit) is7Bit = false
            hash = (hash shl shift) - hash + b2

            val b3 = localData[pos + 3].toInt() and BYTE_MASK
            if (b3 < asciiLimit && (masks[b3 shr BITMASK_SHIFT] shr (b3 and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
                if (b3 == QUOTE_INT) return GhostJsonConstants.packScanResult(pos + 3 - start, hash, is7Bit)
                return -1L
            } else if (b3 >= asciiLimit) is7Bit = false
            hash = (hash shl shift) - hash + b3

            pos += 4
        }
        while (pos < limit) {
            val b = localData[pos].toInt() and BYTE_MASK
            if (b < asciiLimit && (masks[b shr BITMASK_SHIFT] shr (b and BITMASK_INDEX_MASK)) and BITMASK_UNIT != 0L) {
                if (b == QUOTE_INT) return GhostJsonConstants.packScanResult(pos - start, hash, is7Bit)
                return -1L
            } else if (b >= asciiLimit) is7Bit = false
            hash = (hash shl shift) - hash + b
            pos++
        }
        return -1L
    }


    override fun contentEqualsString(start: Int, length: Int, str: String): Boolean {
        if (str.length != length) return false
        val localData = data
        var index = 0
        while (index < length) {
            if (str[index].code != (localData[start + index].toInt() and BYTE_MASK)) return false
            index++
        }
        return true
    }
}

@InternalGhostApi
actual fun createByteArraySource(data: ByteArray): GhostSource = WasmByteArraySource(data)

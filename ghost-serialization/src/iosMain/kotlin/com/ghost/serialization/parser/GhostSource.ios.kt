package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.GhostJsonConstants.BYTE_MASK
import com.ghost.serialization.parser.GhostJsonConstants.HASH_SHIFT
import com.ghost.serialization.parser.GhostJsonConstants.QUOTE_INT
import com.ghost.serialization.parser.GhostJsonConstants.ASCII_LIMIT
import com.ghost.serialization.parser.GhostJsonConstants.NEEDS_ESCAPE_MASK_LOW
import com.ghost.serialization.parser.GhostJsonConstants.NEEDS_ESCAPE_MASK_HIGH
import com.ghost.serialization.parser.GhostJsonConstants.BITMASK_INDEX_MASK
import com.ghost.serialization.parser.GhostJsonConstants.BITMASK_WIDTH
import com.ghost.serialization.parser.GhostJsonConstants.WHITESPACE_MASK

@InternalGhostApi
class IosByteArraySource(val data: ByteArray) : GhostSource {

    override val size: Int get() = data.size

    override fun get(index: Int): Int = data[index].toInt() and BYTE_MASK

    override val rawSourceData: ByteArray get() = data

    override fun decodeToString(start: Int, end: Int): String = data.decodeToString(start, end)

    override fun contentEquals(start: Int, expected: okio.ByteString): Boolean {
        if (start + expected.size > size) return false
        return expected.rangeEquals(0, data, start, expected.size)
    }

    override fun copyTo(sink: ByteArray, sinkOffset: Int, start: Int, count: Int) {
        data.copyInto(sink, sinkOffset, start, start + count)
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
        val maskLow = NEEDS_ESCAPE_MASK_LOW
        val maskHigh = NEEDS_ESCAPE_MASK_HIGH
        val indexMask = BITMASK_INDEX_MASK
        val bitmaskWidth = BITMASK_WIDTH

        while (currentPos + 3 < limit) {
            val b0 = sourceData[currentPos].toInt() and BYTE_MASK
            if (b0 < ASCII_LIMIT) {
                if (((if (b0.toLong() < bitmaskWidth) maskLow else maskHigh) shr (b0 and indexMask)) and 1L != 0L) {
                    if (b0 == QUOTE_INT) return currentPos
                    return -1
                }
            }
            val b1 = sourceData[currentPos + 1].toInt() and BYTE_MASK
            if (b1 < ASCII_LIMIT) {
                if (((if (b1.toLong() < bitmaskWidth) maskLow else maskHigh) shr (b1 and indexMask)) and 1L != 0L) {
                    if (b1 == QUOTE_INT) return currentPos + 1
                    return -1
                }
            }
            val b2 = sourceData[currentPos + 2].toInt() and BYTE_MASK
            if (b2 < ASCII_LIMIT) {
                if (((if (b2.toLong() < bitmaskWidth) maskLow else maskHigh) shr (b2 and indexMask)) and 1L != 0L) {
                    if (b2 == QUOTE_INT) return currentPos + 2
                    return -1
                }
            }
            val b3 = sourceData[currentPos + 3].toInt() and BYTE_MASK
            if (b3 < ASCII_LIMIT) {
                if (((if (b3.toLong() < bitmaskWidth) maskLow else maskHigh) shr (b3 and indexMask)) and 1L != 0L) {
                    if (b3 == QUOTE_INT) return currentPos + 3
                    return -1
                }
            }
            currentPos += 4
        }
        while (currentPos < limit) {
            val b = sourceData[currentPos].toInt() and BYTE_MASK
            if (b < ASCII_LIMIT) {
                if (((if (b.toLong() < bitmaskWidth) maskLow else maskHigh) shr (b and indexMask)) and 1L != 0L) {
                    if (b == QUOTE_INT) return currentPos
                    return -1
                }
            }
            currentPos++
        }
        return -1
    }

    override fun scanString(start: Int, limit: Int, reader: GhostJsonReader): Int {
        reader.beginUnescapedStringContentScan()
        val localData = data
        var pos = start
        var hash = 0
        val maskLow = NEEDS_ESCAPE_MASK_LOW
        val maskHigh = NEEDS_ESCAPE_MASK_HIGH
        val indexMask = BITMASK_INDEX_MASK
        val bitmaskWidth = BITMASK_WIDTH
        val shift = HASH_SHIFT

        while (pos + 3 < limit) {
            val b0 = localData[pos].toInt() and BYTE_MASK
            if (b0 < ASCII_LIMIT) {
                if (((if (b0.toLong() < bitmaskWidth) maskLow else maskHigh) shr (b0 and indexMask)) and 1L != 0L) {
                    if (b0 == QUOTE_INT) { reader.position = pos; return hash }
                    return -1
                }
            } else reader.lastScanContentWas7BitOnly = false
            hash = (hash shl shift) - hash + b0

            val b1 = localData[pos + 1].toInt() and BYTE_MASK
            if (b1 < ASCII_LIMIT) {
                if (((if (b1.toLong() < bitmaskWidth) maskLow else maskHigh) shr (b1 and indexMask)) and 1L != 0L) {
                    if (b1 == QUOTE_INT) { reader.position = pos + 1; return hash }
                    return -1
                }
            } else reader.lastScanContentWas7BitOnly = false
            hash = (hash shl shift) - hash + b1

            val b2 = localData[pos + 2].toInt() and BYTE_MASK
            if (b2 < ASCII_LIMIT) {
                if (((if (b2.toLong() < bitmaskWidth) maskLow else maskHigh) shr (b2 and indexMask)) and 1L != 0L) {
                    if (b2 == QUOTE_INT) { reader.position = pos + 2; return hash }
                    return -1
                }
            } else reader.lastScanContentWas7BitOnly = false
            hash = (hash shl shift) - hash + b2

            val b3 = localData[pos + 3].toInt() and BYTE_MASK
            if (b3 < ASCII_LIMIT) {
                if (((if (b3.toLong() < bitmaskWidth) maskLow else maskHigh) shr (b3 and indexMask)) and 1L != 0L) {
                    if (b3 == QUOTE_INT) { reader.position = pos + 3; return hash }
                    return -1
                }
            } else reader.lastScanContentWas7BitOnly = false
            hash = (hash shl shift) - hash + b3

            pos += 4
        }
        while (pos < limit) {
            val b = localData[pos].toInt() and BYTE_MASK
            if (b < ASCII_LIMIT) {
                if (((if (b.toLong() < bitmaskWidth) maskLow else maskHigh) shr (b and indexMask)) and 1L != 0L) {
                    if (b == QUOTE_INT) { reader.position = pos; return hash }
                    return -1
                }
            } else reader.lastScanContentWas7BitOnly = false
            hash = (hash shl shift) - hash + b
            pos++
        }
        return -1
    }

    override fun calculateHash(start: Int, length: Int): Int {
        val localData = data
        var hashResult = 0
        var index = 0
        val shift = HASH_SHIFT
        while (index + 3 < length) {
            hashResult = (hashResult shl shift) - hashResult + (localData[start + index].toInt() and BYTE_MASK)
            hashResult = (hashResult shl shift) - hashResult + (localData[start + index + 1].toInt() and BYTE_MASK)
            hashResult = (hashResult shl shift) - hashResult + (localData[start + index + 2].toInt() and BYTE_MASK)
            hashResult = (hashResult shl shift) - hashResult + (localData[start + index + 3].toInt() and BYTE_MASK)
            index += 4
        }
        while (index < length) {
            hashResult = (hashResult shl shift) - hashResult + (localData[start + index].toInt() and BYTE_MASK)
            index++
        }
        return hashResult
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
actual fun createByteArraySource(data: ByteArray): GhostSource = IosByteArraySource(data)

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi

@InternalGhostApi
class IosByteArraySource(val data: ByteArray) : GhostSource {
    override val size: Int get() = data.size
    override fun get(index: Int): Int = data[index].toInt() and GhostJsonConstants.BYTE_MASK
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
        while (currentPos + GhostJsonConstants.SEARCH_UNROLL_LIMIT < limit) {
            if (sourceData[currentPos] > GhostJsonConstants.SPACE_INT) return currentPos
            if (sourceData[currentPos + 1] > GhostJsonConstants.SPACE_INT) return currentPos + 1
            if (sourceData[currentPos + 2] > GhostJsonConstants.SPACE_INT) return currentPos + 2
            if (sourceData[currentPos + 3] > GhostJsonConstants.SPACE_INT) return currentPos + 3
            if (sourceData[currentPos + 4] > GhostJsonConstants.SPACE_INT) return currentPos + 4
            if (sourceData[currentPos + 5] > GhostJsonConstants.SPACE_INT) return currentPos + 5
            if (sourceData[currentPos + 6] > GhostJsonConstants.SPACE_INT) return currentPos + 6
            if (sourceData[currentPos + 7] > GhostJsonConstants.SPACE_INT) return currentPos + 7
            currentPos += GhostJsonConstants.SEARCH_UNROLL_STEP
        }
        while (currentPos < limit) {
            if (sourceData[currentPos] > GhostJsonConstants.SPACE_INT) return currentPos
            currentPos++
        }
        return -1
    }

    override fun findClosingQuote(position: Int, limit: Int): Int {
        val sourceData = data
        var currentPos = position
        while (currentPos + GhostJsonConstants.SEARCH_UNROLL_LIMIT < limit) {
            val byteAt0 = sourceData[currentPos]
            if (byteAt0 == GhostJsonConstants.QUOTE_BYTE) return currentPos
            if (byteAt0 == GhostJsonConstants.BACKSLASH_BYTE || byteAt0 in GhostJsonConstants.CONTROL_CHAR_START..GhostJsonConstants.CONTROL_CHAR_LIMIT) return -1
            
            val byteAt1 = sourceData[currentPos + 1]
            if (byteAt1 == GhostJsonConstants.QUOTE_BYTE) return currentPos + 1
            if (byteAt1 == GhostJsonConstants.BACKSLASH_BYTE || byteAt1 in GhostJsonConstants.CONTROL_CHAR_START..GhostJsonConstants.CONTROL_CHAR_LIMIT) return -1

            val byteAt2 = sourceData[currentPos + 2]
            if (byteAt2 == GhostJsonConstants.QUOTE_BYTE) return currentPos + 2
            if (byteAt2 == GhostJsonConstants.BACKSLASH_BYTE || byteAt2 in GhostJsonConstants.CONTROL_CHAR_START..GhostJsonConstants.CONTROL_CHAR_LIMIT) return -1

            val byteAt3 = sourceData[currentPos + 3]
            if (byteAt3 == GhostJsonConstants.QUOTE_BYTE) return currentPos + 3
            if (byteAt3 == GhostJsonConstants.BACKSLASH_BYTE || byteAt3 in GhostJsonConstants.CONTROL_CHAR_START..GhostJsonConstants.CONTROL_CHAR_LIMIT) return -1

            val byteAt4 = sourceData[currentPos + 4]
            if (byteAt4 == GhostJsonConstants.QUOTE_BYTE) return currentPos + 4
            if (byteAt4 == GhostJsonConstants.BACKSLASH_BYTE || byteAt4 in GhostJsonConstants.CONTROL_CHAR_START..GhostJsonConstants.CONTROL_CHAR_LIMIT) return -1

            val byteAt5 = sourceData[currentPos + 5]
            if (byteAt5 == GhostJsonConstants.QUOTE_BYTE) return currentPos + 5
            if (byteAt5 == GhostJsonConstants.BACKSLASH_BYTE || byteAt5 in GhostJsonConstants.CONTROL_CHAR_START..GhostJsonConstants.CONTROL_CHAR_LIMIT) return -1

            val byteAt6 = sourceData[currentPos + 6]
            if (byteAt6 == GhostJsonConstants.QUOTE_BYTE) return currentPos + 6
            if (byteAt6 == GhostJsonConstants.BACKSLASH_BYTE || byteAt6 in GhostJsonConstants.CONTROL_CHAR_START..GhostJsonConstants.CONTROL_CHAR_LIMIT) return -1

            val byteAt7 = sourceData[currentPos + 7]
            if (byteAt7 == GhostJsonConstants.QUOTE_BYTE) return currentPos + 7
            if (byteAt7 == GhostJsonConstants.BACKSLASH_BYTE || byteAt7 in GhostJsonConstants.CONTROL_CHAR_START..GhostJsonConstants.CONTROL_CHAR_LIMIT) return -1
            
            currentPos += GhostJsonConstants.SEARCH_UNROLL_STEP
        }
        while (currentPos < limit) {
            val byteValue = sourceData[currentPos]
            if (byteValue == GhostJsonConstants.QUOTE_BYTE) return currentPos
            if (byteValue == GhostJsonConstants.BACKSLASH_BYTE || byteValue in GhostJsonConstants.CONTROL_CHAR_START..GhostJsonConstants.CONTROL_CHAR_LIMIT) return -1
            currentPos++
        }
        return -1
    }

    override fun scanString(start: Int, limit: Int, reader: GhostJsonReader): Int {
        val d = data
        var pos = start
        var hash = 0
        
        while (pos + 3 < limit) {
            val b0 = d[pos]
            if (b0 == GhostJsonConstants.QUOTE_BYTE) { reader.position = pos; return hash }
            if (b0 == GhostJsonConstants.BACKSLASH_BYTE || b0 in GhostJsonConstants.CONTROL_CHAR_START..GhostJsonConstants.CONTROL_CHAR_LIMIT) return -1
            hash = (hash shl GhostJsonConstants.HASH_SHIFT) - hash + b0
            
            val b1 = d[pos + 1]
            if (b1 == GhostJsonConstants.QUOTE_BYTE) { reader.position = pos + 1; return hash }
            if (b1 == GhostJsonConstants.BACKSLASH_BYTE || b1 in GhostJsonConstants.CONTROL_CHAR_START..GhostJsonConstants.CONTROL_CHAR_LIMIT) return -1
            hash = (hash shl GhostJsonConstants.HASH_SHIFT) - hash + b1
            
            val b2 = d[pos + 2]
            if (b2 == GhostJsonConstants.QUOTE_BYTE) { reader.position = pos + 2; return hash }
            if (b2 == GhostJsonConstants.BACKSLASH_BYTE || b2 in GhostJsonConstants.CONTROL_CHAR_START..GhostJsonConstants.CONTROL_CHAR_LIMIT) return -1
            hash = (hash shl GhostJsonConstants.HASH_SHIFT) - hash + b2
            
            val b3 = d[pos + 3]
            if (b3 == GhostJsonConstants.QUOTE_BYTE) { reader.position = pos + 3; return hash }
            if (b3 == GhostJsonConstants.BACKSLASH_BYTE || b3 in GhostJsonConstants.CONTROL_CHAR_START..GhostJsonConstants.CONTROL_CHAR_LIMIT) return -1
            hash = (hash shl GhostJsonConstants.HASH_SHIFT) - hash + b3
            
            pos += 4
        }
        
        while (pos < limit) {
            val b = d[pos]
            if (b == GhostJsonConstants.QUOTE_BYTE) {
                reader.position = pos
                return hash
            }
            if (b == GhostJsonConstants.BACKSLASH_BYTE || b in GhostJsonConstants.CONTROL_CHAR_START..GhostJsonConstants.CONTROL_CHAR_LIMIT) {
                return -1
            }
            hash = (hash shl GhostJsonConstants.HASH_SHIFT) - hash + b
            pos++
        }
        return -1
    }

    override fun calculateHash(start: Int, length: Int): Int {
        val d = data
        var hashResult = 0
        var i = 0
        while (i + 3 < length) {
            hashResult = (hashResult shl GhostJsonConstants.HASH_SHIFT) - hashResult + d[start + i]
            hashResult = (hashResult shl GhostJsonConstants.HASH_SHIFT) - hashResult + d[start + i + 1]
            hashResult = (hashResult shl GhostJsonConstants.HASH_SHIFT) - hashResult + d[start + i + 2]
            hashResult = (hashResult shl GhostJsonConstants.HASH_SHIFT) - hashResult + d[start + i + 3]
            i += 4
        }
        while (i < length) {
            hashResult = (hashResult shl GhostJsonConstants.HASH_SHIFT) - hashResult + d[start + i]
            i++
        }
        return hashResult
    }
}

@InternalGhostApi
actual fun createByteArraySource(data: ByteArray): GhostSource = IosByteArraySource(data)

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.GhostJsonConstants.BACKSLASH_INT
import com.ghost.serialization.parser.GhostJsonConstants.CONTROL_CHAR_LIMIT_INT
import com.ghost.serialization.parser.GhostJsonConstants.CONTROL_CHAR_START_INT
import com.ghost.serialization.parser.GhostJsonConstants.HASH_SHIFT
import com.ghost.serialization.parser.GhostJsonConstants.QUOTE_INT
import com.ghost.serialization.parser.GhostJsonConstants.SPACE_INT
import okio.Buffer
import okio.BufferedSource
import okio.ByteString

/**
 * Implementation of [GhostSource] for streaming data from an [okio.BufferedSource].
 * Automatically requests data from the source as needed.
 */
@InternalGhostApi
class StreamingGhostSource(val okioSource: BufferedSource) : GhostSource {
    private val buffer = okioSource.buffer

    override val size: Int get() = Int.MAX_VALUE

    override fun get(index: Int): Int {
        okioSource.request(index + 1L)
        return buffer[index.toLong()].toInt() and GhostJsonConstants.BYTE_MASK
    }

    override fun decodeToString(start: Int, end: Int): String {
        val length = (end - start).toLong()
        okioSource.request(end.toLong())
        val tempBuffer = Buffer()
        buffer.copyTo(tempBuffer, start.toLong(), length)
        return tempBuffer.readUtf8()
    }

    override fun contentEquals(start: Int, expected: ByteString): Boolean {
        return okioSource.rangeEquals(start.toLong(), expected)
    }

    override fun copyTo(sink: ByteArray, sinkOffset: Int, start: Int, count: Int) {
        val startLong = start.toLong()
        val countLong = count.toLong()
        okioSource.request(startLong + countLong)
        val tempBuffer = Buffer()
        buffer.copyTo(tempBuffer, startLong, countLong)
        tempBuffer.read(sink, sinkOffset, count)
    }

    override fun findNextNonWhitespace(position: Int, limit: Int): Int {
        var localPosition = position
        while (localPosition < limit) {
            if (get(localPosition) > SPACE_INT) return localPosition
            localPosition++
        }
        return -1
    }

    override fun findClosingQuote(position: Int, limit: Int): Int {
        var localPosition = position
        while (localPosition < limit) {
            val byte = get(localPosition)
            if (byte == QUOTE_INT) return localPosition

            if (
                byte == BACKSLASH_INT ||
                byte in CONTROL_CHAR_START_INT..CONTROL_CHAR_LIMIT_INT
            ) {
                return -1
            }
            localPosition++
        }
        return -1
    }

    override fun scanString(start: Int, limit: Int, reader: GhostJsonReader): Int {
        reader.beginUnescapedStringContentScan()
        var position = start
        var hashResult = 0
        while (position < limit) {
            val byte = get(position)
            if (byte == QUOTE_INT) {
                reader.position = position
                return hashResult
            }
            if (byte == BACKSLASH_INT || byte < SPACE_INT) {
                return -1
            }
            reader.noteUnescapedStringContentByte(byte)
            hashResult = (hashResult shl HASH_SHIFT) - hashResult + byte
            position++
        }
        return -1
    }

    override fun calculateHash(start: Int, length: Int): Int {
        var hashResult = 0
        var index = 0
        while (index < length) {
            hashResult = (hashResult shl HASH_SHIFT) - hashResult + get(start + index)
            index++
        }
        return hashResult
    }

    override fun contentEqualsString(start: Int, length: Int, str: String): Boolean {
        if (str.length != length) return false
        var index = 0
        while (index < length) {
            if (str[index].code != get(start + index)) return false
            index++
        }
        return true
    }
}
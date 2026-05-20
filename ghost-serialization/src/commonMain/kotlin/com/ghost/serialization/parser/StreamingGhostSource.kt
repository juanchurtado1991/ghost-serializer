package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import okio.Buffer
import okio.BufferedSource
import okio.ByteString

/**
 * Implementation of [GhostSource] for streaming data from an [okio.BufferedSource].
 * Automatically requests data from the source as needed.
 */
@InternalGhostApi
class StreamingGhostSource(
    val okioSource: BufferedSource
) : GhostSource {

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

    override fun findNextNonWhitespace(position: Int, limit: Int): Int {
        return findNextNonWhitespaceImpl(position, limit) { get(it) }
    }

    override fun findClosingQuote(position: Int, limit: Int): Int {
        return findClosingQuoteImpl(position, limit) { get(it) }
    }

    override fun scanString(start: Int, limit: Int): Long {
        return scanStringImpl(start, limit) { get(it) }
    }

    override fun contentEqualsString(
        start: Int,
        length: Int,
        str: String
    ): Boolean {
        return contentEqualsStringImpl(start, length, str) { get(it) }
    }
}
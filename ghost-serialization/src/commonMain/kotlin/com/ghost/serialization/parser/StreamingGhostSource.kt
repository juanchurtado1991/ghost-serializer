package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
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

    private val bufferBytes = ByteArray(GhostJsonConstants.STREAMING_BUFFER_SIZE)
    private var bufferStart = -1
    private var bufferEnd = -1

    override fun get(index: Int): Int {
        if (index >= bufferStart && index < bufferEnd) {
            return bufferBytes[index - bufferStart].toInt() and GhostJsonConstants.BYTE_MASK
        }
        return getSlow(index)
    }

    private fun getSlow(index: Int): Int {
        val requestedIndexL = index.toLong()
        okioSource.request(requestedIndexL + 1L)
        val available = buffer.size
        if (requestedIndexL >= available) {
            throw IndexOutOfBoundsException("Index $index is out of bounds (available: $available)")
        }

        val alignedStart = (index / GhostJsonConstants.STREAMING_BUFFER_SIZE) * GhostJsonConstants.STREAMING_BUFFER_SIZE
        val toCopy = minOf(GhostJsonConstants.STREAMING_BUFFER_SIZE.toLong(), available - alignedStart)

        if (toCopy <= 0L) {
            throw IndexOutOfBoundsException("Index $index is out of bounds")
        }

        val tempBuffer = okio.Buffer()
        buffer.copyTo(tempBuffer, alignedStart.toLong(), toCopy)
        tempBuffer.read(bufferBytes, 0, toCopy.toInt())
        bufferStart = alignedStart
        bufferEnd = (alignedStart + toCopy).toInt()

        return bufferBytes[index - bufferStart].toInt() and GhostJsonConstants.BYTE_MASK
    }

    override fun decodeToString(start: Int, end: Int): String {
        okioSource.request(end.toLong())
        // snapshot(end) returns a ByteString backed by existing Okio segments — no byte copy.
        // substring(start, end) is a zero-copy range view over that ByteString.
        // The only unavoidable allocation is the final String produced by utf8().
        return buffer.snapshot(end).substring(start, end).utf8()
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
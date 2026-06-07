package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import okio.BufferedSource
import okio.ByteString
import okio.Buffer

/**
 * Implementation of [GhostSource] for streaming data from an [okio.BufferedSource].
 * Automatically requests data from the source as needed.
 */
@InternalGhostApi
class StreamingGhostSource(
    val okioSource: BufferedSource
) : GhostSource {

    private val buffer = okioSource.buffer

    /** Reused across every [getSlow] call and every [decodeToString] call to avoid per-operation allocations. */
    private val tempBuffer = Buffer()

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

        buffer.copyTo(tempBuffer, alignedStart.toLong(), toCopy)
        tempBuffer.read(bufferBytes, 0, toCopy.toInt())
        bufferStart = alignedStart
        bufferEnd = (alignedStart + toCopy).toInt()

        return bufferBytes[index - bufferStart].toInt() and GhostJsonConstants.BYTE_MASK
    }

    override fun decodeToString(start: Int, end: Int): String {
        val length = end - start
        okioSource.request(end.toLong())
        // copyTo fills the reusable tempBuffer with exactly [length] bytes from the live Okio
        // buffer without advancing its read position.  readUtf8 then decodes them in one pass
        // and returns the final String — no intermediate ByteString, no snapshot, no substring.
        buffer.copyTo(tempBuffer, start.toLong(), length.toLong())
        return tempBuffer.readUtf8(length.toLong())
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
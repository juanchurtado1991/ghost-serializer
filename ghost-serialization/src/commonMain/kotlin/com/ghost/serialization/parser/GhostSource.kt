package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import okio.BufferedSource

/**
 * Low-level data source for the Ghost JSON parser.
 *
 * Implementations MUST return bytes as [Int] values in the range 0-255.
 * This ensures that the parser can perform comparisons without sign-extension
 * or explicit masking overhead.
 */
@InternalGhostApi
interface GhostSource {
    val size: Int
    operator fun get(index: Int): Int
    fun decodeToString(start: Int, end: Int): String
    fun contentEquals(start: Int, expected: okio.ByteString): Boolean
    fun copyTo(sink: ByteArray, sinkOffset: Int, start: Int, count: Int)

    /** Optional access to the raw byte array if the source is backed by one. */
    val rawSourceData: ByteArray? get() = null

    /**
     * Finds the next non-whitespace byte (> 32) starting from [position].
     * Returns the position or -1 if not found.
     */
    fun findNextNonWhitespace(position: Int, limit: Int): Int {
        var pos = position
        while (pos < limit) {
            if (get(pos) > GhostJsonConstants.SPACE_INT) return pos
            pos++
        }
        return -1
    }

    /**
     * Finds the closing quote (") starting from [position], stopping at [limit].
     * If a backslash (\) is encountered, it returns -1 to signal the slow path is needed.
     */
    fun findClosingQuote(position: Int, limit: Int): Int {
        var pos = position
        while (pos < limit) {
            val b = get(pos)
            if (b == GhostJsonConstants.QUOTE_INT) return pos
            if (
                b == GhostJsonConstants.BACKSLASH_INT ||
                b in GhostJsonConstants.CONTROL_CHAR_START_INT..GhostJsonConstants.CONTROL_CHAR_LIMIT_INT
            ) {
                return -1
            }
            pos++
        }
        return -1
    }

    /**
     * Scans for the closing quote while calculating the hash in a single pass.
     * Updates the reader's position and returns the hash.
     * Returns -1 if an escape sequence or control character is encountered.
     */
    fun scanString(start: Int, limit: Int, reader: GhostJsonReader): Int {
        var pos = start
        var hashResult = 0
        while (pos < limit) {
            val b = get(pos)
            if (b == GhostJsonConstants.QUOTE_INT) {
                reader.position = pos
                return hashResult
            }
            if (b == GhostJsonConstants.BACKSLASH_INT || b < GhostJsonConstants.SPACE_INT) {
                return -1
            }
            hashResult = (hashResult shl GhostJsonConstants.HASH_SHIFT) - hashResult + b
            pos++
        }
        return -1
    }

    /**
     * Calculates the rolling hash of the bytes at [start] for [length].
     * Default implementation; platforms should override this for speed.
     */
    fun calculateHash(start: Int, length: Int): Int {
        var hashResult = 0
        var i = 0
        while (i < length) {
            hashResult =
                (hashResult shl GhostJsonConstants.HASH_SHIFT) - hashResult + get(start + i)
            i++
        }
        return hashResult
    }
}

@InternalGhostApi
class StreamingGhostSource(val okioSource: BufferedSource) : GhostSource {
    private val buffer = okioSource.buffer

    override val size: Int get() = Int.MAX_VALUE

    override fun get(index: Int): Int {
        okioSource.request(index + 1L)
        return buffer[index.toLong()].toInt() and GhostJsonConstants.BYTE_MASK
    }

    override fun decodeToString(start: Int, end: Int): String {
        okioSource.request(end.toLong())
        return buffer.snapshot().substring(start, end).utf8()
    }

    override fun contentEquals(start: Int, expected: okio.ByteString): Boolean {
        return okioSource.rangeEquals(start.toLong(), expected)
    }

    override fun copyTo(sink: ByteArray, sinkOffset: Int, start: Int, count: Int) {
        val startLong = start.toLong()
        okioSource.request(startLong + count)
        var currentOffset = 0
        var sourceIndexLong = startLong
        while (currentOffset < count) {
            sink[sinkOffset + currentOffset] = buffer[sourceIndexLong]
            sourceIndexLong++
            currentOffset++
        }
    }
}

@InternalGhostApi
expect fun createByteArraySource(
    data: ByteArray
): GhostSource

@InternalGhostApi
fun createSourceBridge(
    okioSource: BufferedSource
): GhostSource = StreamingGhostSource(okioSource)

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
        var currentPosition = position

        while (true) {
            val segmentStart = bufferStart
            val segmentEnd = bufferEnd
            if (currentPosition >= segmentStart && currentPosition < segmentEnd) {
                val segmentLimit = minOf(limit, segmentEnd)
                var p = currentPosition

                while (p + 3 < segmentLimit) {
                    val byte0 = bufferBytes[p - segmentStart].toInt() and GhostJsonConstants.BYTE_MASK
                    if (byte0 > GhostJsonConstants.SPACE_INT ||
                        (GhostJsonConstants.WHITESPACE_MASK shr byte0) and GhostJsonConstants.BYTE_SHIFT_UNIT == GhostJsonConstants.RESULT_NONE
                    ) return p

                    val byte1 = bufferBytes[p + 1 - segmentStart].toInt() and GhostJsonConstants.BYTE_MASK
                    if (byte1 > GhostJsonConstants.SPACE_INT ||
                        (GhostJsonConstants.WHITESPACE_MASK shr byte1) and GhostJsonConstants.BYTE_SHIFT_UNIT == GhostJsonConstants.RESULT_NONE
                    ) return p + 1

                    val byte2 = bufferBytes[p + 2 - segmentStart].toInt() and GhostJsonConstants.BYTE_MASK
                    if (byte2 > GhostJsonConstants.SPACE_INT ||
                        (GhostJsonConstants.WHITESPACE_MASK shr byte2) and GhostJsonConstants.BYTE_SHIFT_UNIT == GhostJsonConstants.RESULT_NONE
                    ) return p + 2

                    val byte3 = bufferBytes[p + 3 - segmentStart].toInt() and GhostJsonConstants.BYTE_MASK
                    if (byte3 > GhostJsonConstants.SPACE_INT ||
                        (GhostJsonConstants.WHITESPACE_MASK shr byte3) and GhostJsonConstants.BYTE_SHIFT_UNIT == GhostJsonConstants.RESULT_NONE
                    ) return p + 3

                    p += 4
                }

                while (p < segmentLimit) {
                    val singleByte = bufferBytes[p - segmentStart].toInt() and GhostJsonConstants.BYTE_MASK
                    if (singleByte > GhostJsonConstants.SPACE_INT ||
                        (GhostJsonConstants.WHITESPACE_MASK shr singleByte) and GhostJsonConstants.BYTE_SHIFT_UNIT == GhostJsonConstants.RESULT_NONE
                    ) return p
                    p++
                }

                currentPosition = p
                if (currentPosition >= limit) return -1
            } else {
                getSlow(currentPosition)
                if (bufferStart == -1 || currentPosition >= bufferEnd) return -1
            }
        }
    }

    override fun findClosingQuote(position: Int, limit: Int): Int {
        var currentPosition = position
        val escapeMasks = GhostJsonConstants.ESCAPE_MASKS

        while (true) {
            val segmentStart = bufferStart
            val segmentEnd = bufferEnd
            if (currentPosition >= segmentStart && currentPosition < segmentEnd) {
                val segmentLimit = minOf(limit, segmentEnd)
                var p = currentPosition

                while (p + 3 < segmentLimit) {
                    val byte0 = bufferBytes[p - segmentStart].toInt() and GhostJsonConstants.BYTE_MASK
                    if (byte0 < GhostJsonConstants.ASCII_LIMIT &&
                        (escapeMasks[byte0 shr GhostJsonConstants.BITMASK_SHIFT] shr
                                (byte0 and GhostJsonConstants.BITMASK_INDEX_MASK)) and GhostJsonConstants.BITMASK_UNIT != GhostJsonConstants.RESULT_NONE
                    ) {
                        if (byte0 == GhostJsonConstants.QUOTE_INT) return p
                        return -1
                    }
                    val byte1 = bufferBytes[p + 1 - segmentStart].toInt() and GhostJsonConstants.BYTE_MASK
                    if (byte1 < GhostJsonConstants.ASCII_LIMIT &&
                        (escapeMasks[byte1 shr GhostJsonConstants.BITMASK_SHIFT] shr
                                (byte1 and GhostJsonConstants.BITMASK_INDEX_MASK)) and GhostJsonConstants.BITMASK_UNIT != GhostJsonConstants.RESULT_NONE
                    ) {
                        if (byte1 == GhostJsonConstants.QUOTE_INT) return p + 1
                        return -1
                    }
                    val byte2 = bufferBytes[p + 2 - segmentStart].toInt() and GhostJsonConstants.BYTE_MASK
                    if (byte2 < GhostJsonConstants.ASCII_LIMIT &&
                        (escapeMasks[byte2 shr GhostJsonConstants.BITMASK_SHIFT] shr
                                (byte2 and GhostJsonConstants.BITMASK_INDEX_MASK)) and GhostJsonConstants.BITMASK_UNIT != GhostJsonConstants.RESULT_NONE
                    ) {
                        if (byte2 == GhostJsonConstants.QUOTE_INT) return p + 2
                        return -1
                    }
                    val byte3 = bufferBytes[p + 3 - segmentStart].toInt() and GhostJsonConstants.BYTE_MASK
                    if (byte3 < GhostJsonConstants.ASCII_LIMIT &&
                        (escapeMasks[byte3 shr GhostJsonConstants.BITMASK_SHIFT] shr
                                (byte3 and GhostJsonConstants.BITMASK_INDEX_MASK)) and GhostJsonConstants.BITMASK_UNIT != GhostJsonConstants.RESULT_NONE
                    ) {
                        if (byte3 == GhostJsonConstants.QUOTE_INT) return p + 3
                        return -1
                    }
                    p += 4
                }

                while (p < segmentLimit) {
                    val singleByte = bufferBytes[p - segmentStart].toInt() and GhostJsonConstants.BYTE_MASK
                    if (singleByte < GhostJsonConstants.ASCII_LIMIT &&
                        (escapeMasks[singleByte shr GhostJsonConstants.BITMASK_SHIFT] shr
                                (singleByte and GhostJsonConstants.BITMASK_INDEX_MASK)) and GhostJsonConstants.BITMASK_UNIT != GhostJsonConstants.RESULT_NONE
                    ) {
                        if (singleByte == GhostJsonConstants.QUOTE_INT) return p
                        return -1
                    }
                    p++
                }

                currentPosition = p
                if (currentPosition >= limit) return -1
            } else {
                getSlow(currentPosition)
                if (bufferStart == -1 || currentPosition >= bufferEnd) return -1
            }
        }
    }

    override fun scanString(start: Int, limit: Int): Long {
        var currentPosition = start
        var accumulatedHash = 0
        var isPureAscii = true
        val escapeMasks = GhostJsonConstants.ESCAPE_MASKS

        while (true) {
            val segmentStart = bufferStart
            val segmentEnd = bufferEnd
            if (currentPosition >= segmentStart && currentPosition < segmentEnd) {
                val segmentLimit = minOf(limit, segmentEnd)
                var p = currentPosition

                while (p + 3 < segmentLimit) {
                    val byte0 = bufferBytes[p - segmentStart].toInt() and GhostJsonConstants.BYTE_MASK
                    if (byte0 < GhostJsonConstants.ASCII_LIMIT &&
                        (escapeMasks[byte0 shr GhostJsonConstants.BITMASK_SHIFT] shr
                                (byte0 and GhostJsonConstants.BITMASK_INDEX_MASK)) and GhostJsonConstants.BITMASK_UNIT != GhostJsonConstants.RESULT_NONE
                    ) {
                        if (byte0 == GhostJsonConstants.QUOTE_INT) {
                            return GhostJsonConstants.packScanResult(p - start, accumulatedHash, isPureAscii)
                        }
                        return GhostJsonConstants.MATCH_END.toLong()
                    } else if (byte0 >= GhostJsonConstants.ASCII_LIMIT) {
                        isPureAscii = false
                    }
                    accumulatedHash = (accumulatedHash shl GhostJsonConstants.HASH_SHIFT) - accumulatedHash + byte0

                    val byte1 = bufferBytes[p + 1 - segmentStart].toInt() and GhostJsonConstants.BYTE_MASK
                    if (byte1 < GhostJsonConstants.ASCII_LIMIT &&
                        (escapeMasks[byte1 shr GhostJsonConstants.BITMASK_SHIFT] shr
                                (byte1 and GhostJsonConstants.BITMASK_INDEX_MASK)) and GhostJsonConstants.BITMASK_UNIT != GhostJsonConstants.RESULT_NONE
                    ) {
                        if (byte1 == GhostJsonConstants.QUOTE_INT) {
                            return GhostJsonConstants.packScanResult(p + 1 - start, accumulatedHash, isPureAscii)
                        }
                        return GhostJsonConstants.MATCH_END.toLong()
                    } else if (byte1 >= GhostJsonConstants.ASCII_LIMIT) {
                        isPureAscii = false
                    }
                    accumulatedHash = (accumulatedHash shl GhostJsonConstants.HASH_SHIFT) - accumulatedHash + byte1

                    val byte2 = bufferBytes[p + 2 - segmentStart].toInt() and GhostJsonConstants.BYTE_MASK
                    if (byte2 < GhostJsonConstants.ASCII_LIMIT &&
                        (escapeMasks[byte2 shr GhostJsonConstants.BITMASK_SHIFT] shr
                                (byte2 and GhostJsonConstants.BITMASK_INDEX_MASK)) and GhostJsonConstants.BITMASK_UNIT != GhostJsonConstants.RESULT_NONE
                    ) {
                        if (byte2 == GhostJsonConstants.QUOTE_INT) {
                            return GhostJsonConstants.packScanResult(p + 2 - start, accumulatedHash, isPureAscii)
                        }
                        return GhostJsonConstants.MATCH_END.toLong()
                    } else if (byte2 >= GhostJsonConstants.ASCII_LIMIT) {
                        isPureAscii = false
                    }
                    accumulatedHash = (accumulatedHash shl GhostJsonConstants.HASH_SHIFT) - accumulatedHash + byte2

                    val byte3 = bufferBytes[p + 3 - segmentStart].toInt() and GhostJsonConstants.BYTE_MASK
                    if (byte3 < GhostJsonConstants.ASCII_LIMIT &&
                        (escapeMasks[byte3 shr GhostJsonConstants.BITMASK_SHIFT] shr
                                (byte3 and GhostJsonConstants.BITMASK_INDEX_MASK)) and GhostJsonConstants.BITMASK_UNIT != GhostJsonConstants.RESULT_NONE
                    ) {
                        if (byte3 == GhostJsonConstants.QUOTE_INT) {
                            return GhostJsonConstants.packScanResult(p + 3 - start, accumulatedHash, isPureAscii)
                        }
                        return GhostJsonConstants.MATCH_END.toLong()
                    } else if (byte3 >= GhostJsonConstants.ASCII_LIMIT) {
                        isPureAscii = false
                    }
                    accumulatedHash = (accumulatedHash shl GhostJsonConstants.HASH_SHIFT) - accumulatedHash + byte3

                    p += 4
                }

                while (p < segmentLimit) {
                    val singleByte = bufferBytes[p - segmentStart].toInt() and GhostJsonConstants.BYTE_MASK
                    if (singleByte < GhostJsonConstants.ASCII_LIMIT &&
                        (escapeMasks[singleByte shr GhostJsonConstants.BITMASK_SHIFT] shr
                                (singleByte and GhostJsonConstants.BITMASK_INDEX_MASK)) and GhostJsonConstants.BITMASK_UNIT != GhostJsonConstants.RESULT_NONE
                    ) {
                        if (singleByte == GhostJsonConstants.QUOTE_INT) {
                            return GhostJsonConstants.packScanResult(p - start, accumulatedHash, isPureAscii)
                        }
                        return GhostJsonConstants.MATCH_END.toLong()
                    } else if (singleByte >= GhostJsonConstants.ASCII_LIMIT) {
                        isPureAscii = false
                    }
                    accumulatedHash = (accumulatedHash shl GhostJsonConstants.HASH_SHIFT) - accumulatedHash + singleByte
                    p++
                }

                currentPosition = p
                if (currentPosition >= limit) return GhostJsonConstants.MATCH_END.toLong()
            } else {
                getSlow(currentPosition)
                if (bufferStart == -1 || currentPosition >= bufferEnd) return GhostJsonConstants.MATCH_END.toLong()
            }
        }
    }

    override fun contentEqualsString(
        start: Int,
        length: Int,
        str: String
    ): Boolean {
        var currentPosition = start
        if (str.length != length) return false

        while (true) {
            val segmentStart = bufferStart
            val segmentEnd = bufferEnd
            if (currentPosition >= segmentStart && currentPosition < segmentEnd) {
                val segmentLimit = minOf(start + length, segmentEnd)
                var p = currentPosition

                while (p < segmentLimit) {
                    val byteValue = bufferBytes[p - segmentStart].toInt() and GhostJsonConstants.BYTE_MASK
                    if (byteValue != str[p - start].code) return false
                    p++
                }

                currentPosition = p
                if (currentPosition >= start + length) return true
            } else {
                getSlow(currentPosition)
                if (bufferStart == -1 || currentPosition >= bufferEnd) return false
            }
        }
    }
}
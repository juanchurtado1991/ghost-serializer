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
        if (index in bufferStart..<bufferEnd) {
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
        // Buffer.read(sink, offset, byteCount) reads only UP TO byteCount bytes per call (same
        // "may return fewer than requested" contract as InputStream.read) -- when [toCopy] spans
        // two of Okio's own internal segments (also 8192 bytes), a single call silently stops at
        // the first segment boundary, leaving the rest of [bufferBytes] as stale/zeroed data with
        // no error. Loop until the exact byte count copied above is fully drained.
        val bytesToDrain = toCopy.toInt()
        var bytesDrained = 0
        while (bytesDrained < bytesToDrain) {
            val bytesReadThisCall = tempBuffer.read(
                sink = bufferBytes,
                offset = bytesDrained,
                byteCount = bytesToDrain - bytesDrained
            )
            if (bytesReadThisCall == -1) break
            bytesDrained += bytesReadThisCall
        }
        bufferStart = alignedStart
        bufferEnd = (alignedStart + toCopy).toInt()

        return bufferBytes[index - bufferStart].toInt() and GhostJsonConstants.BYTE_MASK
    }

    override fun decodeToString(start: Int, end: Int): String {
        val length = end - start
        val segmentStart = bufferStart
        val segmentEnd = bufferEnd
        if (start >= segmentStart && end <= segmentEnd) {
            return bufferBytes.decodeToString(start - segmentStart, end - segmentStart)
        }
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
        val localByteMask = GhostJsonConstants.BYTE_MASK
        val localSpaceInt = GhostJsonConstants.SPACE_INT
        val localWhitespaceMask = GhostJsonConstants.WHITESPACE_MASK
        val localByteShiftUnit = GhostJsonConstants.BYTE_SHIFT_UNIT
        val localResultNone = GhostJsonConstants.RESULT_NONE

        while (true) {
            val segmentStart = bufferStart
            val segmentEnd = bufferEnd
            if (currentPosition >= segmentStart && currentPosition < segmentEnd) {
                val segmentLimit = minOf(limit, segmentEnd)
                var localPosition = currentPosition

                while (localPosition + 3 < segmentLimit) {
                    val byte0 = bufferBytes[localPosition - segmentStart].toInt() and localByteMask
                    if (byte0 > localSpaceInt ||
                        (localWhitespaceMask shr byte0) and localByteShiftUnit == localResultNone
                    ) return localPosition

                    val byte1 = bufferBytes[localPosition + 1 - segmentStart].toInt() and localByteMask
                    if (byte1 > localSpaceInt ||
                        (localWhitespaceMask shr byte1) and localByteShiftUnit == localResultNone
                    ) return localPosition + 1

                    val byte2 = bufferBytes[localPosition + 2 - segmentStart].toInt() and localByteMask
                    if (byte2 > localSpaceInt ||
                        (localWhitespaceMask shr byte2) and localByteShiftUnit == localResultNone
                    ) return localPosition + 2

                    val byte3 = bufferBytes[localPosition + 3 - segmentStart].toInt() and localByteMask
                    if (byte3 > localSpaceInt ||
                        (localWhitespaceMask shr byte3) and localByteShiftUnit == localResultNone
                    ) return localPosition + 3

                    localPosition += 4
                }

                while (localPosition < segmentLimit) {
                    val singleByte = bufferBytes[localPosition - segmentStart].toInt() and localByteMask
                    if (singleByte > localSpaceInt ||
                        (localWhitespaceMask shr singleByte) and localByteShiftUnit == localResultNone
                    ) return localPosition
                    localPosition++
                }

                currentPosition = localPosition
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
        val localByteMask = GhostJsonConstants.BYTE_MASK
        val localAsciiLimit = GhostJsonConstants.ASCII_LIMIT
        val localBitmaskShift = GhostJsonConstants.BITMASK_SHIFT
        val localBitmaskIndexMask = GhostJsonConstants.BITMASK_INDEX_MASK
        val localBitmaskUnit = GhostJsonConstants.BITMASK_UNIT
        val localResultNone = GhostJsonConstants.RESULT_NONE
        val localQuoteInt = GhostJsonConstants.QUOTE_INT

        while (true) {
            val segmentStart = bufferStart
            val segmentEnd = bufferEnd
            if (currentPosition in segmentStart..<segmentEnd) {
                val segmentLimit = minOf(limit, segmentEnd)
                var localPosition = currentPosition

                while (localPosition + 3 < segmentLimit) {
                    val byte0 = bufferBytes[localPosition - segmentStart].toInt() and localByteMask
                    if (byte0 < localAsciiLimit &&
                        (escapeMasks[byte0 shr localBitmaskShift] shr
                                (byte0 and localBitmaskIndexMask)) and localBitmaskUnit != localResultNone
                    ) {
                        if (byte0 == localQuoteInt) return localPosition
                        return -1
                    }
                    val byte1 = bufferBytes[localPosition + 1 - segmentStart].toInt() and localByteMask
                    if (byte1 < localAsciiLimit &&
                        (escapeMasks[byte1 shr localBitmaskShift] shr
                                (byte1 and localBitmaskIndexMask)) and localBitmaskUnit != localResultNone
                    ) {
                        if (byte1 == localQuoteInt) return localPosition + 1
                        return -1
                    }
                    val byte2 = bufferBytes[localPosition + 2 - segmentStart].toInt() and localByteMask
                    if (byte2 < localAsciiLimit &&
                        (escapeMasks[byte2 shr localBitmaskShift] shr
                                (byte2 and localBitmaskIndexMask)) and localBitmaskUnit != localResultNone
                    ) {
                        if (byte2 == localQuoteInt) return localPosition + 2
                        return -1
                    }
                    val byte3 = bufferBytes[localPosition + 3 - segmentStart].toInt() and localByteMask
                    if (byte3 < localAsciiLimit &&
                        (escapeMasks[byte3 shr localBitmaskShift] shr
                                (byte3 and localBitmaskIndexMask)) and localBitmaskUnit != localResultNone
                    ) {
                        if (byte3 == localQuoteInt) return localPosition + 3
                        return -1
                    }
                    localPosition += 4
                }

                while (localPosition < segmentLimit) {
                    val singleByte = bufferBytes[localPosition - segmentStart].toInt() and localByteMask
                    if (singleByte < localAsciiLimit &&
                        (escapeMasks[singleByte shr localBitmaskShift] shr
                                (singleByte and localBitmaskIndexMask)) and localBitmaskUnit != localResultNone
                    ) {
                        if (singleByte == localQuoteInt) return localPosition
                        return -1
                    }
                    localPosition++
                }

                currentPosition = localPosition
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
        val localByteMask = GhostJsonConstants.BYTE_MASK
        val localAsciiLimit = GhostJsonConstants.ASCII_LIMIT
        val localBitmaskShift = GhostJsonConstants.BITMASK_SHIFT
        val localBitmaskIndexMask = GhostJsonConstants.BITMASK_INDEX_MASK
        val localBitmaskUnit = GhostJsonConstants.BITMASK_UNIT
        val localResultNone = GhostJsonConstants.RESULT_NONE
        val localQuoteInt = GhostJsonConstants.QUOTE_INT
        val localHashShift = GhostJsonConstants.HASH_SHIFT
        val localMatchEnd = GhostJsonConstants.MATCH_END

        while (true) {
            val segmentStart = bufferStart
            val segmentEnd = bufferEnd
            if (currentPosition >= segmentStart && currentPosition < segmentEnd) {
                val segmentLimit = minOf(limit, segmentEnd)
                var localPosition = currentPosition

                while (localPosition + 3 < segmentLimit) {
                    val byte0 = bufferBytes[localPosition - segmentStart].toInt() and localByteMask
                    if (byte0 < localAsciiLimit &&
                        (escapeMasks[byte0 shr localBitmaskShift] shr
                                (byte0 and localBitmaskIndexMask)) and localBitmaskUnit != localResultNone
                    ) {
                        if (byte0 == localQuoteInt) {
                            return GhostJsonConstants.packScanResult(localPosition - start, accumulatedHash, isPureAscii)
                        }
                        return localMatchEnd.toLong()
                    } else if (byte0 >= localAsciiLimit) {
                        isPureAscii = false
                    }
                    accumulatedHash = (accumulatedHash shl localHashShift) - accumulatedHash + byte0

                    val byte1 = bufferBytes[localPosition + 1 - segmentStart].toInt() and localByteMask
                    if (byte1 < localAsciiLimit &&
                        (escapeMasks[byte1 shr localBitmaskShift] shr
                                (byte1 and localBitmaskIndexMask)) and localBitmaskUnit != localResultNone
                    ) {
                        if (byte1 == localQuoteInt) {
                            return GhostJsonConstants.packScanResult(
                                localPosition + 1 - start, accumulatedHash,
                                isPureAscii
                            )
                        }
                        return localMatchEnd.toLong()
                    } else if (byte1 >= localAsciiLimit) {
                        isPureAscii = false
                    }
                    accumulatedHash = (accumulatedHash shl localHashShift) - accumulatedHash + byte1

                    val byte2 = bufferBytes[localPosition + 2 - segmentStart].toInt() and localByteMask
                    if (byte2 < localAsciiLimit &&
                        (escapeMasks[byte2 shr localBitmaskShift] shr
                                (byte2 and localBitmaskIndexMask)) and localBitmaskUnit != localResultNone
                    ) {
                        if (byte2 == localQuoteInt) {
                            return GhostJsonConstants.packScanResult(
                                localPosition + 2 - start, accumulatedHash,
                                isPureAscii
                            )
                        }
                        return localMatchEnd.toLong()
                    } else if (byte2 >= localAsciiLimit) {
                        isPureAscii = false
                    }
                    accumulatedHash = (accumulatedHash shl localHashShift) - accumulatedHash + byte2

                    val byte3 = bufferBytes[localPosition + 3 - segmentStart].toInt() and localByteMask
                    if (byte3 < localAsciiLimit &&
                        (escapeMasks[byte3 shr localBitmaskShift] shr
                                (byte3 and localBitmaskIndexMask)) and localBitmaskUnit != localResultNone
                    ) {
                        if (byte3 == localQuoteInt) {
                            return GhostJsonConstants.packScanResult(
                                localPosition + 3 - start, accumulatedHash,
                                isPureAscii
                            )
                        }
                        return localMatchEnd.toLong()
                    } else if (byte3 >= localAsciiLimit) {
                        isPureAscii = false
                    }
                    accumulatedHash = (accumulatedHash shl localHashShift) - accumulatedHash + byte3

                    localPosition += 4
                }

                while (localPosition < segmentLimit) {
                    val singleByte = bufferBytes[localPosition - segmentStart].toInt() and localByteMask
                    if (singleByte < localAsciiLimit &&
                        (escapeMasks[singleByte shr localBitmaskShift] shr
                                (singleByte and localBitmaskIndexMask)) and localBitmaskUnit != localResultNone
                    ) {
                        if (singleByte == localQuoteInt) {
                            return GhostJsonConstants.packScanResult(
                                localPosition - start, accumulatedHash,
                                isPureAscii
                            )
                        }
                        return localMatchEnd.toLong()
                    } else if (singleByte >= localAsciiLimit) {
                        isPureAscii = false
                    }
                    accumulatedHash = (accumulatedHash shl localHashShift) - accumulatedHash + singleByte
                    localPosition++
                }

                currentPosition = localPosition
                if (currentPosition >= limit) return localMatchEnd.toLong()
            } else {
                getSlow(currentPosition)
                if (bufferStart == -1 || currentPosition >= bufferEnd) return localMatchEnd.toLong()
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
            if (currentPosition in segmentStart..<segmentEnd) {
                val segmentLimit = minOf(start + length, segmentEnd)
                var localPosition = currentPosition

                while (localPosition < segmentLimit) {
                    val byteValue = bufferBytes[localPosition - segmentStart].toInt() and GhostJsonConstants.BYTE_MASK
                    if (byteValue != str[localPosition - start].code) return false
                    localPosition++
                }

                currentPosition = localPosition
                if (currentPosition >= start + length) return true
            } else {
                getSlow(currentPosition)
                if (bufferStart == -1 || currentPosition >= bufferEnd) return false
            }
        }
    }
}
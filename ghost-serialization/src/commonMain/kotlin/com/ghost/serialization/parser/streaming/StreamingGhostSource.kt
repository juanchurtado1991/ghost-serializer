package com.ghost.serialization.parser.streaming

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.bytes.ghostReadLong8
import com.ghost.serialization.parser.common.GhostHeuristics
import com.ghost.serialization.parser.common.GhostJsonConstants
import com.ghost.serialization.parser.common.GhostSource
import com.ghost.serialization.parser.common.rollingHashImpl
import com.ghost.serialization.parser.common.swarHasZeroByte
import okio.Buffer
import okio.BufferedSource
import okio.ByteString


/**
 * Implementation of [GhostSource] for streaming data from an `okio.BufferedSource`.
 * Automatically requests data from the source as needed.
 *
 * ## Sliding consume
 *
 * Absolute indices stay stable for the parser, but bytes already behind the reader's
 * logical position are skipped via `okio.BufferedSource.skip` so Okio's buffer does not retain the
 * entire document. [releaseBefore] is driven by [GhostJsonReader] (not by [get] alone):
 * discriminator peek reads ahead without advancing the reader, and must not discard the
 * prefix the reader still needs.
 *
 * [pin] / [unpin] protect ranges that may still be re-read during resilient decode
 * rollback or raw-JSON capture materialization on [GhostJsonReader] /
 * [GhostJsonFlatReader].
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

    /** Absolute start index of the bytes currently cached in [bufferBytes]. */
    private var bufferStart = -1

    /** Absolute end index (exclusive) of the bytes currently cached in [bufferBytes]. */
    private var bufferEnd = -1

    /**
     * Absolute index corresponding to Okio buffer offset 0.
     * Bytes in `[0, discarded)` have been skipped and are no longer addressable.
     */
    internal var discarded: Int = 0
        private set

    private var pinStack = IntArray(PIN_STACK_INITIAL_CAPACITY)
    private var pinCount = 0

    override fun get(index: Int): Int {
        if (index in bufferStart..<bufferEnd) {
            return bufferBytes[index - bufferStart].toInt() and GhostJsonConstants.BYTE_MASK
        }
        return getSlow(index)
    }

    override fun byteOrEof(index: Int): Int {
        if (index in bufferStart..<bufferEnd) {
            return bufferBytes[index - bufferStart].toInt() and GhostJsonConstants.BYTE_MASK
        }
        if (index < discarded) return GhostJsonConstants.MATCH_END
        // request() pulls from the underlying source and reports whether the byte exists,
        // which is the only way to bounds-check a stream of unknown length.
        if (!okioSource.request((index - discarded).toLong() + 1L)) {
            return GhostJsonConstants.MATCH_END
        }
        return getSlow(index)
    }

    /**
     * Keeps bytes at and after [absoluteIndex] available even if [releaseBefore] is called
     * with a higher reader position (nested pins supported).
     */
    fun pin(absoluteIndex: Int) {
        if (pinCount == pinStack.size) {
            pinStack = pinStack.copyOf(pinStack.size * 2)
        }
        pinStack[pinCount++] = absoluteIndex
    }

    fun unpin() {
        if (pinCount > 0) pinCount--
    }

    /**
     * Skips Okio prefix bytes that the reader will not need again.
     *
     * Retains at least one [GhostJsonConstants.STREAMING_BUFFER_SIZE] window behind
     * [absoluteIndex], and never discards past any active [pin]. No-ops until at least
     * one full window can be skipped (avoids thrashing on small advances).
     */
    fun releaseBefore(absoluteIndex: Int) {
        if (absoluteIndex <= discarded || absoluteIndex == Int.MAX_VALUE) return

        var retainFrom = (absoluteIndex - GhostJsonConstants.STREAMING_BUFFER_SIZE).coerceAtLeast(0)
        var i = 0
        while (i < pinCount) {
            retainFrom = minOf(retainFrom, pinStack[i])
            i++
        }
        val aligned = (retainFrom / GhostJsonConstants.STREAMING_BUFFER_SIZE) *
                GhostJsonConstants.STREAMING_BUFFER_SIZE
        val toSkip = aligned - discarded
        if (toSkip < GhostJsonConstants.STREAMING_BUFFER_SIZE) return

        okioSource.skip(toSkip.toLong())
        discarded += toSkip

        if (bufferEnd <= discarded || bufferStart < discarded) {
            bufferStart = -1
            bufferEnd = -1
        }
    }

    private fun getSlow(index: Int): Int {
        if (index < discarded) {
            throw IndexOutOfBoundsException(
                "Index $index is below discarded prefix ($discarded)"
            )
        }
        val relativeIndex = (index - discarded).toLong()
        okioSource.request(relativeIndex + 1L)
        val available = buffer.size
        if (relativeIndex >= available) {
            throw IndexOutOfBoundsException(
                "Index $index is out of bounds (available absolute end: ${discarded + available})"
            )
        }

        val alignedStart =
            (index / GhostJsonConstants.STREAMING_BUFFER_SIZE) * GhostJsonConstants.STREAMING_BUFFER_SIZE
        val windowStart = maxOf(alignedStart, discarded)
        val windowStartRel = (windowStart - discarded).toLong()
        val alignedEnd = alignedStart + GhostJsonConstants.STREAMING_BUFFER_SIZE
        val absoluteAvailableEnd = discarded + available.toInt()
        val windowEnd = minOf(alignedEnd, absoluteAvailableEnd)
        val toCopy = (windowEnd - windowStart).toLong()

        if (toCopy <= 0L) {
            throw IndexOutOfBoundsException("Index $index is out of bounds")
        }

        buffer.copyTo(tempBuffer, windowStartRel, toCopy)
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
        bufferStart = windowStart
        bufferEnd = windowStart + toCopy.toInt()

        return bufferBytes[index - bufferStart].toInt() and GhostJsonConstants.BYTE_MASK
    }

    override fun decodeToString(start: Int, end: Int): String {
        val length = end - start
        val segmentStart = bufferStart
        val segmentEnd = bufferEnd
        if (start >= segmentStart && end <= segmentEnd) {
            return bufferBytes.decodeToString(start - segmentStart, end - segmentStart)
        }
        if (start < discarded) {
            throw IndexOutOfBoundsException(
                "decodeToString start $start is below discarded prefix ($discarded)"
            )
        }
        val relativeEnd = (end - discarded).toLong()
        okioSource.request(relativeEnd)
        // copyTo fills the reusable tempBuffer with exactly [length] bytes from the live Okio
        // buffer without advancing its read position.  readUtf8 then decodes them in one pass
        // and returns the final String — no intermediate ByteString, no snapshot, no substring.
        buffer.copyTo(tempBuffer, (start - discarded).toLong(), length.toLong())
        return tempBuffer.readUtf8(length.toLong())
    }

    override fun contentEquals(start: Int, expected: ByteString): Boolean {
        if (start < discarded) return false
        return okioSource.rangeEquals((start - discarded).toLong(), expected)
    }

    override fun findNextNonWhitespace(position: Int, limit: Int): Int {
        var currentPosition = position
        val localByteMask = GhostJsonConstants.BYTE_MASK
        val localSpaceInt = GhostJsonConstants.SPACE_INT
        val localWhitespaceMask = GhostJsonConstants.WHITESPACE_MASK
        val localByteShiftUnit = GhostJsonConstants.BYTE_SHIFT_UNIT
        val localResultNone = GhostJsonConstants.RESULT_NONE
        val longBytes = GhostJsonConstants.LONG_BYTES
        val spaceRun = GhostJsonConstants.SPACE_RUN_LONG

        while (true) {
            val segmentStart = bufferStart
            val segmentEnd = bufferEnd
            if (currentPosition >= segmentStart && currentPosition < segmentEnd) {
                val segmentLimit = minOf(limit, segmentEnd)
                var localPosition = currentPosition
                val base = segmentStart

                // SWAR: swallow 8-byte runs of ASCII space within the current window.
                while (localPosition + longBytes <= segmentLimit &&
                    ghostReadLong8(bufferBytes, localPosition - base) == spaceRun
                ) {
                    localPosition += longBytes
                }

                while (localPosition + 3 < segmentLimit) {
                    val byte0 = bufferBytes[localPosition - base].toInt() and localByteMask
                    if (byte0 > localSpaceInt ||
                        (localWhitespaceMask shr byte0) and localByteShiftUnit == localResultNone
                    ) return localPosition

                    val byte1 = bufferBytes[localPosition + 1 - base].toInt() and localByteMask
                    if (byte1 > localSpaceInt ||
                        (localWhitespaceMask shr byte1) and localByteShiftUnit == localResultNone
                    ) return localPosition + 1

                    val byte2 = bufferBytes[localPosition + 2 - base].toInt() and localByteMask
                    if (byte2 > localSpaceInt ||
                        (localWhitespaceMask shr byte2) and localByteShiftUnit == localResultNone
                    ) return localPosition + 2

                    val byte3 = bufferBytes[localPosition + 3 - base].toInt() and localByteMask
                    if (byte3 > localSpaceInt ||
                        (localWhitespaceMask shr byte3) and localByteShiftUnit == localResultNone
                    ) return localPosition + 3

                    localPosition += 4
                }

                while (localPosition < segmentLimit) {
                    val singleByte = bufferBytes[localPosition - base].toInt() and localByteMask
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
                    val byte1 =
                        bufferBytes[localPosition + 1 - segmentStart].toInt() and localByteMask
                    if (byte1 < localAsciiLimit &&
                        (escapeMasks[byte1 shr localBitmaskShift] shr
                                (byte1 and localBitmaskIndexMask)) and localBitmaskUnit != localResultNone
                    ) {
                        if (byte1 == localQuoteInt) return localPosition + 1
                        return -1
                    }
                    val byte2 =
                        bufferBytes[localPosition + 2 - segmentStart].toInt() and localByteMask
                    if (byte2 < localAsciiLimit &&
                        (escapeMasks[byte2 shr localBitmaskShift] shr
                                (byte2 and localBitmaskIndexMask)) and localBitmaskUnit != localResultNone
                    ) {
                        if (byte2 == localQuoteInt) return localPosition + 2
                        return -1
                    }
                    val byte3 =
                        bufferBytes[localPosition + 3 - segmentStart].toInt() and localByteMask
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
                    val singleByte =
                        bufferBytes[localPosition - segmentStart].toInt() and localByteMask
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
        var isPureAscii = true
        val escapeMasks = GhostJsonConstants.ESCAPE_MASKS
        val localByteMask = GhostJsonConstants.BYTE_MASK
        val localAsciiLimit = GhostJsonConstants.ASCII_LIMIT
        val localBitmaskShift = GhostJsonConstants.BITMASK_SHIFT
        val localBitmaskIndexMask = GhostJsonConstants.BITMASK_INDEX_MASK
        val localBitmaskUnit = GhostJsonConstants.BITMASK_UNIT
        val localResultNone = GhostJsonConstants.RESULT_NONE
        val localQuoteInt = GhostJsonConstants.QUOTE_INT
        val localMatchEnd = GhostJsonConstants.MATCH_END
        val longBytes = GhostJsonConstants.LONG_BYTES
        val spaceRun = GhostJsonConstants.SPACE_RUN_LONG
        val swarHighs = GhostJsonConstants.SWAR_HIGHS
        val swarQuotes = GhostJsonConstants.SWAR_QUOTES
        val swarBackslashes = GhostJsonConstants.SWAR_BACKSLASHES
        val maxPoolLen = GhostHeuristics.maxStringPoolLength

        while (true) {
            val segmentStart = bufferStart
            val segmentEnd = bufferEnd
            if (currentPosition >= segmentStart && currentPosition < segmentEnd) {
                val segmentLimit = minOf(limit, segmentEnd)
                var localPosition = currentPosition
                val base = segmentStart

                // SWAR: skip clean LONG_BYTES windows (no quote / backslash / control).
                // Hash is deferred until the closing quote — long values are never pooled.
                while (localPosition + longBytes <= segmentLimit) {
                    val w = ghostReadLong8(bufferBytes, localPosition - base)
                    val hasQuote = swarHasZeroByte(w xor swarQuotes)
                    val hasBackslash = swarHasZeroByte(w xor swarBackslashes)
                    val hasControl = (w - spaceRun) and w.inv() and swarHighs
                    if ((hasQuote or hasBackslash or hasControl) != localResultNone) {
                        break
                    }
                    if ((w and swarHighs) != localResultNone) {
                        isPureAscii = false
                    }
                    localPosition += longBytes
                }

                while (localPosition < segmentLimit) {
                    val singleByte = bufferBytes[localPosition - base].toInt() and localByteMask
                    if (singleByte < localAsciiLimit &&
                        (escapeMasks[singleByte shr localBitmaskShift] shr
                                (singleByte and localBitmaskIndexMask)) and localBitmaskUnit != localResultNone
                    ) {
                        if (singleByte == localQuoteInt) {
                            val length = localPosition - start
                            val hash = if (length > maxPoolLen) {
                                GhostJsonConstants.SCAN_HASH_NONE
                            } else {
                                rollingHashStreaming(start, length)
                            }
                            return GhostJsonConstants.packScanResult(length, hash, isPureAscii)
                        }
                        return localMatchEnd.toLong()
                    } else if (singleByte >= localAsciiLimit) {
                        isPureAscii = false
                    }
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

    /**
     * Rolling hash over `[start, start+length)` for the string pool. Prefers a contiguous
     * [bufferBytes] slice; falls back to [get] when the span crosses window boundaries.
     */
    private fun rollingHashStreaming(start: Int, length: Int): Int {
        val segmentStart = bufferStart
        val segmentEnd = bufferEnd
        if (start >= segmentStart && start + length <= segmentEnd) {
            return rollingHashImpl(bufferBytes, start - segmentStart, length)
        }
        var accumulatedHash = GhostJsonConstants.SCAN_HASH_NONE
        val hashShift = GhostJsonConstants.HASH_SHIFT
        var i = 0
        while (i < length) {
            accumulatedHash = (accumulatedHash shl hashShift) - accumulatedHash + get(start + i)
            i++
        }
        return accumulatedHash
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
                    val byteValue =
                        bufferBytes[localPosition - segmentStart].toInt() and GhostJsonConstants.BYTE_MASK
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

    private companion object {
        const val PIN_STACK_INITIAL_CAPACITY = 8
    }
}

@file:Suppress("NOTHING_TO_INLINE")

package com.ghost.serialization.parser.streaming

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.bytes.ghostReadLong8
import com.ghost.serialization.parser.common.AbstractGhostSource
import com.ghost.serialization.parser.common.GhostHeuristics
import com.ghost.serialization.parser.common.GhostSource
import com.ghost.serialization.parser.common.isEscapeOrControlByte
import com.ghost.serialization.parser.common.isNonWhitespace
import com.ghost.serialization.parser.common.rollingHashImpl
import com.ghost.serialization.parser.common.swarHasZeroByte
import okio.Buffer
import okio.BufferedSource
import okio.ByteString
import com.ghost.serialization.parser.common.constants.GhostJsonErrorMessages as EM
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants as SCN
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants as WR

/**
 * Implementation of [GhostSource] for streaming data from an `okio.BufferedSource`.
 * Automatically requests data from the source as needed.
 *
 * ## Sliding consume
 *
 * Absolute indices stay stable for the parser, but bytes already behind the reader's logical
 * position are skipped via `okio.BufferedSource.skip` so Okio's buffer does not retain the
 * entire document — [discarded] is the absolute index at Okio buffer offset 0, and bytes before
 * it are no longer addressable. [releaseBefore] retains at least one [WR.STREAMING_BUFFER_SIZE]
 * window behind its target index, no-ops until a full window can be skipped (avoids thrashing on
 * small advances), and is driven by [GhostJsonReader] rather than [get] alone: discriminator peek
 * reads ahead without advancing the reader, and must not discard the prefix it still needs.
 *
 * [pin] / [unpin] protect ranges — even nested — that may still be re-read during resilient
 * decode rollback or raw-JSON capture materialization on [GhostJsonReader] /
 * [com.ghost.serialization.parser.bytes.GhostJsonFlatReader]; [releaseBefore] never discards
 * past an active pin.
 *
 * [bufferStart]/[bufferEnd] bound the currently cached [bufferBytes] window as absolute indices;
 * [tempBuffer] is reused across every [getSlow] and [decodeToString] call to avoid
 * per-operation allocations.
 */
@InternalGhostApi
class StreamingGhostSource(val okioSource: BufferedSource) : AbstractGhostSource() {

    private val buffer = okioSource.buffer
    private val tempBuffer = Buffer()
    override val size: Int get() = Int.MAX_VALUE
    private val bufferBytes = ByteArray(WR.STREAMING_BUFFER_SIZE)
    private var bufferStart = -1
    private var bufferEnd = -1

    internal var discarded: Int = 0
        private set

    private var pinStack = IntArray(PIN_STACK_INITIAL_CAPACITY)
    private var pinCount = 0

    override fun byteOrEof(index: Int): Int {
        if (index in bufferStart..<bufferEnd) {
            return bufferBytes[index - bufferStart].toInt() and TOK.BYTE_MASK
        }
        if (index < discarded) return SCN.MATCH_END
        // request() pulls from the underlying source and reports whether the byte exists,
        // which is the only way to bounds-check a stream of unknown length.
        if (!okioSource.request((index - discarded).toLong() + 1L)) {
            return SCN.MATCH_END
        }
        return getSlow(index = index)
    }

    override fun get(index: Int): Int {
        if (index in bufferStart..<bufferEnd) {
            return bufferBytes[index - bufferStart].toInt() and TOK.BYTE_MASK
        }
        return getSlow(index = index)
    }

    fun pin(absoluteIndex: Int) {
        if (pinCount == pinStack.size) {
            pinStack = pinStack.copyOf(pinStack.size * 2)
        }
        pinStack[pinCount++] = absoluteIndex
    }

    fun releaseBefore(absoluteIndex: Int) {
        if (absoluteIndex <= discarded || absoluteIndex == Int.MAX_VALUE) return

        var retainFrom = (absoluteIndex - WR.STREAMING_BUFFER_SIZE).coerceAtLeast(0)
        var pinIndex = 0
        while (pinIndex < pinCount) {
            retainFrom = minOf(retainFrom, pinStack[pinIndex])
            pinIndex++
        }
        val aligned = (retainFrom / WR.STREAMING_BUFFER_SIZE) *
                WR.STREAMING_BUFFER_SIZE
        val toSkip = aligned - discarded
        if (toSkip < WR.STREAMING_BUFFER_SIZE) return

        okioSource.skip(toSkip.toLong())
        discarded += toSkip

        if (bufferEnd <= discarded || bufferStart < discarded) {
            bufferStart = -1
            bufferEnd = -1
        }
    }

    fun unpin() {
        if (pinCount > 0) pinCount--
    }

    private fun getSlow(index: Int): Int {
        if (index < discarded) {
            throw IndexOutOfBoundsException(
                "${EM.ERR_INDEX_BELOW_DISCARDED_PREFIX}$index${EM.ERR_INDEX_BELOW_DISCARDED_MID}" +
                    "$discarded${EM.ERR_INDEX_BELOW_DISCARDED_SUFFIX}"
            )
        }
        val relativeIndex = (index - discarded).toLong()
        okioSource.request(relativeIndex + 1L)
        val available = buffer.size
        if (relativeIndex >= available) {
            throw IndexOutOfBoundsException(
                "${EM.ERR_INDEX_OOB_PREFIX}$index${EM.ERR_INDEX_OOB_MID}" +
                    "${discarded + available}${EM.ERR_INDEX_OOB_SUFFIX}"
            )
        }

        val alignedStart =
            (index / WR.STREAMING_BUFFER_SIZE) * WR.STREAMING_BUFFER_SIZE
        val windowStart = maxOf(alignedStart, discarded)
        val windowStartRel = (windowStart - discarded).toLong()
        val alignedEnd = alignedStart + WR.STREAMING_BUFFER_SIZE
        val absoluteAvailableEnd = discarded + available.toInt()
        val windowEnd = minOf(alignedEnd, absoluteAvailableEnd)
        val toCopy = (windowEnd - windowStart).toLong()

        if (toCopy <= 0L) {
            throw IndexOutOfBoundsException("${EM.ERR_INDEX_OOB_PREFIX}$index${EM.ERR_INDEX_OOB}")
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

        return bufferBytes[index - bufferStart].toInt() and TOK.BYTE_MASK
    }

    override fun contentEquals(start: Int, expected: ByteString): Boolean {
        if (start < discarded) return false
        return okioSource.rangeEquals((start - discarded).toLong(), expected)
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
                "${EM.ERR_DECODE_START_BELOW_DISCARDED_PREFIX}$start${EM.ERR_DECODE_START_BELOW_DISCARDED_MID}" +
                    "$discarded${EM.ERR_INDEX_BELOW_DISCARDED_SUFFIX}"
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

    override fun findNextNonWhitespace(position: Int, limit: Int): Int {
        var currentPosition = position
        val localByteMask = TOK.BYTE_MASK
        val whitespaceMask = SCN.WHITESPACE_MASK
        val longBytes = SCN.LONG_BYTES

        while (true) {
            val segmentStart = bufferStart
            val segmentEnd = bufferEnd
            if (currentPosition in segmentStart..<segmentEnd) {
                val segmentLimit = minOf(limit, segmentEnd)
                var localPosition = currentPosition

                // SWAR: swallow 8-byte runs of ASCII space within the current window.
                while (hasFullSpaceWindowAt(
                    localPosition = localPosition,
                    segmentStart = segmentStart,
                    segmentLimit = segmentLimit
                )) {
                    localPosition += longBytes
                }

                while (localPosition + SCN.UNROLL_OFFSET_3 < segmentLimit) {
                    val byte0 = bufferBytes[localPosition - segmentStart].toInt() and localByteMask
                    if (isNonWhitespace(byte = byte0, whitespaceMask = whitespaceMask)) return localPosition

                    val byte1 = bufferBytes[localPosition + SCN.UNROLL_OFFSET_1 - segmentStart].toInt() and localByteMask
                    if (isNonWhitespace(
                        byte = byte1,
                        whitespaceMask = whitespaceMask
                    )) return localPosition + SCN.UNROLL_OFFSET_1

                    val byte2 = bufferBytes[localPosition + SCN.UNROLL_OFFSET_2 - segmentStart].toInt() and localByteMask
                    if (isNonWhitespace(
                        byte = byte2,
                        whitespaceMask = whitespaceMask
                    )) return localPosition + SCN.UNROLL_OFFSET_2

                    val byte3 = bufferBytes[localPosition + SCN.UNROLL_OFFSET_3 - segmentStart].toInt() and localByteMask
                    if (isNonWhitespace(
                        byte = byte3,
                        whitespaceMask = whitespaceMask
                    )) return localPosition + SCN.UNROLL_OFFSET_3

                    localPosition += SCN.UNROLL_STEP
                }

                while (localPosition < segmentLimit) {
                    val singleByte = bufferBytes[localPosition - segmentStart].toInt() and localByteMask
                    if (isNonWhitespace(byte = singleByte, whitespaceMask = whitespaceMask)) return localPosition
                    localPosition++
                }

                currentPosition = localPosition
                if (currentPosition >= limit) return -1
            } else {
                getSlow(index = currentPosition)
                if (bufferStart == -1 || currentPosition >= bufferEnd) return -1
            }
        }
    }

    /** Whether a full 8-byte window starting at [localPosition] is all ASCII space (0x20). */
    private inline fun hasFullSpaceWindowAt(localPosition: Int, segmentStart: Int, segmentLimit: Int): Boolean =
        localPosition + SCN.LONG_BYTES <= segmentLimit &&
            ghostReadLong8(data = bufferBytes, index = localPosition - segmentStart) == SCN.SPACE_RUN_LONG

    override fun findClosingQuote(position: Int, limit: Int): Int {
        var currentPosition = position
        val escapeMasks = WR.ESCAPE_MASKS
        val localByteMask = TOK.BYTE_MASK
        val localQuoteInt = TOK.QUOTE_INT

        while (true) {
            val segmentStart = bufferStart
            val segmentEnd = bufferEnd
            if (currentPosition in segmentStart..<segmentEnd) {
                val segmentLimit = minOf(limit, segmentEnd)
                var localPosition = currentPosition

                while (localPosition + SCN.UNROLL_OFFSET_3 < segmentLimit) {
                    val byte0 = bufferBytes[localPosition - segmentStart].toInt() and localByteMask
                    if (isEscapeOrControlByte(byte = byte0, escapeMasks = escapeMasks)) {
                        if (byte0 == localQuoteInt) return localPosition
                        return -1
                    }
                    val byte1 =
                        bufferBytes[localPosition + SCN.UNROLL_OFFSET_1 - segmentStart].toInt() and localByteMask
                    if (isEscapeOrControlByte(byte = byte1, escapeMasks = escapeMasks)) {
                        if (byte1 == localQuoteInt) return localPosition + SCN.UNROLL_OFFSET_1
                        return -1
                    }
                    val byte2 =
                        bufferBytes[localPosition + SCN.UNROLL_OFFSET_2 - segmentStart].toInt() and localByteMask
                    if (isEscapeOrControlByte(byte = byte2, escapeMasks = escapeMasks)) {
                        if (byte2 == localQuoteInt) return localPosition + SCN.UNROLL_OFFSET_2
                        return -1
                    }
                    val byte3 =
                        bufferBytes[localPosition + SCN.UNROLL_OFFSET_3 - segmentStart].toInt() and localByteMask
                    if (isEscapeOrControlByte(byte = byte3, escapeMasks = escapeMasks)) {
                        if (byte3 == localQuoteInt) return localPosition + SCN.UNROLL_OFFSET_3
                        return -1
                    }
                    localPosition += SCN.UNROLL_STEP
                }

                while (localPosition < segmentLimit) {
                    val singleByte =
                        bufferBytes[localPosition - segmentStart].toInt() and localByteMask
                    if (isEscapeOrControlByte(byte = singleByte, escapeMasks = escapeMasks)) {
                        if (singleByte == localQuoteInt) return localPosition
                        return -1
                    }
                    localPosition++
                }

                currentPosition = localPosition
                if (currentPosition >= limit) return -1
            } else {
                getSlow(index = currentPosition)
                if (bufferStart == -1 || currentPosition >= bufferEnd) return -1
            }
        }
    }

    override fun scanString(start: Int, limit: Int): Long {
        var currentPosition = start
        var isPureAscii = true
        val escapeMasks = WR.ESCAPE_MASKS
        val localByteMask = TOK.BYTE_MASK
        val localAsciiLimit = TOK.ASCII_LIMIT
        val localResultNone = SCN.RESULT_NONE
        val localQuoteInt = TOK.QUOTE_INT
        val localMatchEnd = SCN.MATCH_END
        val longBytes = SCN.LONG_BYTES
        val spaceRun = SCN.SPACE_RUN_LONG
        val swarHighs = SCN.SWAR_HIGHS
        val swarQuotes = SCN.SWAR_QUOTES
        val swarBackslashes = SCN.SWAR_BACKSLASHES
        val maxPoolLen = GhostHeuristics.maxStringPoolLength

        while (true) {
            val segmentStart = bufferStart
            val segmentEnd = bufferEnd
            if (currentPosition in segmentStart..<segmentEnd) {
                val segmentLimit = minOf(limit, segmentEnd)
                var localPosition = currentPosition
                val base = segmentStart

                // SWAR: skip clean LONG_BYTES windows (no quote / backslash / control).
                // Hash is deferred until the closing quote — long values are never pooled.
                while (localPosition + longBytes <= segmentLimit) {
                    val packedWindow = ghostReadLong8(data = bufferBytes, index = localPosition - base)
                    val hasQuote = swarHasZeroByte(v = packedWindow xor swarQuotes)
                    val hasBackslash = swarHasZeroByte(v = packedWindow xor swarBackslashes)
                    val hasControl =
                        (packedWindow - spaceRun) and packedWindow.inv() and swarHighs
                    if ((hasQuote or hasBackslash or hasControl) != localResultNone) {
                        break
                    }
                    if ((packedWindow and swarHighs) != localResultNone) {
                        isPureAscii = false
                    }
                    localPosition += longBytes
                }

                while (localPosition < segmentLimit) {
                    val singleByte = bufferBytes[localPosition - base].toInt() and localByteMask
                    if (isEscapeOrControlByte(byte = singleByte, escapeMasks = escapeMasks)) {
                        if (singleByte == localQuoteInt) {
                            val length = localPosition - start
                            val hash = if (length > maxPoolLen) {
                                SCN.SCAN_HASH_NONE
                            } else {
                                rollingHashStreaming(start = start, length = length)
                            }
                            return SCN.packScanResult(length = length, hash = hash, is7Bit = isPureAscii)
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
                getSlow(index = currentPosition)
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
            return rollingHashImpl(data = bufferBytes, start = start - segmentStart, length = length)
        }
        var accumulatedHash = SCN.SCAN_HASH_NONE
        val hashShift = SCN.HASH_SHIFT
        var byteOffset = 0
        while (byteOffset < length) {
            accumulatedHash =
                (accumulatedHash shl hashShift) - accumulatedHash + get(start + byteOffset)
            byteOffset++
        }
        return accumulatedHash
    }

    override fun contentEqualsString(
        start: Int,
        length: Int,
        expected: String
    ): Boolean {
        var currentPosition = start
        if (expected.length != length) return false

        while (true) {
            val segmentStart = bufferStart
            val segmentEnd = bufferEnd
            if (currentPosition in segmentStart..<segmentEnd) {
                val segmentLimit = minOf(start + length, segmentEnd)
                var localPosition = currentPosition

                while (localPosition < segmentLimit) {
                    val byteValue =
                        bufferBytes[localPosition - segmentStart].toInt() and TOK.BYTE_MASK
                    if (byteValue != expected[localPosition - start].code) return false
                    localPosition++
                }

                currentPosition = localPosition
                if (currentPosition >= start + length) return true
            } else {
                getSlow(index = currentPosition)
                if (bufferStart == -1 || currentPosition >= bufferEnd) return false
            }
        }
    }

    private companion object {
        const val PIN_STACK_INITIAL_CAPACITY = 8
    }
}

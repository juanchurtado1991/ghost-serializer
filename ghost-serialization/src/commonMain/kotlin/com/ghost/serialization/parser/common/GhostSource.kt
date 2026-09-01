package com.ghost.serialization.parser.common

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.StreamingGhostSource
import okio.BufferedSource
import okio.ByteString


/**
 * Data source abstraction letting the parser operate on both [ByteArray] and streaming
 * `okio.BufferedSource` through one API.
 *
 * Implementations MUST return bytes as [Int] in 0-255 (unsigned) — prevents sign-extension
 * bugs and allows direct comparisons with [GhostJsonConstants].
 */
@InternalGhostApi
interface GhostSource {
    val size: Int

    operator fun get(index: Int): Int

    /**
     * Like [get], but returns [GhostJsonConstants.MATCH_END] instead of throwing when [index]
     * is past the available data. Streaming sources report [size] as [Int.MAX_VALUE] because
     * the document length is unknown, so callers that speculatively read ahead (such as the
     * in-order field prediction) cannot bounds-check against [size] and need this instead.
     */
    fun byteOrEof(index: Int): Int =
        if (index < size) get(index) else GhostJsonConstants.MATCH_END

    fun decodeToString(start: Int, end: Int): String

    /**
     * Decodes bytes [start, end) as UTF-8. When [isKnown7BitContent] is true, the caller guarantees
     * every byte in the range had bit 7 clear (ASCII); JVM/Android may use a faster decoder.
     */
    fun decodeJsonStringRange(start: Int, end: Int, isKnown7BitContent: Boolean): String =
        decodeToString(start, end)

    fun contentEquals(start: Int, expected: ByteString): Boolean


    /** Access to the raw byte array if the source is backed by one. Returns EMPTY_BYTES otherwise. */
    val rawSourceData: ByteArray get() = GhostJsonConstants.EMPTY_BYTES

    /**
     * Finds the next non-whitespace byte (> 32) starting from [position].
     * Returns the position or -1 if not found.
     */
    fun findNextNonWhitespace(position: Int, limit: Int): Int

    /**
     * Fast-path scan for the closing quote from [position] to [limit]. Returns -1 on a
     * backslash or control character, signaling the parser must fall back to the slow
     * (escaping) path.
     */
    fun findClosingQuote(position: Int, limit: Int): Int

    /**
     * Ultra-fast-path: scans for the closing quote while computing a rolling hash in one
     * pass. Returns the rolling hash (low 31 bits) and length (high 32 bits) packed into a
     * Long, or -1L if an escape/control character is hit.
     */
    fun scanString(start: Int, limit: Int): Long

    /**
     * Compares source bytes against a cached [String] without allocating a new String for
     * the comparison — used by the [GhostJsonReader] string pool.
     */
    fun contentEqualsString(start: Int, length: Int, expected: String): Boolean
}

@InternalGhostApi
expect fun createByteArraySource(data: ByteArray): GhostSource

@InternalGhostApi
fun createSourceBridge(source: BufferedSource): GhostSource =
    StreamingGhostSource(source)

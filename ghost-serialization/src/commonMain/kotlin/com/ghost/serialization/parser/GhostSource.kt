package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import okio.BufferedSource
import okio.ByteString

/**
 * Core data source abstraction for the Ghost JSON parser.
 *
 * This interface allows the parser to operate on both [ByteArray] and streaming [okio.BufferedSource]
 * with a unified, high-performance API.
 *
 * **Key Constraints:**
 * - Implementations MUST return bytes as [Int] values in the range 0-255 (unsigned).
 * - This prevents sign-extension bugs and allows direct comparisons with [GhostJsonConstants].
 */
@InternalGhostApi
interface GhostSource {
    val size: Int

    operator fun get(index: Int): Int

    fun decodeToString(start: Int, end: Int): String

    /**
     * Decodes bytes [start, end) as UTF-8. When [isKnown7BitContent] is true, the caller guarantees
     * every byte in the range had bit 7 clear (ASCII); JVM/Android may use a faster decoder.
     */
    fun decodeJsonStringRange(start: Int, end: Int, isKnown7BitContent: Boolean): String =
        decodeToString(start, end)

    fun contentEquals(start: Int, expected: ByteString): Boolean

    fun copyTo(sink: ByteArray, sinkOffset: Int, start: Int, count: Int)

    /** Access to the raw byte array if the source is backed by one. Returns EMPTY_BYTES otherwise. */
    val rawSourceData: ByteArray get() = GhostJsonConstants.EMPTY_BYTES

    /**
     * Finds the next non-whitespace byte (> 32) starting from [position].
     * Returns the position or -1 if not found.
     */
    fun findNextNonWhitespace(position: Int, limit: Int): Int

    /**
     * Finds the closing quote (") starting from [position], stopping at [limit].
     *
     * This is a "Fast-Path" scan:
     * - Returns the position of the closing quote.
     * - Returns -1 if it hits a backslash (\) or control character, signaling that the
     *   parser must switch to the "Slow-Path" ( StringBuilder/escaping logic).
     */
    fun findClosingQuote(position: Int, limit: Int): Int

    /**
     * Scans for the closing quote while calculating a rolling hash in a single pass.
     *
     * This is the "Ultra-Fast-Path" for string reading:
     * - Updates [reader] position to the closing quote position.
     * - Returns the rolling hash of the string content.
     * - Returns -1 if an escape sequence or control character is encountered.
     */
    fun scanString(start: Int, limit: Int, reader: GhostJsonReader): Int

    /**
     * Calculates the rolling hash of the bytes at [start] for [length].
     * Default implementation; platforms should override this for speed.
     */
    fun calculateHash(start: Int, length: Int): Int

    /**
     * Compares the source bytes against a cached [String] instance.
     *
     * This optimization is crucial for the [GhostJsonReader] string pool.
     * It allows checking if a pool-cached string matches the source bytes WITHOUT
     * allocating a new [String] for the comparison.
     */
    fun contentEqualsString(start: Int, length: Int, str: String): Boolean
}

@InternalGhostApi
expect fun createByteArraySource(data: ByteArray): GhostSource

@InternalGhostApi
fun createSourceBridge(source: BufferedSource): GhostSource =
    StreamingGhostSource(source)

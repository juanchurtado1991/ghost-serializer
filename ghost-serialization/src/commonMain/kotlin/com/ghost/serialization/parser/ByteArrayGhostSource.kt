package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.GhostJsonConstants.BYTE_MASK

/**
 * High-performance [GhostSource] backed by an in-memory [ByteArray].
 *
 * Contains all loop-unrolled hot-path scanning logic shared across every platform.
 * JVM and Android subclass this to override [decodeJsonStringRange] with a faster
 * ASCII decoder; all other platforms use this class directly.
 */
@InternalGhostApi
open class ByteArrayGhostSource(var data: ByteArray) : GhostSource {

    override val size: Int get() = data.size

    override fun get(index: Int): Int = data[index].toInt() and BYTE_MASK

    override val rawSourceData: ByteArray get() = data

    override fun decodeToString(start: Int, end: Int): String =
        data.decodeToString(start, end)

    override fun contentEquals(start: Int, expected: okio.ByteString): Boolean {
        if (start + expected.size > size) return false
        return expected.rangeEquals(0, data, start, expected.size)
    }

    override fun findNextNonWhitespace(position: Int, limit: Int): Int {
        val localData = data
        return findNextNonWhitespaceImpl(position, limit) { localData[it].toInt() and BYTE_MASK }
    }

    override fun findClosingQuote(position: Int, limit: Int): Int {
        val localData = data
        return findClosingQuoteImpl(position, limit) { localData[it].toInt() and BYTE_MASK }
    }

    override fun scanString(start: Int, limit: Int): Long {
        val localData = data
        return scanStringImpl(start, limit) { localData[it].toInt() and BYTE_MASK }
    }

    override fun contentEqualsString(start: Int, length: Int, str: String): Boolean {
        val localData = data
        return contentEqualsStringImpl(start, length, str) { localData[it].toInt() and BYTE_MASK }
    }
}

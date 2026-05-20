package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.GhostJsonConstants.BYTE_MASK
import okio.ByteString

/**
 * [GhostSource] backed by an Okio [ByteString] (e.g. [okio.Buffer.snapshot]) without
 * copying into a [ByteArray]. Used by Retrofit/Ktor network adapters.
 */
@InternalGhostApi
class ByteStringGhostSource(val bytes: ByteString) : GhostSource {

    override val size: Int get() = bytes.size

    override fun get(index: Int): Int = bytes[index].toInt() and BYTE_MASK

    override fun decodeToString(start: Int, end: Int): String =
        bytes.substring(start, end).utf8()

    override fun contentEquals(start: Int, expected: ByteString): Boolean {
        if (start + expected.size > size) return false
        return bytes.rangeEquals(start, expected, 0, expected.size)
    }

    override fun findNextNonWhitespace(position: Int, limit: Int): Int {
        val localBytes = bytes
        return findNextNonWhitespaceImpl(position, limit) { localBytes[it].toInt() and BYTE_MASK }
    }

    override fun findClosingQuote(position: Int, limit: Int): Int {
        val localBytes = bytes
        return findClosingQuoteImpl(position, limit) { localBytes[it].toInt() and BYTE_MASK }
    }

    override fun scanString(start: Int, limit: Int): Long {
        val localBytes = bytes
        return scanStringImpl(start, limit) { localBytes[it].toInt() and BYTE_MASK }
    }

    override fun contentEqualsString(start: Int, length: Int, str: String): Boolean {
        val localBytes = bytes
        return contentEqualsStringImpl(start, length, str) { localBytes[it].toInt() and BYTE_MASK }
    }
}

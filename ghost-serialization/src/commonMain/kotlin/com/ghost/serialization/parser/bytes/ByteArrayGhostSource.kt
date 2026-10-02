package com.ghost.serialization.parser.bytes

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.BYTE_MASK
import com.ghost.serialization.parser.common.AbstractGhostSource
import com.ghost.serialization.parser.common.GhostSource
import com.ghost.serialization.parser.common.contentEqualsStringImpl
import com.ghost.serialization.parser.common.findClosingQuoteImpl
import com.ghost.serialization.parser.common.findNextNonWhitespaceImpl
import com.ghost.serialization.parser.common.scanStringImpl
import okio.ByteString

/**
 * [GhostSource] backed by an in-memory [ByteArray]; holds the loop-unrolled scanning
 * logic shared across platforms. JVM/Android subclass this to override
 * [decodeJsonStringRange] with a faster ASCII decoder; other platforms use it directly.
 */
@InternalGhostApi
open class ByteArrayGhostSource(var data: ByteArray) : AbstractGhostSource() {

    override val size: Int get() = data.size

    override fun get(
        index: Int
    ): Int = data[index].toInt() and BYTE_MASK

    override val rawSourceData: ByteArray get() = data

    override fun contentEquals(
        start: Int,
        expected: ByteString
    ): Boolean {
        if (start + expected.size > size)
            return false

        return expected.rangeEquals(
            offset = 0,
            other = data,
            otherOffset = start,
            byteCount = expected.size
        )
    }

    override fun contentEqualsString(start: Int, length: Int, expected: String): Boolean {
        val localData = data
        return contentEqualsStringImpl(start = start, length = length, targetString = expected) {
            localData[it].toInt() and BYTE_MASK
        }
    }

    override fun decodeToString(start: Int, end: Int): String = data
        .decodeToString(startIndex = start, endIndex = end)

    override fun findClosingQuote(position: Int, limit: Int): Int {
        val localData = data
        return findClosingQuoteImpl(position = position, limit = limit) {
            localData[it].toInt() and BYTE_MASK
        }
    }

    override fun findNextNonWhitespace(position: Int, limit: Int): Int {
        val localData = data
        return findNextNonWhitespaceImpl(position = position, limit = limit) {
            localData[it].toInt() and BYTE_MASK
        }
    }

    override fun scanString(start: Int, limit: Int): Long {
        val localData = data
        return scanStringImpl(start = start, limit = limit) {
            localData[it].toInt() and BYTE_MASK
        }
    }
}

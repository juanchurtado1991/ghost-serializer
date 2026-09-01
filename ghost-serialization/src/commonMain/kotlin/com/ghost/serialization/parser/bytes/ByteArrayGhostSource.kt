package com.ghost.serialization.parser.bytes

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.GhostJsonConstants.BYTE_MASK
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
open class ByteArrayGhostSource(var data: ByteArray) : GhostSource {

    override val size: Int get() = data.size

    override fun get(index: Int): Int = data[index].toInt() and BYTE_MASK

    override val rawSourceData: ByteArray get() = data

    override fun decodeToString(start: Int, end: Int): String =
        data.decodeToString(start, end)

    override fun contentEquals(start: Int, expected: ByteString): Boolean {
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

    override fun contentEqualsString(start: Int, length: Int, expected: String): Boolean {
        val localData = data
        return contentEqualsStringImpl(start, length, expected) { localData[it].toInt() and BYTE_MASK }
    }
}

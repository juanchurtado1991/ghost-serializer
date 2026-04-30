@file:OptIn(ExperimentalWasmJsInterop::class)

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi

@InternalGhostApi
class WasmByteArraySource(val data: ByteArray) : GhostSource {
    override val size: Int get() = data.size
    override fun get(index: Int): Int = data[index].toInt() and GhostJsonConstants.BYTE_MASK
    override fun decodeToString(start: Int, end: Int): String = data.decodeToString(start, end)
    override fun contentEquals(start: Int, expected: okio.ByteString): Boolean {
        if (start + expected.size > size) return false
        return expected.rangeEquals(0, data, start, expected.size)
    }

    override fun copyTo(sink: ByteArray, sinkOffset: Int, start: Int, count: Int) {
        data.copyInto(sink, sinkOffset, start, start + count)
    }

    override fun findNextNonWhitespace(position: Int, limit: Int): Int {
        val sourceData = data
        var currentPos = position
        while (currentPos + GhostJsonConstants.SEARCH_UNROLL_LIMIT < limit) {
            if (sourceData[currentPos] > GhostJsonConstants.SPACE_INT) return currentPos
            if (sourceData[currentPos + 1] > GhostJsonConstants.SPACE_INT) return currentPos + 1
            if (sourceData[currentPos + 2] > GhostJsonConstants.SPACE_INT) return currentPos + 2
            if (sourceData[currentPos + 3] > GhostJsonConstants.SPACE_INT) return currentPos + 3
            if (sourceData[currentPos + 4] > GhostJsonConstants.SPACE_INT) return currentPos + 4
            if (sourceData[currentPos + 5] > GhostJsonConstants.SPACE_INT) return currentPos + 5
            if (sourceData[currentPos + 6] > GhostJsonConstants.SPACE_INT) return currentPos + 6
            if (sourceData[currentPos + 7] > GhostJsonConstants.SPACE_INT) return currentPos + 7
            currentPos += GhostJsonConstants.SEARCH_UNROLL_STEP
        }
        while (currentPos < limit) {
            if (sourceData[currentPos] > GhostJsonConstants.SPACE_INT) return currentPos
            currentPos++
        }
        return -1
    }

    override fun findClosingQuote(position: Int, limit: Int): Int {
        val sourceData = data
        var currentPos = position
        while (currentPos + GhostJsonConstants.SEARCH_UNROLL_LIMIT < limit) {
            val byteAt0 = sourceData[currentPos]
            if (byteAt0 == GhostJsonConstants.QUOTE_BYTE) return currentPos
            if (byteAt0 == GhostJsonConstants.BACKSLASH_BYTE || byteAt0 in GhostJsonConstants.CONTROL_CHAR_START..GhostJsonConstants.CONTROL_CHAR_LIMIT) return -1
            
            val byteAt1 = sourceData[currentPos + 1]
            if (byteAt1 == GhostJsonConstants.QUOTE_BYTE) return currentPos + 1
            if (byteAt1 == GhostJsonConstants.BACKSLASH_BYTE || byteAt1 in GhostJsonConstants.CONTROL_CHAR_START..GhostJsonConstants.CONTROL_CHAR_LIMIT) return -1

            val byteAt2 = sourceData[currentPos + 2]
            if (byteAt2 == GhostJsonConstants.QUOTE_BYTE) return currentPos + 2
            if (byteAt2 == GhostJsonConstants.BACKSLASH_BYTE || byteAt2 in GhostJsonConstants.CONTROL_CHAR_START..GhostJsonConstants.CONTROL_CHAR_LIMIT) return -1

            val byteAt3 = sourceData[currentPos + 3]
            if (byteAt3 == GhostJsonConstants.QUOTE_BYTE) return currentPos + 3
            if (byteAt3 == GhostJsonConstants.BACKSLASH_BYTE || byteAt3 in GhostJsonConstants.CONTROL_CHAR_START..GhostJsonConstants.CONTROL_CHAR_LIMIT) return -1

            val byteAt4 = sourceData[currentPos + 4]
            if (byteAt4 == GhostJsonConstants.QUOTE_BYTE) return currentPos + 4
            if (byteAt4 == GhostJsonConstants.BACKSLASH_BYTE || byteAt4 in GhostJsonConstants.CONTROL_CHAR_START..GhostJsonConstants.CONTROL_CHAR_LIMIT) return -1

            val byteAt5 = sourceData[currentPos + 5]
            if (byteAt5 == GhostJsonConstants.QUOTE_BYTE) return currentPos + 5
            if (byteAt5 == GhostJsonConstants.BACKSLASH_BYTE || byteAt5 in GhostJsonConstants.CONTROL_CHAR_START..GhostJsonConstants.CONTROL_CHAR_LIMIT) return -1

            val byteAt6 = sourceData[currentPos + 6]
            if (byteAt6 == GhostJsonConstants.QUOTE_BYTE) return currentPos + 6
            if (byteAt6 == GhostJsonConstants.BACKSLASH_BYTE || byteAt6 in GhostJsonConstants.CONTROL_CHAR_START..GhostJsonConstants.CONTROL_CHAR_LIMIT) return -1

            val byteAt7 = sourceData[currentPos + 7]
            if (byteAt7 == GhostJsonConstants.QUOTE_BYTE) return currentPos + 7
            if (byteAt7 == GhostJsonConstants.BACKSLASH_BYTE || byteAt7 in GhostJsonConstants.CONTROL_CHAR_START..GhostJsonConstants.CONTROL_CHAR_LIMIT) return -1
            
            currentPos += GhostJsonConstants.SEARCH_UNROLL_STEP
        }
        while (currentPos < limit) {
            val byteValue = sourceData[currentPos]
            if (byteValue == GhostJsonConstants.QUOTE_BYTE) return currentPos
            if (byteValue == GhostJsonConstants.BACKSLASH_BYTE || byteValue in GhostJsonConstants.CONTROL_CHAR_START..GhostJsonConstants.CONTROL_CHAR_LIMIT) return -1
            currentPos++
        }
        return -1
    }

    override fun scanString(start: Int, limit: Int, reader: GhostJsonReader): Int {
        val sourceData = data
        var currentPos = start
        var hashResult = 0
        
        while (currentPos < limit) {
            val byteValue = sourceData[currentPos]
            if (byteValue == GhostJsonConstants.QUOTE_BYTE) {
                reader.position = currentPos
                return hashResult
            }
            if (byteValue == GhostJsonConstants.BACKSLASH_BYTE || byteValue in GhostJsonConstants.CONTROL_CHAR_START..GhostJsonConstants.CONTROL_CHAR_LIMIT) {
                return -1
            }
            hashResult = (hashResult shl GhostJsonConstants.HASH_SHIFT) - hashResult + byteValue
            currentPos++
        }
        return -1
    }

    override fun calculateHash(start: Int, length: Int): Int {
        val d = data
        var hashResult = 0
        var i = 0
        while (i + 3 < length) {
            hashResult = (hashResult shl GhostJsonConstants.HASH_SHIFT) - hashResult + d[start + i]
            hashResult = (hashResult shl GhostJsonConstants.HASH_SHIFT) - hashResult + d[start + i + 1]
            hashResult = (hashResult shl GhostJsonConstants.HASH_SHIFT) - hashResult + d[start + i + 2]
            hashResult = (hashResult shl GhostJsonConstants.HASH_SHIFT) - hashResult + d[start + i + 3]
            i += 4
        }
        while (i < length) {
            hashResult = (hashResult shl GhostJsonConstants.HASH_SHIFT) - hashResult + d[start + i]
            i++
        }
        return hashResult
    }
}

@InternalGhostApi
class WasmJsSource(val data: JsAny) : GhostSource {
    private val scanResultOut = Int32Array(2)

    override val size: Int get() = getPlatformSize(data)
    override fun get(index: Int): Int = getPlatformByte(data, index)
    override fun decodeToString(start: Int, end: Int): String =
        decodePlatformString(data, start, end)

    override fun findNextNonWhitespace(position: Int, limit: Int): Int =
        findPlatformNextNonWhitespace(data, position, limit)

    override fun findClosingQuote(position: Int, limit: Int): Int =
        findPlatformClosingQuote(data, position, limit)

    override fun contentEquals(start: Int, expected: okio.ByteString): Boolean {
        if (start + expected.size > size) return false
        // For WasmJs, we compare byte by byte or use a JS function. 
        // For now, let's use a JS function for speed.
        return platformContentEquals(data, start, expected.toByteArray())
    }

    override fun copyTo(sink: ByteArray, sinkOffset: Int, start: Int, count: Int) {
        copyPlatformToSink(data, sink, sinkOffset, start, count)
    }

    override fun scanString(start: Int, limit: Int, reader: GhostJsonReader): Int {
        val result = scanPlatformString(data, start, limit, scanResultOut)
        if (result == 0) {
            reader.position = scanResultOut[0]
            return scanResultOut[1]
        }
        return -1
    }

    override fun calculateHash(start: Int, length: Int): Int =
        calculatePlatformHash(data, start, length)
}

@JsFun("(a) => a.length")
private external fun getPlatformSize(a: JsAny): Int

@JsFun("(a, i) => a[i]")
private external fun getPlatformByte(a: JsAny, i: Int): Int

@JsFun("(a, s, e) => new TextDecoder().decode(a.subarray(s, e))")
private external fun decodePlatformString(a: JsAny, s: Int, e: Int): String

@JsFun("(a, p, l) => { for (let i = p; i < l; i++) { if (a[i] > 32) return i; } return -1; }")
private external fun findPlatformNextNonWhitespace(a: JsAny, p: Int, l: Int): Int

@JsFun("(a, p, l) => { for (let i = p; i < l; i++) { let b = a[i]; if (b === 34) return i; if (b === 92 || (b >= 0 && b <= 31)) return -1; } return -1; }")
private external fun findPlatformClosingQuote(a: JsAny, p: Int, l: Int): Int

@JsFun("(a, s, expected) => { if (s + expected.length > a.length) return false; for (let i = 0; i < expected.length; i++) { if (a[s + i] !== expected[i]) return false; } return true; }")
private external fun platformContentEquals(a: JsAny, s: Int, expected: ByteArray): Boolean

@JsFun("(a, sink, sinkOffset, start, count) => { sink.set(a.subarray(start, start + count), sinkOffset); }")
private external fun copyPlatformToSink(a: JsAny, sink: ByteArray, sinkOffset: Int, start: Int, count: Int)

@JsFun("(a, start, length) => { let hash = 0; for (let i = 0; i < length; i++) { hash = ((hash << 5) - hash) + a[start + i]; } return hash | 0; }")
private external fun calculatePlatformHash(a: JsAny, start: Int, length: Int): Int

@JsFun("(a, p, l, out) => { let hash = 0; for (let i = p; i < l; i++) { let b = a[i]; if (b === 34) { out[0] = i; out[1] = hash | 0; return 0; } if (b === 92 || (b >= 0 && b <= 31)) return -1; hash = ((hash << 5) - hash) + b; } return -1; }")
private external fun scanPlatformString(a: JsAny, p: Int, l: Int, out: Int32Array): Int

@InternalGhostApi
actual fun createByteArraySource(data: ByteArray): GhostSource = WasmByteArraySource(data)

@InternalGhostApi
fun createJsSource(data: JsAny): GhostSource = WasmJsSource(data)

package com.ghost.serialization.core.parser

import okio.BufferedSource
import com.ghost.serialization.core.exception.GhostJsonException

class GhostJsonReader(
    @PublishedApi internal val data: ByteArray,
    internal val maxDepth: Int = 255,
    internal val strictMode: Boolean = false
) {
    constructor(source: BufferedSource, maxDepth: Int = 255, strictMode: Boolean = false)
            : this(source.readByteArray(), maxDepth, strictMode)

    @PublishedApi internal var pos = 0
    @PublishedApi internal var line = 1
    @PublishedApi internal var column = 1
    @PublishedApi internal var depth = 0
    val path: String get() = "$" 
    internal val stringPool = arrayOfNulls<String>(GhostJsonConstants.STR_POOL_SIZE)

    class Options private constructor(
        val strings: Array<String>,
        val byteStrings: Array<okio.ByteString>,
        val rawBytes: Array<ByteArray>,
        val writerHeaders: Array<okio.ByteString>,
        val writerHeadersWithComma: Array<okio.ByteString>
    ) {
        @PublishedApi internal val dispatch = IntArray(1024) { -1 }

        init {
            for (i in rawBytes.indices) {
                val bytes = rawBytes[i]
                if (bytes.isNotEmpty()) {
                    val h = ((bytes[0].toInt() and 0xFF) * 31 + bytes.size) and 1023
                    if (dispatch[h] == -1) dispatch[h] = i
                }
            }
        }

        companion object {
            fun of(vararg names: String): Options {
                val byteStrings = Array(names.size) {
                    okio.ByteString.Companion.run { names[it].encodeUtf8() }
                }
                val rawBytes = Array(names.size) { names[it].encodeToByteArray() }
                val strings = Array(names.size) { names[it] }

                val writerHeaders = Array(names.size) {
                    okio.ByteString.Companion.run { "\"${names[it]}\":".encodeUtf8() }
                }
                val writerHeadersWithComma = Array(names.size) {
                    okio.ByteString.Companion.run { ",\"${names[it]}\":".encodeUtf8() }
                }

                return Options(strings, byteStrings, rawBytes, writerHeaders, writerHeadersWithComma)
            }
        }
    }

    @PublishedApi internal fun internalSkip(n: Int) {
        pos += n
        column += n
    }

    internal fun expectByte(expected: Byte) {
        if (pos >= data.size) throwError("Expected '${expected.toInt().toChar()}' but reached end")
        val actual = data[pos++]
        column++
        if (actual != expected) throwError(
            "Expected '${expected.toInt().toChar()}' but found '${actual.toInt().toChar()}'"
        )
    }

    internal fun internalSelect(options: Options): Int {
        val remaining = data.size - pos
        if (remaining == 0) return -2

        // Find the length of the field name without decoding
        var len = 0
        while (pos + len < data.size && data[pos + len] != GhostJsonConstants.QUOTE) {
            len++
        }
        
        if (pos + len >= data.size) return -2

        // O(1) Dispatch Table Look-up
        val firstByte = data[pos].toInt() and 0xFF
        val h = (firstByte * 31 + len) and 1023
        val hint = options.dispatch[h]
        
        if (hint != -1) {
            val opt = options.rawBytes[hint]
            if (opt.size == len) {
                var match = true
                for (j in 0 until len) {
                    if (data[pos + j] != opt[j]) {
                        match = false; break
                    }
                }
                if (match) {
                    pos += len
                    column += len
                    return hint
                }
            }
        }

        // Fallback to linear scan (rare, only on hash collisions)
        for (idx in options.rawBytes.indices) {
            if (idx == hint) continue
            val opt = options.rawBytes[idx]
            if (opt.size == len) {
                var match = true
                for (j in 0 until len) {
                    if (data[pos + j] != opt[j]) {
                        match = false; break
                    }
                }
                if (match) {
                    pos += len
                    column += len
                    return idx
                }
            }
        }
        return -2
    }

    fun selectName(options: Options): Int {
        skipWhitespace()
        if (pos >= data.size) return -1
        
        // Handle leading comma between fields
        if (data[pos] == GhostJsonConstants.COMMA) {
            pos++; column++; skipWhitespace()
        }
        
        if (pos >= data.size || data[pos] == GhostJsonConstants.CLOSE_OBJ) return -1
        
        expectByte(GhostJsonConstants.QUOTE)
        val index = internalSelect(options)
        if (index >= 0) {
            expectByte(GhostJsonConstants.QUOTE)
            return index
        }
        
        if (strictMode) {
            val fileNameBody = readStringBody()
            throwError("Unknown field in strict mode: '$fileNameBody'")
        }
        
        skipQuotedStringBody()
        return -2
    }

    fun beginObject() {
        checkDepth()
        skipWhitespace()
        expectByte(GhostJsonConstants.OPEN_OBJ); depth++
    }

    fun endObject() {
        skipWhitespace()
        expectByte(GhostJsonConstants.CLOSE_OBJ)
        depth--
    }

    fun beginArray() {
        checkDepth()
        skipWhitespace()
        expectByte(GhostJsonConstants.OPEN_ARR)
        depth++
    }

    fun endArray() {
        skipWhitespace()
        expectByte(GhostJsonConstants.CLOSE_ARR)
        depth--
    }

    fun hasNext(): Boolean {
        skipWhitespace()
        if (pos >= data.size) return false
        val b = data[pos]
        return b != GhostJsonConstants.CLOSE_OBJ && b != GhostJsonConstants.CLOSE_ARR
    }

    fun nextString(): String {
        skipWhitespace()
        return readQuotedString()
    }

    fun nextBoolean(): Boolean {
        skipWhitespace()
        if (pos + 4 > data.size) throwError("Truncated literal at end of source")
        val b = data[pos]
        if (b == GhostJsonConstants.TRUE_CHAR) {
            if (data[pos + 1] == 'r'.code.toByte() && 
                data[pos + 2] == 'u'.code.toByte() && 
                data[pos + 3] == 'e'.code.toByte()) {
                internalSkip(4); return true
            }
        } else if (b == GhostJsonConstants.FALSE_CHAR) {
            if (pos + 5 > data.size) throwError("Truncated literal at end of source")
            if (data[pos + 1] == 'a'.code.toByte() && 
                data[pos + 2] == 'l'.code.toByte() && 
                data[pos + 3] == 's'.code.toByte() && 
                data[pos + 4] == 'e'.code.toByte()) {
                internalSkip(5); return false
            }
        }
        throwError("Expected boolean but found ${b.toInt().toChar()}")
    }

    fun nextNull(): Nothing? {
        skipWhitespace()
        if (pos + 4 > data.size) throwError("Truncated literal at end of source")
        if (data[pos] == 'n'.code.toByte() && 
            data[pos + 1] == 'u'.code.toByte() && 
            data[pos + 2] == 'l'.code.toByte() && 
            data[pos + 3] == 'l'.code.toByte()) {
            internalSkip(4); return null
        }
        throwError("Expected null but found ${data[pos].toInt().toChar()}")
    }

    internal fun readQuotedString(): String {
        expectByte(GhostJsonConstants.QUOTE)
        return readStringBody()
    }

    internal fun readStringBody(): String {
        val start = pos
        while (pos < data.size) {
            val b = data[pos]
            if (b == GhostJsonConstants.QUOTE) {
                val len = pos - start
                return readPooledString(start, len)
            }
            if (b.toInt() in 0..31) throwError("Unescaped control character in string")
            if (b == GhostJsonConstants.BACKSLASH) return readStringWithEscapes(start)
            pos++
        }
        throwError(GhostJsonConstants.UNTERMINATED_STRING_ERROR)
    }

    private fun readPooledString(start: Int, len: Int): String {
        if (len <= 0) {
            pos++; column += 1
            return ""
        }

        if (len > GhostJsonConstants.MAX_POOL_STRING_LENGTH) {
            val result = data.decodeToString(start, start + len)
            pos++; column += len + 1
            return result
        }

        var hash = 0
        for (i in start until start + len) {
            hash = 33 * hash + data[i].toInt()
        }
        val poolIndex = hash and (GhostJsonConstants.STR_POOL_SIZE - 1)
        val cached = stringPool[poolIndex]
        if (cached != null && cached.length == len) {
            var match = true
            for (i in 0 until len) {
                if (cached[i].code.toByte() != data[start + i]) {
                    match = false; break
                }
            }
            if (match) {
                pos++; column += len + 1
                return cached
            }
        }
        val result = data.decodeToString(start, start + len)
        stringPool[poolIndex] = result
        pos++; column += len + 1
        return result
    }

    private fun readStringWithEscapes(start: Int): String {
        val out = StringBuilder(GhostJsonConstants.STRING_BUILDER_CAPACITY)
        if (pos > start) {
            out.append(data.decodeToString(start, pos))
            column += pos - start
        }
        while (pos < data.size) {
            val b = data[pos]
            if (b == GhostJsonConstants.QUOTE) {
                pos++; column++; return out.toString()
            }
            if (b.toInt() in 0..31) throwError("Unescaped control character in string")
            if (b == GhostJsonConstants.BACKSLASH) {
                pos++; column++
                val c = readEscapeCode()
                if (c <= 0xFFFF) {
                    out.append(c.toChar())
                } else {
                    out.append(((c - 0x10000) shr 10 or 0xD800).toChar())
                    out.append(((c - 0x10000) and 0x3FF or 0xDC00).toChar())
                }
            } else {
                val scanStart = pos
                while (pos < data.size) {
                    val sb = data[pos]
                    if (sb == GhostJsonConstants.QUOTE || sb == GhostJsonConstants.BACKSLASH || sb.toInt() in 0..31) break
                    pos++
                }
                out.append(data.decodeToString(scanStart, pos))
                column += pos - scanStart
            }
        }
        throwError(GhostJsonConstants.UNTERMINATED_STRING_ERROR)
    }

    private fun readEscapeCode(): Int {
        if (pos >= data.size) throwError("Unterminated escape sequence")
        val b = data[pos++]; column++
        return when (b.toInt().toChar()) {
            'n' -> '\n'.code; 't' -> '\t'.code; 'r' -> '\r'.code
            'b' -> '\b'.code; 'f' -> '\u000C'.code
            'u' -> readUnicodeCode()
            '\\' -> '\\'.code; '"' -> '"'.code; '/' -> '/'.code
            else -> throwError("Invalid escape sequence: \\${b.toInt().toChar()}")
        }
    }

    private fun readUnicodeCode(): Int {
        if (pos + 4 > data.size) throwError("Unterminated unicode escape")
        val hex = data.decodeToString(pos, pos + 4)
        pos += 4; column += 4
        val code = try {
            hex.toInt(16)
        } catch (_: Exception) {
            throwError("Invalid unicode escape: \\u$hex")
        }
        if (code in 0xD800..0xDBFF) {
            if (pos + 6 > data.size || data[pos] != GhostJsonConstants.BACKSLASH || data[pos + 1] != 'u'.code.toByte()) {
                throwError("Lone high surrogate: \\u$hex")
            }
            pos += 2; column += 2
            val lowHex = data.decodeToString(pos, pos + 4)
            pos += 4; column += 4
            val lowCode = try {
                lowHex.toInt(16)
            } catch (_: Exception) {
                throwError("Invalid low surrogate: \\u$lowHex")
            }
            if (lowCode !in 0xDC00..0xDFFF) throwError("Invalid low surrogate: \\u$lowHex")
            return (((code - 0xD800) shl 10) or (lowCode - 0xDC00)) + 0x10000
        }
        if (code in 0xDC00..0xDFFF) throwError("Lone low surrogate: \\u$hex")
        return code
    }

    internal fun skipQuotedString() {
        expectByte(GhostJsonConstants.QUOTE)
        skipQuotedStringBody()
    }

    internal fun skipQuotedStringBody() {
        while (pos < data.size) {
            val b = data[pos++]; column++
            if (b == GhostJsonConstants.QUOTE) return
            if (b == GhostJsonConstants.BACKSLASH) {
                if (strictMode) {
                    readEscapeCode()
                } else if (pos < data.size) {
                    if (data[pos] == 'u'.code.toByte()) {
                        pos++; column++
                        val skip = minOf(4, data.size - pos)
                        pos += skip; column += skip
                    } else {
                        pos++; column++
                    }
                }
            } else if (b.toInt() in 0..31) {
                throwError("Unescaped control character in string")
            }
        }
        throwError(GhostJsonConstants.UNTERMINATED_STRING_ERROR)
    }

    @PublishedApi
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun skipWhitespace() {
        while (pos < data.size) {
            val b = data[pos]
            if (b > 32) return // Most common case: not whitespace
            
            when (b) {
                GhostJsonConstants.SPACE, GhostJsonConstants.TAB, GhostJsonConstants.CR -> {
                    pos++; column++
                }

                GhostJsonConstants.NEWLINE -> {
                    pos++; line++; column = 1
                }

                else -> return
            }
        }
    }

    internal fun peekByte(): Byte {
        if (pos >= data.size) throwError("Unexpected EOF")
        return data[pos]
    }

    internal fun peekNextByte(offset: Long): Byte? {
        val idx = pos + offset.toInt()
        if (idx >= data.size) return null
        return data[idx]
    }

    fun throwError(msg: String): Nothing {
        throw GhostJsonException(msg, line, column)
    }

    private fun checkDepth() {
        if (depth >= maxDepth) throwError("Reached maximum recursion depth ($maxDepth)")
    }
}
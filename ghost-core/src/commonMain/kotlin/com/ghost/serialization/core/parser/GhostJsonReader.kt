package com.ghost.serialization.core.parser

import okio.BufferedSource
import com.ghost.serialization.core.exception.GhostJsonException

class Options(
    val strings: Array<String>,
    val byteStrings: Array<okio.ByteString>,
    val rawBytes: Array<ByteArray>,
    val writerHeaders: Array<okio.ByteString>,
    val writerHeadersWithComma: Array<okio.ByteString>,
    @PublishedApi internal val shift: Int,
    @PublishedApi internal val multiplier: Int
) {
    @PublishedApi internal val dispatch = IntArray(1024) { -1 }

    init {
        for (i in rawBytes.indices) {
            val bytes = rawBytes[i]
            if (bytes.isNotEmpty()) {
                // Ghost Zenith Hash: Use up to 4 bytes for the dispatch key
                var key = 0
                if (bytes.size >= 1) key = key or (bytes[0].toInt() and 0xFF)
                if (bytes.size >= 2) key = key or ((bytes[1].toInt() and 0xFF) shl 8)
                if (bytes.size >= 3) key = key or ((bytes[2].toInt() and 0xFF) shl 16)
                if (bytes.size >= 4) key = key or ((bytes[3].toInt() and 0xFF) shl 24)
                
                val h = ((key * multiplier + bytes.size) shr shift) and 1023
                if (dispatch[h] == -1) {
                    dispatch[h] = i
                }
            }
        }
    }

    companion object {
        fun of(vararg names: String): Options = of(0, 31, *names)

        fun of(shift: Int, multiplier: Int, vararg names: String): Options {
            val byteStrings = Array(names.size) { names[it].encodeToByteArray() }
            val rawBytes = Array(names.size) { names[it].encodeToByteArray() }
            val strings = Array(names.size) { names[it] }

            val writerHeaders = Array(names.size) {
                okio.ByteString.Companion.run { "\"${names[it]}\":".encodeUtf8() }
            }
            val writerHeadersWithComma = Array(names.size) {
                okio.ByteString.Companion.run { ",\"${names[it]}\":".encodeUtf8() }
            }

            return Options(
                strings, emptyArray(), rawBytes, writerHeaders, writerHeadersWithComma,
                shift, multiplier
            )
        }
    }
}

class GhostJsonReader(
    @PublishedApi internal var data: ByteArray,
    internal val maxDepth: Int = 255,
    internal val strictMode: Boolean = false
) {
    fun reset(newData: ByteArray) {
        this.data = newData
        this.pos = 0
        this.depth = 0
        this.nextTokenByte = -1
    }
    constructor(source: BufferedSource, maxDepth: Int = 255, strictMode: Boolean = false)
            : this(source.readByteArray(), maxDepth, strictMode)

    private var _pos = 0
    @PublishedApi internal var pos: Int
        get() = _pos
        set(value) {
            _pos = value
            _nextTokenByte = -1
        }

    @PublishedApi internal var depth: Int = 0
    
    private var _nextTokenByte: Int = -1
    @PublishedApi internal var nextTokenByte: Int
        get() = _nextTokenByte
        set(value) { _nextTokenByte = value }
    val path: String get() = GhostJsonConstants.PATH_ROOT
    internal val stringPool = arrayOfNulls<String>(GhostJsonConstants.STR_POOL_SIZE)

    @PublishedApi internal fun internalSkip(n: Int) {
        pos += n
    }

    internal fun expectByte(expected: Byte) {
        if (pos >= data.size) throwError("Expected '${expected.toInt().toChar()}' but reached end")
        val actual = data[pos++]
        if (actual != expected) throwError(
            "Expected '${expected.toInt().toChar()}' but found '${actual.toInt().toChar()}'"
        )
    }

    internal fun internalSelect(options: Options): Int {
        val remaining = data.size - pos
        if (remaining <= 0) return -2

        var len = 0
        // Ghost Zenith Vectorized Scan: Find quote faster
        while (pos + len + 7 < data.size) {
            if (data[pos + len] == 34.toByte()) break
            if (data[pos + len + 1] == 34.toByte()) { len += 1; break }
            if (data[pos + len + 2] == 34.toByte()) { len += 2; break }
            if (data[pos + len + 3] == 34.toByte()) { len += 3; break }
            if (data[pos + len + 4] == 34.toByte()) { len += 4; break }
            if (data[pos + len + 5] == 34.toByte()) { len += 5; break }
            if (data[pos + len + 6] == 34.toByte()) { len += 6; break }
            if (data[pos + len + 7] == 34.toByte()) { len += 7; break }
            len += 8
        }
        while (pos + len < data.size && data[pos + len] != 34.toByte()) {
            len++
        }
        if (pos + len >= data.size) return -2

        // Zenith Hash Calculation
        var key = 0
        if (len >= 1) key = key or (data[pos].toInt() and 0xFF)
        if (len >= 2) key = key or ((data[pos + 1].toInt() and 0xFF) shl 8)
        if (len >= 3) key = key or ((data[pos + 2].toInt() and 0xFF) shl 16)
        if (len >= 4) key = key or ((data[pos + 3].toInt() and 0xFF) shl 24)

        val h = ((key * options.multiplier) + len) shr options.shift and 1023
        val hint = options.dispatch[h]
        
        if (hint != -1) {
            val opt = options.rawBytes[hint]
            if (opt.size == len) {
                var i = 0
                var match = true
                while (i + 3 < len) {
                    if (data[pos + i] != opt[i] || data[pos + i + 1] != opt[i + 1] || 
                        data[pos + i + 2] != opt[i + 2] || data[pos + i + 3] != opt[i + 3]) {
                        match = false; break
                    }
                    i += 4
                }
                if (match) {
                    while (i < len) {
                        if (data[pos + i] != opt[i]) { match = false; break }
                        i++
                    }
                }
                if (match) { internalSkip(len); return hint }
            }
        }

        // Collision recovery
        for (idx in options.rawBytes.indices) {
            if (idx == hint) continue
            val opt = options.rawBytes[idx]
            if (opt.size == len) {
                var i = 0
                var match = true
                while (i + 3 < len) {
                    if (data[pos + i] != opt[i] || data[pos + i + 1] != opt[i + 1] || 
                        data[pos + i + 2] != opt[i + 2] || data[pos + i + 3] != opt[i + 3]) {
                        match = false; break
                    }
                    i += 4
                }
                if (match) {
                    while (i < len) {
                        if (data[pos + i] != opt[i]) { match = false; break }
                        i++
                    }
                }
                if (match) { internalSkip(len); return idx }
            }
        }
        return -2
    }

    fun selectName(options: Options): Int {
        if (nextTokenByte == -1) skipWhitespace()
        if (pos >= data.size) return -1
        
        var b = if (nextTokenByte != -1) nextTokenByte else (data[pos].toInt() and 0xFF)
        if (b == 44) { // COMMA
            pos++; nextTokenByte = -1; skipWhitespace()
            if (pos >= data.size) return -1
            b = data[pos].toInt() and 0xFF
        }
        
        if (b == 125) return -1 // CLOSE_OBJ
        
        if (b != 34) throwError("Expected '\"' but found ${b.toChar()}")
        pos++; nextTokenByte = -1 // Skip opening quote
        
        val index = internalSelect(options)
        if (index >= 0) {
            if (pos < data.size && data[pos] == 34.toByte()) {
                pos++; nextTokenByte = -1
                return index
            }
            throwError("Expected '\"'")
        }
        
        // Cold path: Unknown field
        if (strictMode) {
            val name = readStringBody() // This skips closing quote
            throwError("${GhostJsonConstants.STRICT_MODE_UNKNOWN_FIELD}'$name'")
        }
        skipQuotedStringBody()
        return -2
    }

    fun selectNameAndConsume(options: Options): Int {
        if (nextTokenByte == -1) skipWhitespace()
        if (pos >= data.size) return -1
        
        var b = if (nextTokenByte != -1) nextTokenByte else (data[pos].toInt() and 0xFF)
        if (b == 44) { // COMMA
            pos++; nextTokenByte = -1; skipWhitespace()
            if (pos >= data.size) return -1
            b = data[pos].toInt() and 0xFF
        }
        
        if (b == 125) return -1 // CLOSE_OBJ
        
        if (b != 34) throwError("Expected '\"' but found ${b.toChar()}")
        pos++; nextTokenByte = -1 // Skip opening quote
        
        val index = internalSelect(options)
        if (index >= 0) {
            // Found it. Skip closing quote and separator
            if (pos < data.size && data[pos] == 34.toByte()) {
                pos++
                // Fused consumeKeySeparator
                skipWhitespace()
                if (pos < data.size && data[pos] == 58.toByte()) {
                    pos++; nextTokenByte = -1
                    skipWhitespace()
                    if (pos < data.size) nextTokenByte = data[pos].toInt() and 0xFF
                    return index
                }
                throwError("Expected ':'")
            }
            throwError("Expected '\"'")
        }
        
        // Cold path: Unknown field
        if (strictMode) {
            val name = readStringBody() // This skips closing quote
            throwError("${GhostJsonConstants.STRICT_MODE_UNKNOWN_FIELD}'$name'")
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
        val b = peekNextToken()
        if (b == -1) return false
        return b.toByte() != GhostJsonConstants.CLOSE_OBJ && b.toByte() != GhostJsonConstants.CLOSE_ARR
    }

    fun nextString(): String {
        if (nextTokenByte == -1) skipWhitespace()
        return readQuotedString()
    }

    fun nextBoolean(): Boolean {
        if (nextTokenByte == -1) skipWhitespace()
        if (pos + 4 > data.size) throwError(GhostJsonConstants.TRUNCATED_LITERAL_ERROR)
        val b = data[pos]
        if (b == GhostJsonConstants.TRUE_CHAR) {
            if (data[pos + 1] == 'r'.code.toByte() && 
                data[pos + 2] == 'u'.code.toByte() && 
                data[pos + 3] == 'e'.code.toByte()) {
                internalSkip(4); return true
            }
        } else if (b == GhostJsonConstants.FALSE_CHAR) {
            if (pos + 5 > data.size) throwError(GhostJsonConstants.TRUNCATED_LITERAL_ERROR)
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
        if (pos + 4 > data.size) throwError(GhostJsonConstants.TRUNCATED_LITERAL_ERROR)
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
        // Ghost Vectorized Scan: Find quote or escape faster using Lookup Table
        while (pos + 3 < data.size) {
            val b1 = data[pos].toInt() and 0xFF
            val b2 = data[pos + 1].toInt() and 0xFF
            val b3 = data[pos + 2].toInt() and 0xFF
            val b4 = data[pos + 3].toInt() and 0xFF
            
            if (GhostJsonConstants.IS_STRING_TERMINATOR[b1]) break
            if (GhostJsonConstants.IS_STRING_TERMINATOR[b2]) { pos += 1; break }
            if (GhostJsonConstants.IS_STRING_TERMINATOR[b3]) { pos += 2; break }
            if (GhostJsonConstants.IS_STRING_TERMINATOR[b4]) { pos += 3; break }
            pos += 4
        }
        
        while (pos < data.size) {
            val b = data[pos]
            if (b == GhostJsonConstants.QUOTE) {
                val len = pos - start
                return readPooledString(start, len)
            }
            if (b.toInt() in 0..31) throwError(GhostJsonConstants.UNESCAPED_CONTROL_CHAR_ERROR)
            if (b == GhostJsonConstants.BACKSLASH) return readStringWithEscapes(start)
            pos++
        }
        throwError(GhostJsonConstants.UNTERMINATED_STRING_ERROR)
    }

    private fun readPooledString(start: Int, len: Int): String {
        if (len <= 0) {
            internalSkip(1); return ""
        }
        if (len > GhostJsonConstants.MAX_POOL_STRING_LENGTH) {
            val result = data.decodeToString(start, start + len)
            internalSkip(1); return result
        }
        // Fast Hash Zenith: Optimized Rolling Hash with Unrolling
        var hash = 0
        var i = start
        val end = start + len
        
        while (i + 3 < end) {
            hash = (hash shl 5) - hash + (data[i].toInt() and 0xFF)
            hash = (hash shl 5) - hash + (data[i + 1].toInt() and 0xFF)
            hash = (hash shl 5) - hash + (data[i + 2].toInt() and 0xFF)
            hash = (hash shl 5) - hash + (data[i + 3].toInt() and 0xFF)
            i += 4
        }
        while (i < end) {
            hash = (hash shl 5) - hash + (data[i].toInt() and 0xFF)
            i++
        }
        
        val poolIndex = hash and (GhostJsonConstants.STR_POOL_SIZE - 1)
        val cached = stringPool[poolIndex]
        
        if (cached != null && cached.length == len) {
            // Hot Path: Candidate found, verify bytes with unrolling
            var match = true
            if (len >= 4) {
                if (cached[0].code != (data[start].toInt() and 0xFF) ||
                    cached[1].code != (data[start + 1].toInt() and 0xFF) ||
                    cached[2].code != (data[start + 2].toInt() and 0xFF) ||
                    cached[3].code != (data[start + 3].toInt() and 0xFF)) {
                    match = false
                }
            }
            if (match) {
                var j = if (len >= 4) 4 else 0
                while (j < len) {
                    if (cached[j].code != (data[start + j].toInt() and 0xFF)) {
                        match = false
                        break
                    }
                    j++
                }
                if (match) {
                    internalSkip(1) // Skip closing quote
                    return cached
                }
            }
        }

        // Cold Path: Decode and potentially update pool
        val result = data.decodeToString(start, start + len)
        stringPool[poolIndex] = result
        internalSkip(1) // Skip closing quote
        return result
    }

    private fun readStringWithEscapes(start: Int): String {
        val out = StringBuilder(GhostJsonConstants.STRING_BUILDER_CAPACITY)
        if (pos > start) {
            out.append(data.decodeToString(start, pos))
        }
        while (pos < data.size) {
            val b = data[pos]
            if (b == GhostJsonConstants.QUOTE) {
                internalSkip(1); return out.toString()
            }
            if (b.toInt() in 0..31) throwError(GhostJsonConstants.UNESCAPED_CONTROL_CHAR_ERROR)
            if (b == GhostJsonConstants.BACKSLASH) {
                internalSkip(1)
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
                    internalSkip(1)
                }
                out.append(data.decodeToString(scanStart, pos))
            }
        }
        throwError(GhostJsonConstants.UNTERMINATED_STRING_ERROR)
    }

    private fun readEscapeCode(): Int {
        if (pos >= data.size) throwError(GhostJsonConstants.UNTERMINATED_ESCAPE_ERROR)
        val b = data[pos]
        internalSkip(1)
        return when (b.toInt().toChar()) {
            'n' -> '\n'.code; 't' -> '\t'.code; 'r' -> '\r'.code
            'b' -> '\b'.code; 'f' -> '\u000C'.code
            'u' -> readUnicodeCode()
            '\\' -> '\\'.code; '"' -> '"'.code; '/' -> '/'.code
            else -> throwError("Invalid escape sequence: \\${b.toInt().toChar()}")
        }
    }

    private fun readUnicodeCode(): Int {
        if (pos + 4 > data.size) throwError(GhostJsonConstants.UNTERMINATED_UNICODE_ERROR)
        val hex = data.decodeToString(pos, pos + 4)
        internalSkip(4)
        val code = try { hex.toInt(16) } catch (_: Exception) { throwError("Invalid unicode escape: \\u$hex") }
        if (code in 0xD800..0xDBFF) {
            if (pos + 6 > data.size || data[pos] != GhostJsonConstants.BACKSLASH || data[pos + 1] != 'u'.code.toByte()) {
                throwError("Lone high surrogate: \\u$hex")
            }
            internalSkip(2)
            val lowHex = data.decodeToString(pos, pos + 4)
            internalSkip(4)
            val lowCode = try { lowHex.toInt(16) } catch (_: Exception) { throwError("Invalid low surrogate: \\u$lowHex") }
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
            val b = data[pos]
            internalSkip(1)
            if (b == GhostJsonConstants.QUOTE) return
            if (b == GhostJsonConstants.BACKSLASH) {
                if (strictMode) {
                    readEscapeCode()
                } else if (pos < data.size) {
                    if (data[pos] == 'u'.code.toByte()) {
                        internalSkip(1); val skip = minOf(4, data.size - pos); internalSkip(skip)
                    } else { internalSkip(1) }
                }
            } else if (b.toInt() in 0..31) {
                throwError(GhostJsonConstants.UNESCAPED_CONTROL_CHAR_ERROR)
            }
        }
        throwError(GhostJsonConstants.UNTERMINATED_STRING_ERROR)
    }

    @PublishedApi
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun skipWhitespace() {
        if (nextTokenByte != -1) return
        
        while (pos < data.size) {
            val b = data[pos].toInt() and 0xFF
            
            // Ghost Universal Overdrive: Bitmask check for 0x20 (space), 0x0A (LF), 0x0D (CR), 0x09 (TAB)
            // (1L << 32) | (1L << 10) | (1L << 13) | (1L << 9) = 0x100002600
            if (b > 32 || (0x100002600L and (1L shl b)) == 0L) return
            
            pos++
            // Word-skipping Overdrive
            while (pos + 3 < data.size) {
                val w1 = data[pos].toInt() and 0xFF
                val w2 = data[pos + 1].toInt() and 0xFF
                val w3 = data[pos + 2].toInt() and 0xFF
                val w4 = data[pos + 3].toInt() and 0xFF
                
                if (w1 <= 32 && (0x100002600L and (1L shl w1)) != 0L &&
                    w2 <= 32 && (0x100002600L and (1L shl w2)) != 0L &&
                    w3 <= 32 && (0x100002600L and (1L shl w3)) != 0L &&
                    w4 <= 32 && (0x100002600L and (1L shl w4)) != 0L) {
                    pos += 4
                } else break
            }
        }
    }

    internal fun peekByte(): Byte {
        if (pos >= data.size) throwError(GhostJsonConstants.UNEXPECTED_EOF_ERROR)
        return data[pos]
    }

    internal fun peekNextByte(offset: Long): Byte? {
        val idx = pos + offset.toInt()
        if (idx >= data.size) return null
        return data[idx]
    }

    fun throwError(msg: String): Nothing {
        var line = 1
        var column = 1
        for (i in 0 until pos) {
            if (data[i] == GhostJsonConstants.NEWLINE) { line++; column = 1 } else { column++ }
        }
        throw GhostJsonException(msg, line, column)
    }

    private fun checkDepth() {
        if (depth >= maxDepth) throwError("${GhostJsonConstants.ERR_MAX_DEPTH} ($maxDepth)")
    }
}
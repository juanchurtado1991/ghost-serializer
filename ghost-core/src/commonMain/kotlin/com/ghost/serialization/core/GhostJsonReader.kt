package com.ghost.serialization.core

import okio.BufferedSource
import com.ghost.serialization.core.GhostJsonConstants.COLON
import com.ghost.serialization.core.GhostJsonConstants.COMMA
import com.ghost.serialization.core.GhostJsonConstants.CR
import com.ghost.serialization.core.GhostJsonConstants.FALSE_CHAR
import com.ghost.serialization.core.GhostJsonConstants.FALSE_LENGTH
import com.ghost.serialization.core.GhostJsonConstants.NEWLINE
import com.ghost.serialization.core.GhostJsonConstants.NULL_CHAR
import com.ghost.serialization.core.GhostJsonConstants.NULL_LENGTH
import com.ghost.serialization.core.GhostJsonConstants.CLOSE_ARR
import com.ghost.serialization.core.GhostJsonConstants.CLOSE_OBJ
import com.ghost.serialization.core.GhostJsonConstants.OPEN_ARR
import com.ghost.serialization.core.GhostJsonConstants.OPEN_OBJ
import com.ghost.serialization.core.GhostJsonConstants.QUOTE
import com.ghost.serialization.core.GhostJsonConstants.BACKSLASH
import com.ghost.serialization.core.GhostJsonConstants.SPACE
import com.ghost.serialization.core.GhostJsonConstants.TAB
import com.ghost.serialization.core.GhostJsonConstants.TRUE_CHAR
import com.ghost.serialization.core.GhostJsonConstants.TRUE_LENGTH

class GhostJsonReader(
    internal val source: okio.BufferedSource,
    internal val maxDepth: Int = 255,
    internal val strictMode: Boolean = false
) {
    internal var line = 1
    internal var column = 1
    internal var depth = 0
    internal val stringPool = arrayOfNulls<String>(GhostJsonConstants.STR_POOL_SIZE)

    class Options private constructor(
        val strings: Array<String>,
        val byteStrings: Array<okio.ByteString>,
        val okioOptions: okio.Options
    ) {
        companion object {
            fun of(vararg names: String): Options {
                val byteStrings = Array<okio.ByteString>(names.size) { 
                    okio.ByteString.Companion.run { names[it].encodeUtf8() } 
                }
                val strings = Array<String>(names.size) { names[it] }
                return Options(strings, byteStrings, okio.Options.of(*byteStrings))
            }
        }
    }

    // --- STATEFUL CONSUMPTION PIPELINE ---

    internal fun internalReadByte(): Byte {
        val actual = source.readByte()
        column++
        return actual
    }

    internal fun internalSkip(n: Long) {
        source.skip(n)
        column += n.toInt()
    }

    internal fun internalSelect(options: Options): Int {
        val index = source.select(options.okioOptions)
        if (index != -1) {
            column += options.byteStrings[index].size
        }
        return index
    }

    // --- CORE LOGIC ---

    fun beginObject() { checkDepth(); skipWhitespace(); expectByte(OPEN_OBJ); depth++ }
    fun endObject() { skipWhitespace(); expectByte(CLOSE_OBJ); depth-- }
    fun beginArray() { checkDepth(); skipWhitespace(); expectByte(OPEN_ARR); depth++ }
    fun endArray() { skipWhitespace(); expectByte(CLOSE_ARR); depth-- }

    fun hasNext(): Boolean {
        skipWhitespace()
        if (!source.request(1)) return false
        val b = source.buffer[0]
        return b != GhostJsonConstants.CLOSE_OBJ && b != GhostJsonConstants.CLOSE_ARR
    }

    fun nextString(): String { skipWhitespace(); return readQuotedString() }

    fun nextBoolean(): Boolean {
        skipWhitespace()
        val b = peekByte()
        return when (b) {
            TRUE_CHAR -> { internalSkip(TRUE_LENGTH); true }
            FALSE_CHAR -> { internalSkip(FALSE_LENGTH); false }
            else -> throwError("Expected boolean but found ${b.toInt().toChar()}")
        }
    }

    internal fun readQuotedString(): String {
        expectByte(QUOTE)
        return readStringBody()
    }

    internal fun readStringBody(): String {
        val index = source.indexOfElement(GhostJsonConstants.QUOTE_OR_BACKSLASH)
        if (index == -1L) throwError(GhostJsonConstants.UNTERMINATED_STRING_ERROR)
        
        val b = source.buffer[index]
        if (b == QUOTE) {
            return readPooledString(index)
        }
        
        return readStringWithEscapes(index)
    }

    private fun readPooledString(length: Long): String {
        if (length <= 0) {
            internalSkip(1)
            return ""
        }
        
        val len = length.toInt()
        
        if (len > GhostJsonConstants.MAX_POOL_STRING_LENGTH) {
            for (i in 0L until length) {
                val byte = source.buffer[i]
                if (byte in 0..31) throwError("Unescaped control character in string")
            }
            val result = source.readUtf8(length)
            column += len
            internalSkip(1)
            return result
        }
        
        var hash = 0
        for (i in 0L until length) {
            val byte = source.buffer[i].toInt()
            if (byte in 0..31) throwError("Unescaped control character in string")
            hash = 31 * hash + byte
        }
        val poolIndex = hash and (GhostJsonConstants.STR_POOL_SIZE - 1)
        val cached = stringPool[poolIndex]
        if (cached != null && cached.length == len) {
            var match = true
            for (i in 0 until len) {
                if (cached[i].code.toByte() != source.buffer[i.toLong()]) { match = false; break }
            }
            if (match) {
                internalSkip(length + 1)
                return cached
            }
        }
        val result = source.readUtf8(length)
        column += len
        stringPool[poolIndex] = result
        internalSkip(1)
        return result
    }

    private fun readStringWithEscapes(index: Long): String {
        val out = StringBuilder()
        out.append(source.readUtf8(index))
        column += index.toInt()
        
        while (source.request(1)) {
            val b = internalReadByte()
            if (b == QUOTE) return out.toString()
            if (b < 32) throwError("Unescaped control character in string")
            if (b == BACKSLASH) {
                val c = readEscapeCode()
                if (c <= 0xFFFF) {
                    out.append(c.toChar())
                } else {
                    out.append(((c - 0x10000) shr 10 or 0xD800).toChar())
                    out.append(((c - 0x10000) and 0x3FF or 0xDC00).toChar())
                }
            } else {
                out.append(b.toInt().toChar())
            }
        }
        throwError(GhostJsonConstants.UNTERMINATED_STRING_ERROR)
    }

    private fun readEscapeCode(): Int {
        if (!source.request(1)) throwError("Unterminated escape sequence")
        return when (val b = internalReadByte()) {
            'n'.code.toByte() -> '\n'.code
            't'.code.toByte() -> '\t'.code
            'r'.code.toByte() -> '\r'.code
            'b'.code.toByte() -> '\b'.code
            'f'.code.toByte() -> '\u000C'.code
            'u'.code.toByte() -> readUnicodeCode()
            BACKSLASH, QUOTE, '/'.code.toByte() -> b.toInt() and 0xFF
            else -> throwError("Invalid escape sequence: \\${b.toInt().toChar()}")
        }
    }

    private fun readUnicodeCode(): Int {
        if (!source.request(4)) throwError("Unterminated unicode escape")
        val hex = source.readUtf8(4)
        column += 4
        val code = try { hex.toInt(16) } catch (e: Exception) { throwError("Invalid unicode escape: \\u$hex") }
        
        if (code in 0xD800..0xDBFF) {
            if (!source.request(6) || source.readByte() != BACKSLASH || source.readByte() != 'u'.code.toByte()) {
                throwError("Lone high surrogate: \\u$hex")
            }
            val lowHex = source.readUtf8(4)
            column += 6
            val lowCode = try { lowHex.toInt(16) } catch (e: Exception) { throwError("Invalid low surrogate: \\u$lowHex") }
            if (lowCode !in 0xDC00..0xDFFF) {
                throwError("Invalid low surrogate: \\u$lowHex")
            }
            return (((code - 0xD800) shl 10) or (lowCode - 0xDC00)) + 0x10000
        }
        
        if (code in 0xDC00..0xDFFF) {
            throwError("Lone low surrogate: \\u$hex")
        }
        
        return code
    }

    internal fun skipQuotedString() {
        expectByte(QUOTE)
        while (source.request(1)) {
            val b = internalReadByte()
            if (b == QUOTE) return
            if (b == BACKSLASH) {
                // In strict mode, we validate escapes even when skipping
                if (strictMode) {
                    readEscapeCode()
                } else {
                    internalSkip(1)
                }
            } else if (b < 32) {
                throwError("Unescaped control character in string")
            }
        }
        throwError(GhostJsonConstants.UNTERMINATED_STRING_ERROR)
    }

    internal fun skipWhitespace() {
        while (source.request(1)) {
            val b = source.buffer[0]
            if (b == SPACE || b == TAB || b == CR) {
                internalSkip(1)
            } else if (b == NEWLINE) {
                source.skip(1)
                line++
                column = 1
            } else {
                break
            }
        }
    }

    internal fun expectByte(expected: Byte) {
        if (!source.request(1)) throwError("Expected '${expected.toInt().toChar()}' but reached end")
        val actual = internalReadByte()
        if (actual != expected) throwError("Expected '${expected.toInt().toChar()}' but found '${actual.toInt().toChar()}'")
    }

    internal fun peekByte(): Byte {
        source.require(1)
        return source.buffer[0]
    }

    internal fun peekNextByte(offset: Long): Byte? {
        if (!source.request(offset + 1)) return null
        return source.buffer[offset]
    }

    internal fun internalThrowError(msg: String): Nothing = throwError(msg)

    private fun throwError(msg: String): Nothing {
        throw GhostJsonException(msg, line, column)
    }

    private fun checkDepth() {
        if (depth >= maxDepth) throwError("Reached maximum recursion depth ($maxDepth)")
    }
}
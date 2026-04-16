package com.ghost.serialization.core.writer

import okio.BufferedSink
import com.ghost.serialization.core.parser.GhostJsonConstants
import com.ghost.serialization.core.exception.GhostJsonException

class GhostJsonWriter(internal val sink: BufferedSink) {

    private var needsComma = false
    private var depth = 0

    fun beginObject(): GhostJsonWriter {
        checkDepth()
        appendSeparator()
        sink.writeByte('{'.code)
        needsComma = false
        depth++
        return this
    }

    fun endObject(): GhostJsonWriter {
        sink.writeByte('}'.code)
        needsComma = true
        depth--
        return this
    }

    fun beginArray(): GhostJsonWriter {
        checkDepth()
        appendSeparator()
        sink.writeByte('['.code)
        needsComma = false
        depth++
        return this
    }

    fun endArray(): GhostJsonWriter {
        sink.writeByte(']'.code)
        needsComma = true
        depth--
        return this
    }

    fun name(key: String): GhostJsonWriter {
        appendSeparator()
        sink.writeByte('"'.code)
        writeEscaped(key)
        sink.writeUtf8("\":")
        needsComma = false
        return this
    }

    fun name(key: okio.ByteString): GhostJsonWriter {
        appendSeparator()
        sink.writeByte('"'.code)
        sink.write(key)
        sink.writeUtf8("\":")
        needsComma = false
        return this
    }

    fun value(text: String): GhostJsonWriter {
        appendSeparator()
        sink.writeByte('"'.code)
        writeEscaped(text)
        sink.writeByte('"'.code)
        needsComma = true
        return this
    }

    fun value(number: Int): GhostJsonWriter {
        appendSeparator()
        sink.writeDecimalLong(number.toLong())
        needsComma = true
        return this
    }

    fun value(number: Long): GhostJsonWriter {
        appendSeparator()
        sink.writeDecimalLong(number)
        needsComma = true
        return this
    }

    fun value(number: Double): GhostJsonWriter {
        if (!number.isFinite()) throw GhostJsonException("JSON does not support non-finite numbers like NaN or Infinity", 0, 0)
        appendSeparator()
        sink.writeUtf8(number.toString())
        needsComma = true
        return this
    }

    fun value(number: Float): GhostJsonWriter {
        if (!number.isFinite()) throw GhostJsonException("JSON does not support non-finite numbers line NaN or Infinity", 0, 0)
        appendSeparator()
        sink.writeUtf8(number.toString())
        needsComma = true
        return this
    }

    fun value(bool: Boolean): GhostJsonWriter {
        appendSeparator()
        sink.write(if (bool) GhostJsonConstants.TRUE_BYTES else GhostJsonConstants.FALSE_BYTES)
        needsComma = true
        return this
    }

    fun nullValue(): GhostJsonWriter {
        appendSeparator()
        sink.write(GhostJsonConstants.NULL_BYTES)
        needsComma = true
        return this
    }

    fun rawSink(): BufferedSink = sink

    private fun appendSeparator() {
        if (needsComma) {
            sink.writeByte(GhostJsonConstants.COMMA.toInt())
        }
    }

    private fun writeEscaped(text: String) {
        var last = 0
        val length = text.length
        val escapeTable = GhostJsonConstants.BLOCK_ESCAPE
        for (i in 0 until length) {
            val c = text[i]
            val code = c.code
            if (code >= 128 || escapeTable[code].toInt() == 0) continue

            if (i > last) sink.writeUtf8(text, last, i)
            val replacement = when (c) {
                '"' -> "\\\""
                '\\' -> "\\\\"
                '\n' -> "\\n"
                '\r' -> "\\r"
                '\t' -> "\\t"
                '\b' -> "\\b"
                '\u000C' -> "\\f"
                else -> null // Will handle control chars as unicode escapes
            }
            if (replacement != null) {
                sink.writeUtf8(replacement)
            } else {
                writeUnicodeEscape(c)
            }
            last = i + 1
        }
        if (length > last) sink.writeUtf8(text, last, length)
    }

    private fun checkDepth() {
        if (depth >= MAX_DEPTH) {
            throw GhostJsonException("Reached maximum recursion depth ($MAX_DEPTH)", 0, 0)
        }
    }


    private fun writeUnicodeEscape(char: Char) {
        sink.writeUtf8("\\u")
        val hex = char.code.toString(HEX_RADIX)
        repeat(UNICODE_PAD_LENGTH - hex.length) { sink.writeUtf8("0") }
        sink.writeUtf8(hex)
    }

    companion object {
        private const val MAX_DEPTH = 100
        private const val NULL_LITERAL = "null"
        private const val TRUE_LITERAL = "true"
        private const val FALSE_LITERAL = "false"
        private const val CONTROL_CHAR_BOUND = 0x20
        private const val HEX_RADIX = 16
        private const val UNICODE_PAD_LENGTH = 4
        private val CONTROL_CHARS_AND_QUOTES = charArrayOf(
            '"', '\\', '\n', '\r', '\t', '\b', '\u000C'
        )
    }
}

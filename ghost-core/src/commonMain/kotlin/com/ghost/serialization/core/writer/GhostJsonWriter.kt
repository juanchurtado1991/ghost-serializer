package com.ghost.serialization.core.writer

import okio.BufferedSink
import com.ghost.serialization.core.parser.GhostJsonConstants
import com.ghost.serialization.core.exception.GhostJsonException

class GhostJsonWriter(@PublishedApi internal val sink: BufferedSink) {

    @PublishedApi internal var needsComma = false
    @PublishedApi internal val scratch = ByteArray(48)
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
        sink.writeByte(GhostJsonConstants.QUOTE.toInt())
        writeEscaped(key)
        sink.writeUtf8(GhostJsonConstants.COLON_QUOTE)
        needsComma = false
        return this
    }

    fun name(key: okio.ByteString): GhostJsonWriter {
        appendSeparator()
        sink.writeByte(GhostJsonConstants.QUOTE.toInt())
        sink.write(key)
        sink.writeUtf8(GhostJsonConstants.COLON_QUOTE)
        needsComma = false
        return this
    }

    fun writeName(index: Int, options: com.ghost.serialization.core.parser.GhostJsonReader.Options): GhostJsonWriter {
        if (needsComma) {
            sink.write(options.writerHeadersWithComma[index])
        } else {
            sink.write(options.writerHeaders[index])
            needsComma = true
        }
        needsComma = false // Reset for value
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
        if (!number.isFinite()) throw GhostJsonException(GhostJsonConstants.ERR_NON_FINITE, 0, 0)
        appendSeparator()
        
        var count = 0
        var n = if (number < 0) {
            scratch[count++] = '-'.code.toByte()
            -number
        } else number

        val i = n.toLong()
        val intStart = count
        var tempI = i
        if (tempI == 0L) {
            scratch[count++] = '0'.code.toByte()
        } else {
            while (tempI > 0) {
                scratch[count++] = ('0'.code.toLong() + (tempI % 10)).toByte()
                tempI /= 10
            }
            var left = intStart
            var right = count - 1
            while (left < right) {
                val tmp = scratch[left]
                scratch[left] = scratch[right]
                scratch[right] = tmp
                left++; right--
            }
        }
        
        val f = n - i
        if (f > 0.0) {
            scratch[count++] = '.'.code.toByte()
            val fracStart = count
            var fraction = f
            for (step in 1..15) {
                fraction *= 10
                val digit = fraction.toInt()
                scratch[count++] = ('0'.code + digit).toByte()
                fraction -= digit
                if (fraction < 1e-15) break
            }
            // Trim trailing zeros; keep at least one digit after the decimal point
            while (count > fracStart + 1 && scratch[count - 1] == '0'.code.toByte()) {
                count--
            }
        } else {
            // Always emit ".0" so 0.0 is distinguishable from integer 0
            scratch[count++] = '.'.code.toByte()
            scratch[count++] = '0'.code.toByte()
        }
        
        sink.write(scratch, 0, count)
        needsComma = true
        return this
    }

    fun value(number: Float): GhostJsonWriter {
        return value(number.toDouble())
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

    @PublishedApi internal fun appendSeparator() {
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
            
            // Fast path for non-escaped characters
            if (code >= 128 || escapeTable[code].toInt() == 0) continue

            // Write accumulated unescaped segment
            if (i > last) sink.writeUtf8(text, last, i)
            
            val replacement = when (c) {
                '"' -> "\\\""
                '\\' -> "\\\\"
                '\n' -> "\\n"
                '\r' -> "\\r"
                '\t' -> "\\t"
                '\b' -> "\\b"
                '\u000C' -> "\\f"
                else -> null
            }
            
            if (replacement != null) {
                sink.writeUtf8(replacement)
            } else {
                writeUnicodeEscape(c)
            }
            last = i + 1
        }
        
        // Write final segment
        if (length > last) sink.writeUtf8(text, last, length)
    }

    private fun checkDepth() {
        if (depth >= MAX_DEPTH) {
            throw GhostJsonException("${GhostJsonConstants.ERR_DEPTH_EXCEEDED} ($MAX_DEPTH)", 0, 0)
        }
    }


    private fun writeUnicodeEscape(char: Char) {
        sink.writeUtf8(GhostJsonConstants.UNICODE_PREFIX)
        val hex = char.code.toString(HEX_RADIX)
        repeat(UNICODE_PAD_LENGTH - hex.length) { sink.writeUtf8(GhostJsonConstants.ZERO_CHAR) }
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

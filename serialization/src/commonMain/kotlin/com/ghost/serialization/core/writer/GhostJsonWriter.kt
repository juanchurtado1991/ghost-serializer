package com.ghost.serialization.core.writer

import com.ghost.serialization.core.exception.GhostJsonException
import com.ghost.serialization.core.parser.GhostJsonConstants
import com.ghost.serialization.core.parser.JsonReaderOptions
import okio.BufferedSink

/**
 * High-performance JSON writer that streams output directly to a [BufferedSink].
 *
 * GhostJsonWriter is optimized to minimize memory allocations by using an internal
 * work buffer ([scratch]) and supporting pre-encoded field names for faster output.
 *
 * @param sink The destination output stream where JSON data is written.
 */
class GhostJsonWriter(
    @PublishedApi internal val sink: BufferedSink
) {

    @PublishedApi
    internal var needsComma = false

    @PublishedApi
    internal val scratch = ByteArray(48)

    private var depth = 0

    /**
     * Begins a new JSON object '{'. Handles comma insertion between fields automatically.
     */
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

    /**
     * Writes a field name using pre-encoded headers defined in [JsonReaderOptions]
     * to avoid UTF-8 encoding overhead at runtime.
     *
     * @param index The field index defined in the serializer's [JsonReaderOptions].
     */
    fun writeName(
        index: Int,
        options: JsonReaderOptions
    ): GhostJsonWriter {
        if (needsComma) {
            sink.write(options.writerHeadersWithComma[index])
        } else {
            sink.write(options.writerHeaders[index])
        }
        needsComma = false
        return this
    }

    /**
     * Writes both field name and value in a single fused operation to improve CPU cache efficiency.
     */
    fun writeField(
        index: Int,
        options: JsonReaderOptions,
        value: Int
    ) {
        writeName(index, options)
        value(value)
    }

    fun writeField(
        index: Int,
        options: JsonReaderOptions,
        value: Long
    ) {
        writeName(index, options)
        value(value)
    }

    fun writeField(
        index: Int,
        options: JsonReaderOptions,
        value: String
    ) {
        writeName(index, options)
        value(value)
    }

    fun writeField(
        index: Int,
        options: JsonReaderOptions,
        value: Boolean
    ) {
        writeName(index, options)
        value(value)
    }

    fun writeField(
        index: Int,
        options: JsonReaderOptions,
        value: Double
    ) {
        writeName(index, options)
        value(value)
    }

    fun writeField(
        index: Int,
        options: JsonReaderOptions,
        value: Float
    ) {
        writeName(index, options)
        value(value)
    }

    fun value(text: String): GhostJsonWriter {
        appendSeparator()
        sink.writeByte('"'.code)
        writeEscaped(text)
        sink.writeByte('"'.code)
        needsComma = true
        return this
    }

    /** Writes an integer value directly to the sink without intermediate string allocations. */
    fun value(number: Int): GhostJsonWriter {
        appendSeparator()
        writeDecimalLong(number.toLong())
        needsComma = true
        return this
    }

    fun value(number: Long): GhostJsonWriter {
        appendSeparator()
        writeDecimalLong(number)
        needsComma = true
        return this
    }

    private fun writeDecimalLong(value: Long) {
        if (value == Long.MIN_VALUE) {
            sink.writeUtf8("-9223372036854775808")
            return
        }
        var v = value
        if (v < 0) {
            sink.writeByte('-'.code)
            v = -v
        }
        if (v == 0L) {
            sink.writeByte('0'.code)
            return
        }
        var i = 40
        while (v >= 100) {
            val remainder = (v % 100).toInt()
            v /= 100
            scratch[--i] = GhostJsonConstants.FormatUtils.DIGIT_ONES[remainder]
            scratch[--i] = GhostJsonConstants.FormatUtils.DIGIT_TENS[remainder]
        }
        if (v >= 10) {
            val remainder = v.toInt()
            scratch[--i] = GhostJsonConstants.FormatUtils.DIGIT_ONES[remainder]
            scratch[--i] = GhostJsonConstants.FormatUtils.DIGIT_TENS[remainder]
        } else {
            scratch[--i] = (v.toInt() + 48).toByte()
        }
        sink.write(scratch, i, 40 - i)
    }

    fun value(number: Double): GhostJsonWriter {
        appendSeparator()

        val bytesWrittenLength = GhostDoubleFormatter.writeDoubleDirect(
            value = number,
            scratch = scratch,
            offset = 0,
            fallback = { fallbackNum ->
                if (!fallbackNum.isFinite()) {
                    throw GhostJsonException(GhostJsonConstants.ERR_NON_FINITE, 0, 0)
                }
                val str = fallbackNum.toString()
                sink.writeUtf8(str)
                -1
            }
        )

        if (bytesWrittenLength > 0) {
            sink.write(scratch, 0, bytesWrittenLength)
        }

        needsComma = true
        return this
    }

    fun value(number: Float): GhostJsonWriter {
        return value(number.toDouble())
    }

    fun value(bool: Boolean): GhostJsonWriter {
        appendSeparator()
        sink.write(
            if (bool) {
                GhostJsonConstants.TRUE_BYTES
            } else {
                GhostJsonConstants.FALSE_BYTES
            }
        )
        needsComma = true
        return this
    }

    fun nullValue(): GhostJsonWriter {
        appendSeparator()
        sink.write(GhostJsonConstants.NULL_BYTES)
        needsComma = true
        return this
    }

    @PublishedApi
    internal fun appendSeparator() {
        if (needsComma) {
            sink.writeByte(GhostJsonConstants.COMMA.toInt())
        }
    }

    private fun writeEscaped(text: String) {
        val length = text.length
        if (length == 0) return

        var last = 0
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
                else -> null
            }

            if (replacement != null) {
                sink.writeUtf8(replacement)
            } else {
                writeUnicodeEscape(c)
            }
            last = i + 1
        }

        if (length > last) {
            sink.writeUtf8(text, last, length)
        }
    }

    private fun checkDepth() {
        if (depth >= MAX_DEPTH) {
            throw GhostJsonException(
                "${GhostJsonConstants.ERR_DEPTH_EXCEEDED} ($MAX_DEPTH)",
                0,
                0
            )
        }
    }


    private fun writeUnicodeEscape(char: Char) {
        sink.writeUtf8(GhostJsonConstants.UNICODE_PREFIX)
        val hex = char.code.toString(HEX_RADIX)
        repeat(UNICODE_PAD_LENGTH - hex.length) {
            sink.writeUtf8(GhostJsonConstants.ZERO_CHAR)
        }
        sink.writeUtf8(hex)
    }

    companion object {
        private const val MAX_DEPTH = 100
        private const val HEX_RADIX = 16
        private const val UNICODE_PAD_LENGTH = 4
    }
}


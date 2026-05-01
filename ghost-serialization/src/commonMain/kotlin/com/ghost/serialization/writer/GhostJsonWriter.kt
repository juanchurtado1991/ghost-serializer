@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.writer

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.releaseScratchBuffer
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.GhostJsonConstants
import com.ghost.serialization.parser.JsonReaderOptions
import okio.BufferedSink

/**
 * High-performance JSON writer that streams output directly to a [BufferedSink].
 *
 * GhostJsonWriter is optimized to minimize memory allocations by using an internal
 * work buffer and supporting pre-encoded field names for faster output.
 */
class GhostJsonWriter(
    @PublishedApi internal val sink: BufferedSink
) {

    @PublishedApi
    internal var needsComma = false

    @PublishedApi
    internal var bytes: ByteArray? = null

    private fun getScratch(minSize: Int = 48): ByteArray {
        val current = bytes
        if (current != null && current.size >= minSize) return current
        
        // If we need a larger buffer, release old one and get new one
        if (current != null) releaseScratchBuffer(current)
        
        val s = acquireScratchBuffer(minSize)
        bytes = s
        return s
    }

    /**
     * Releases the internal scratch buffer back to the pool.
     * Must be called at the end of the root serialization process.
     */
    @InternalGhostApi
    fun release() {
        bytes?.let {
            releaseScratchBuffer(it)
            bytes = null
        }
    }

    private var depth = 0

    /**
     * Begins a new JSON object '{'. Handles comma insertion between fields automatically.
     */
    fun beginObject(): GhostJsonWriter {
        checkDepth()
        appendSeparator()
        sink.writeByte(GhostJsonConstants.OPEN_OBJ_INT)
        needsComma = false
        depth++
        return this
    }

    fun endObject(): GhostJsonWriter {
        sink.writeByte(GhostJsonConstants.CLOSE_OBJ_INT)
        needsComma = true
        depth--
        return this
    }

    fun beginArray(): GhostJsonWriter {
        checkDepth()
        appendSeparator()
        sink.writeByte(GhostJsonConstants.OPEN_ARR_INT)
        needsComma = false
        depth++
        return this
    }

    fun endArray(): GhostJsonWriter {
        sink.writeByte(GhostJsonConstants.CLOSE_ARR_INT)
        needsComma = true
        depth--
        return this
    }

    fun name(key: String): GhostJsonWriter {
        appendSeparator()
        sink.writeByte(GhostJsonConstants.QUOTE_INT)
        writeEscaped(key)
        sink.write(GhostJsonConstants.COLON_QUOTE_BS)
        needsComma = false
        return this
    }

    fun name(key: okio.ByteString): GhostJsonWriter {
        appendSeparator()
        sink.write(key)
        needsComma = false
        return this
    }

    @InternalGhostApi
    fun writeNameRaw(header: okio.ByteString): GhostJsonWriter = name(header)

    /**
     * Writes the start of an object and the first field in a single fused operation.
     */
    fun writeFirstField(index: Int, options: JsonReaderOptions, value: Int): GhostJsonWriter =
        writeFirstField(options.writerFirstHeaders[index], value)

    @InternalGhostApi
    fun writeFirstField(header: okio.ByteString, value: Int): GhostJsonWriter {
        appendSeparator()
        checkDepth()
        sink.write(header)
        writeIntValueRaw(value)
        needsComma = true
        depth++
        return this
    }

    fun writeFirstField(index: Int, options: JsonReaderOptions, value: Long): GhostJsonWriter =
        writeFirstField(options.writerFirstHeaders[index], value)

    @InternalGhostApi
    fun writeFirstField(header: okio.ByteString, value: Long): GhostJsonWriter {
        appendSeparator()
        checkDepth()
        sink.write(header)
        writeLongValueRaw(value)
        needsComma = true
        depth++
        return this
    }

    fun writeFirstField(index: Int, options: JsonReaderOptions, value: String): GhostJsonWriter =
        writeFirstField(options.writerFirstHeaders[index], value)

    @InternalGhostApi
    fun writeFirstField(header: okio.ByteString, value: String): GhostJsonWriter {
        appendSeparator()
        checkDepth()
        sink.write(header)
        writeStringValueRaw(value)
        needsComma = true
        depth++
        return this
    }

    @InternalGhostApi
    fun writeFirstField(header: okio.ByteString, value: Boolean): GhostJsonWriter {
        appendSeparator()
        checkDepth()
        sink.write(header)
        writeBooleanValueRaw(value)
        needsComma = true
        depth++
        return this
    }

    @InternalGhostApi
    fun writeFirstField(header: okio.ByteString, value: Double): GhostJsonWriter {
        appendSeparator()
        checkDepth()
        sink.write(header)
        writeDoubleValueRaw(value)
        needsComma = true
        depth++
        return this
    }

    @InternalGhostApi
    fun writeFirstField(header: okio.ByteString, value: Float): GhostJsonWriter =
        writeFirstField(header, value.toDouble())

    fun writeField(index: Int, options: JsonReaderOptions, value: Int): GhostJsonWriter =
        writeField(options.writerHeaders[index], value)

    @InternalGhostApi
    fun writeField(header: okio.ByteString, value: Int): GhostJsonWriter {
        appendSeparator()
        sink.write(header)
        writeIntValueRaw(value)
        needsComma = true
        return this
    }

    fun writeField(index: Int, options: JsonReaderOptions, value: Long): GhostJsonWriter =
        writeField(options.writerHeaders[index], value)

    @InternalGhostApi
    fun writeField(header: okio.ByteString, value: Long): GhostJsonWriter {
        appendSeparator()
        sink.write(header)
        writeLongValueRaw(value)
        needsComma = true
        return this
    }

    fun writeField(index: Int, options: JsonReaderOptions, value: String): GhostJsonWriter =
        writeField(options.writerHeaders[index], value)

    @InternalGhostApi
    fun writeField(header: okio.ByteString, value: String): GhostJsonWriter {
        appendSeparator()
        sink.write(header)
        writeStringValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: okio.ByteString, value: Boolean): GhostJsonWriter {
        appendSeparator()
        sink.write(header)
        writeBooleanValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: okio.ByteString, value: Double): GhostJsonWriter {
        appendSeparator()
        sink.write(header)
        writeDoubleValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: okio.ByteString, value: Float): GhostJsonWriter =
        writeField(header, value.toDouble())

    fun writeField(index: Int, options: JsonReaderOptions, value: Boolean): GhostJsonWriter =
        name(options.writerHeaders[index]).value(value)

    fun writeField(index: Int, options: JsonReaderOptions, value: Double): GhostJsonWriter =
        name(options.writerHeaders[index]).value(value)

    fun writeField(index: Int, options: JsonReaderOptions, value: Float): GhostJsonWriter =
        name(options.writerHeaders[index]).value(value.toDouble())

    fun value(text: String): GhostJsonWriter {
        appendSeparator()
        writeStringValueRaw(text)
        needsComma = true
        return this
    }

    fun value(number: Int): GhostJsonWriter {
        appendSeparator()
        writeIntValueRaw(number)
        needsComma = true
        return this
    }

    fun value(number: Long): GhostJsonWriter {
        appendSeparator()
        writeLongValueRaw(number)
        needsComma = true
        return this
    }

    fun value(number: Double): GhostJsonWriter {
        appendSeparator()
        writeDoubleValueRaw(number)
        needsComma = true
        return this
    }

    fun value(number: Float): GhostJsonWriter = value(number.toDouble())

    fun value(value: Boolean): GhostJsonWriter {
        appendSeparator()
        sink.write(if (value) GhostJsonConstants.TRUE_BS else GhostJsonConstants.FALSE_BS)
        needsComma = true
        return this
    }

    fun nullValue(): GhostJsonWriter {
        appendSeparator()
        sink.write(GhostJsonConstants.NULL_BS)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeBooleanValueRaw(value: Boolean) {
        sink.write(if (value) GhostJsonConstants.TRUE_BS else GhostJsonConstants.FALSE_BS)
        needsComma = true
    }

    @InternalGhostApi
    fun writeIntValueRaw(value: Int) {
        when (value) {
            0 -> sink.writeByte(GhostJsonConstants.ZERO_INT)
            1 -> sink.writeByte(GhostJsonConstants.ONE_INT)
            2 -> sink.writeByte(GhostJsonConstants.TWO_INT)
            -1 -> {
                sink.writeByte(GhostJsonConstants.MINUS_INT)
                sink.writeByte(GhostJsonConstants.ONE_INT)
            }
            Int.MIN_VALUE -> {
                sink.write(GhostJsonConstants.MIN_INT_BS)
                needsComma = true
                return
            }
            else -> writeLongValueRawInternal(value.toLong())
        }
        needsComma = true
    }
    
    @InternalGhostApi
    fun writeLongValueRaw(value: Long) {
        if (value >= -1L && value <= 2L) {
            writeIntValueRaw(value.toInt())
            return
        }
        writeLongValueRawInternal(value)
    }

    private fun writeLongValueRawInternal(value: Long) {
        val scratch = getScratch(24) // Longs need ~20 chars
        var pos = 20 // Long.MIN_VALUE is 20 chars
        var v = value
        val isNegative = v < 0
        if (isNegative) {
            if (v == Long.MIN_VALUE) {
                sink.write(GhostJsonConstants.MIN_LONG_BS)
                needsComma = true
                return
            }
            v = -v
        }

        do {
            scratch[--pos] = (GhostJsonConstants.ZERO_INT + (v % 10).toInt()).toByte()
            v /= 10
        } while (v != 0L)

        if (isNegative) {
            scratch[--pos] = GhostJsonConstants.MINUS
        }

        sink.write(scratch, pos, 20 - pos)
        needsComma = true
    }

    @InternalGhostApi
    fun writeDoubleValueRaw(number: Double) {
        // Fast path for whole numbers: use our optimized internal writer
        if (number >= GhostJsonConstants.MIN_SAFE_INTEGER_DOUBLE && 
            number <= GhostJsonConstants.MAX_SAFE_INTEGER_DOUBLE && 
            number % GhostJsonConstants.WHOLE_NUMBER_CHECK == GhostJsonConstants.ZERO_DOUBLE) {
            writeLongValueRawInternal(number.toLong())
            sink.write(GhostJsonConstants.DOT_ZERO) // Maintain .0 for floating point parity
            needsComma = true
            return
        }

        val bytesWrittenLength = GhostDoubleFormatter.writeDoubleDirect(
            value = number,
            scratch = getScratch(32), // Doubles need ~24 chars
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
            sink.write(getScratch(), 0, bytesWrittenLength)
        }
        needsComma = true
    }

    @InternalGhostApi
    fun writeNullValueRaw() {
        sink.write(GhostJsonConstants.NULL_BS)
        needsComma = true
    }

    @PublishedApi
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun appendSeparator() {
        if (needsComma) {
            sink.writeByte(GhostJsonConstants.COMMA_INT)
            needsComma = false
        }
    }

    @InternalGhostApi
    fun writeRaw(byte: Int) {
        sink.writeByte(byte)
    }

    @InternalGhostApi
    fun writeRaw(bytes: ByteArray) {
        sink.write(bytes)
    }

    @InternalGhostApi
    fun writeStringValueRaw(value: String) {
        sink.writeByte(GhostJsonConstants.QUOTE_INT)
        writeEscaped(value)
        sink.writeByte(GhostJsonConstants.QUOTE_INT)
        needsComma = true
    }

    private fun writeEscaped(text: String) {
        val length = text.length
        if (length == 0) return

        val needsEscapeTable = GhostJsonConstants.NEEDS_ESCAPE_LUT
        val replacements = GhostJsonConstants.ESCAPE_REPLACEMENTS
        
        var lastWriteIndex = 0
        var currentIndex = 0
        
        while (currentIndex < length) {
            val currentChar = text[currentIndex]
            val charCode = currentChar.code
            
            // Check if character needs escaping or is non-ASCII
            if (charCode < GhostJsonConstants.ASCII_LIMIT && !needsEscapeTable[charCode]) {
                currentIndex++
                continue
            }

            // Flush pending clean segment
            if (currentIndex > lastWriteIndex) {
                sink.writeUtf8(text, lastWriteIndex, currentIndex)
            }

            if (charCode < GhostJsonConstants.ASCII_LIMIT) {
                val replacement = replacements[charCode]
                if (replacement != null) {
                    sink.write(replacement)
                } else {
                    writeUnicodeEscape(currentChar)
                }
                lastWriteIndex = currentIndex + 1
            } else {
                // Non-ASCII or Surrogate pairs
                if (currentChar.isHighSurrogate() && currentIndex + 1 < length && text[currentIndex + 1].isLowSurrogate()) {
                    sink.writeUtf8(text, currentIndex, currentIndex + 2)
                    lastWriteIndex = currentIndex + 2
                    currentIndex++ // Extra increment for surrogate
                } else {
                    sink.writeUtf8(text, currentIndex, currentIndex + 1)
                    lastWriteIndex = currentIndex + 1
                }
            }
            currentIndex++
        }

        // Final flush
        if (lastWriteIndex < length) {
            sink.writeUtf8(text, lastWriteIndex, length)
        }
    }

    private fun checkDepth() {
        if (depth >= GhostJsonConstants.MAX_DEPTH) {
            throw GhostJsonException("${GhostJsonConstants.ERR_DEPTH_EXCEEDED} (${GhostJsonConstants.MAX_DEPTH})", 0, 0)
        }
    }

    private fun writeUnicodeEscape(char: Char) {
        val scratch = getScratch()
        scratch[0] = GhostJsonConstants.BACKSLASH
        scratch[1] = GhostJsonConstants.UNICODE_PREFIX_U
        val code = char.code
        val hexChars = GhostJsonConstants.HEX_CHARS
        scratch[2] = hexChars[(code shr GhostJsonConstants.SHIFT_12) and GhostJsonConstants.HEX_MASK]
        scratch[3] = hexChars[(code shr GhostJsonConstants.SHIFT_8) and GhostJsonConstants.HEX_MASK]
        scratch[4] = hexChars[(code shr GhostJsonConstants.SHIFT_4) and GhostJsonConstants.HEX_MASK]
        scratch[5] = hexChars[code and GhostJsonConstants.HEX_MASK]
        sink.write(scratch, 0, 6)
    }
}

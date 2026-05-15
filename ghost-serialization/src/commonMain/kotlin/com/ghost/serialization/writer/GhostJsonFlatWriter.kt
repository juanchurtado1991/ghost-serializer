@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.writer

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.GhostJsonConstants.ASCII_LIMIT
import com.ghost.serialization.parser.GhostJsonConstants.BACKSLASH
import com.ghost.serialization.parser.GhostJsonConstants.CLOSE_ARR_INT
import com.ghost.serialization.parser.GhostJsonConstants.CLOSE_OBJ_INT
import com.ghost.serialization.parser.GhostJsonConstants.BITMASK_INDEX_MASK
import com.ghost.serialization.parser.GhostJsonConstants.BITMASK_WIDTH
import com.ghost.serialization.parser.GhostJsonConstants.COLON_QUOTE_BS
import com.ghost.serialization.parser.GhostJsonConstants.COMMA_INT
import com.ghost.serialization.parser.GhostJsonConstants.DOT_ZERO
import com.ghost.serialization.parser.GhostJsonConstants.DOUBLE_DIGIT_LUT
import com.ghost.serialization.parser.GhostJsonConstants.EMPTY_STRING_BS
import com.ghost.serialization.parser.GhostJsonConstants.ERR_DEPTH_EXCEEDED
import com.ghost.serialization.parser.GhostJsonConstants.ERR_NON_FINITE
import com.ghost.serialization.parser.GhostJsonConstants.ESCAPE_REPLACEMENTS
import com.ghost.serialization.parser.GhostJsonConstants.FALSE_BS
import com.ghost.serialization.parser.GhostJsonConstants.HEX_CHARS
import com.ghost.serialization.parser.GhostJsonConstants.HEX_MASK
import com.ghost.serialization.parser.GhostJsonConstants.HUNDRED_LONG
import com.ghost.serialization.parser.GhostJsonConstants.LONG_SCRATCH_SIZE
import com.ghost.serialization.parser.GhostJsonConstants.MAX_DEPTH
import com.ghost.serialization.parser.GhostJsonConstants.MAX_SAFE_INTEGER_DOUBLE
import com.ghost.serialization.parser.GhostJsonConstants.MINUS
import com.ghost.serialization.parser.GhostJsonConstants.MINUS_ONE_BS
import com.ghost.serialization.parser.GhostJsonConstants.MIN_INT_BS
import com.ghost.serialization.parser.GhostJsonConstants.MIN_LONG_BS
import com.ghost.serialization.parser.GhostJsonConstants.MIN_SAFE_INTEGER_DOUBLE
import com.ghost.serialization.parser.GhostJsonConstants.NEEDS_ESCAPE_MASK_HIGH
import com.ghost.serialization.parser.GhostJsonConstants.NEEDS_ESCAPE_MASK_LOW
import com.ghost.serialization.parser.GhostJsonConstants.NULL_BS
import com.ghost.serialization.parser.GhostJsonConstants.ONE_INT
import com.ghost.serialization.parser.GhostJsonConstants.OPEN_ARR_INT
import com.ghost.serialization.parser.GhostJsonConstants.OPEN_OBJ_INT
import com.ghost.serialization.parser.GhostJsonConstants.QUOTE_BYTE
import com.ghost.serialization.parser.GhostJsonConstants.QUOTE_INT
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_12
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_4
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_8
import com.ghost.serialization.parser.GhostJsonConstants.STRING_QUOTE_PAIR_BYTES
import com.ghost.serialization.parser.GhostJsonConstants.SURROGATE_PAIR_LENGTH
import com.ghost.serialization.parser.GhostJsonConstants.TEN_LONG
import com.ghost.serialization.parser.GhostJsonConstants.TRUE_BS
import com.ghost.serialization.parser.GhostJsonConstants.TWO_INT
import com.ghost.serialization.parser.GhostJsonConstants.UNICODE_ESCAPE_LENGTH
import com.ghost.serialization.parser.GhostJsonConstants.UNICODE_PREFIX_U
import com.ghost.serialization.parser.GhostJsonConstants.WHOLE_NUMBER_CHECK
import com.ghost.serialization.parser.GhostJsonConstants.WRITER_SCRATCH_SIZE
import com.ghost.serialization.parser.GhostJsonConstants.ZERO_DOUBLE
import com.ghost.serialization.parser.GhostJsonConstants.ZERO_INT
import com.ghost.serialization.parser.JsonReaderOptions
import com.ghost.serialization.releaseScratchBuffer
import okio.ByteString

/**
 * In-memory specialization of [GhostJsonWriter] backed by a contiguous
 * [FlatByteArrayWriter] instead of the segmented [okio.Buffer] used by the
 * streaming path.
 *
 * Why a separate concrete class — and not a sealed/interface hierarchy — :
 *
 *  - The KSP-generated `serialize(writer: GhostJsonFlatWriter, value: T)`
 *    overload sees a *known final* receiver. Every byte-level call (`buffer.
 *    writeByte`, `buffer.write`, …) resolves to a final method on
 *    [FlatByteArrayWriter] and is fully inlined by HotSpot/ART/Kotin-Native
 *    AOT. There is no v-table lookup and no interface dispatch on the hot
 *    path, even when both writer types are used in the same JVM.
 *  - The streaming path keeps [GhostJsonWriter] (also `final`) untouched, so
 *    `Ghost.serialize(sink, value)` retains its existing throughput — no
 *    regressions for callers that need true streaming I/O.
 *
 * The two writers expose the *same* public method names (e.g. [beginObject],
 * [writeField], [value]). KSP can therefore emit two `serialize` overloads
 * that share an identical body, only differing in the parameter type. Hand-
 * written serializers must do the same; see [com.ghost.serialization.contract.GhostSerializer].
 */
class GhostJsonFlatWriter @InternalGhostApi internal constructor(
    @PublishedApi internal val buffer: FlatByteArrayWriter
) {

    @PublishedApi
    internal var needsComma: Boolean = false

    private var depth: Int = 0

    @PublishedApi
    internal var scratch: ByteArray? = null

    @PublishedApi
    internal fun acquireScratch(): ByteArray = scratch
        ?: acquireScratchBuffer(WRITER_SCRATCH_SIZE)
            .also { scratch = it }

    /**
     * Returns the scratch buffer to its pool. Must be called once at the end
     * of the root encode so subsequent encodes (potentially on other threads)
     * can reuse the buffer.
     */
    @InternalGhostApi
    fun release() {
        scratch?.let {
            releaseScratchBuffer(it)
            scratch = null
        }
        needsComma = false
        depth = 0
    }

    /**
     * Resets writer state for reuse from a pool while keeping the scratch
     * buffer warm. Pair with a [FlatByteArrayWriter.reset] on the underlying
     * [buffer] to start a fresh encode without re-allocating either.
     */
    @InternalGhostApi
    fun reset() {
        needsComma = false
        depth = 0
    }

    /**
     * No-op for the flat-array path: bytes already live in [buffer] and are
     * read back synchronously by callers via [FlatByteArrayWriter.toByteArray]
     * / [FlatByteArrayWriter.toStringUtf8]. Defined so the writer surface
     * matches [GhostJsonWriter.flush].
     */
    @InternalGhostApi
    @Suppress("EmptyFunctionBlock")
    fun flush() {}

    // ── Structural ────────────────────────────────────────────────────────────

    /**
     * Starts a new JSON object.
     * Automatically handles comma insertion and depth tracking.
     */
    fun beginObject(): GhostJsonFlatWriter {
        checkDepth()
        appendSeparator()
        buffer.writeByte(OPEN_OBJ_INT)
        needsComma = false
        depth++
        return this
    }

    /** Ends the current JSON object. */
    fun endObject(): GhostJsonFlatWriter {
        buffer.writeByte(CLOSE_OBJ_INT)
        needsComma = true
        depth--
        return this
    }

    fun beginArray(): GhostJsonFlatWriter {
        checkDepth()
        appendSeparator()
        buffer.writeByte(OPEN_ARR_INT)
        needsComma = false
        depth++
        return this
    }

    fun endArray(): GhostJsonFlatWriter {
        buffer.writeByte(CLOSE_ARR_INT)
        needsComma = true
        depth--
        return this
    }

    /**
     * Writes a field name as a string.
     * Escapes the key and appends the colon separator.
     */
    fun name(key: String): GhostJsonFlatWriter {
        appendSeparator()
        buffer.writeByte(QUOTE_INT)
        writeEscaped(key)
        buffer.write(COLON_QUOTE_BS)
        needsComma = false
        return this
    }

    /**
     * Writes a pre-encoded field name [ByteString].
     * This is the fastest way to write field names as it avoids runtime escaping.
     */
    fun name(key: ByteString): GhostJsonFlatWriter {
        appendSeparator()
        buffer.write(key)
        needsComma = false
        return this
    }

    @InternalGhostApi
    fun writeNameRaw(header: ByteString): GhostJsonFlatWriter = name(header)

    // ── writeFirstField (fused open+field, no leading comma) ─────────────────

    @InternalGhostApi
    fun writeFirstField(header: ByteString, value: Int): GhostJsonFlatWriter {
        checkDepth()
        buffer.write(header)
        writeIntValueRaw(value)
        needsComma = true
        depth++
        return this
    }

    fun writeFirstField(index: Int, options: JsonReaderOptions, value: Int): GhostJsonFlatWriter =
        writeFirstField(options.writerFirstHeaders[index], value)

    @InternalGhostApi
    fun writeFirstField(header: ByteString, value: Long): GhostJsonFlatWriter {
        checkDepth()
        buffer.write(header)
        writeLongValueRaw(value)
        needsComma = true
        depth++
        return this
    }

    fun writeFirstField(index: Int, options: JsonReaderOptions, value: Long): GhostJsonFlatWriter =
        writeFirstField(options.writerFirstHeaders[index], value)

    @InternalGhostApi
    fun writeFirstField(header: ByteString, value: String): GhostJsonFlatWriter {
        checkDepth()
        buffer.write(header)
        writeStringValueRaw(value)
        needsComma = true
        depth++
        return this
    }

    fun writeFirstField(index: Int, options: JsonReaderOptions, value: String): GhostJsonFlatWriter =
        writeFirstField(options.writerFirstHeaders[index], value)

    @InternalGhostApi
    fun writeFirstField(header: ByteString, value: Boolean): GhostJsonFlatWriter {
        checkDepth()
        buffer.write(header)
        writeBooleanValueRaw(value)
        needsComma = true
        depth++
        return this
    }

    @InternalGhostApi
    fun writeFirstField(header: ByteString, value: Double): GhostJsonFlatWriter {
        checkDepth()
        buffer.write(header)
        writeDoubleValueRaw(value)
        needsComma = true
        depth++
        return this
    }

    @InternalGhostApi
    fun writeFirstField(header: ByteString, value: Float): GhostJsonFlatWriter =
        writeFirstField(header, value.toDouble())

    // ── writeField (with appendSeparator) ────────────────────────────────────

    @InternalGhostApi
    fun writeField(header: ByteString, value: Int): GhostJsonFlatWriter {
        if (needsComma) buffer.writeByte(COMMA_INT)
        buffer.write(header)
        writeIntValueRaw(value)
        needsComma = true
        return this
    }

    fun writeField(index: Int, options: JsonReaderOptions, value: Int): GhostJsonFlatWriter =
        writeField(options.writerHeaders[index], value)

    @InternalGhostApi
    fun writeField(header: ByteString, value: Long): GhostJsonFlatWriter {
        if (needsComma) buffer.writeByte(COMMA_INT)
        buffer.write(header)
        writeLongValueRaw(value)
        needsComma = true
        return this
    }

    fun writeField(index: Int, options: JsonReaderOptions, value: Long): GhostJsonFlatWriter =
        writeField(options.writerHeaders[index], value)

    @InternalGhostApi
    fun writeField(header: ByteString, value: String): GhostJsonFlatWriter {
        if (needsComma) buffer.writeByte(COMMA_INT)
        buffer.write(header)
        writeStringValueRaw(value)
        needsComma = true
        return this
    }

    fun writeField(index: Int, options: JsonReaderOptions, value: String): GhostJsonFlatWriter =
        writeField(options.writerHeaders[index], value)

    @InternalGhostApi
    fun writeField(header: ByteString, value: Boolean): GhostJsonFlatWriter {
        if (needsComma) buffer.writeByte(COMMA_INT)
        buffer.write(header)
        writeBooleanValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: ByteString, value: Double): GhostJsonFlatWriter {
        if (needsComma) buffer.writeByte(COMMA_INT)
        buffer.write(header)
        writeDoubleValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: ByteString, value: Float): GhostJsonFlatWriter =
        writeField(header, value.toDouble())

    fun writeField(index: Int, options: JsonReaderOptions, value: Boolean): GhostJsonFlatWriter =
        writeField(options.writerHeaders[index], value)

    fun writeField(index: Int, options: JsonReaderOptions, value: Double): GhostJsonFlatWriter =
        writeField(options.writerHeaders[index], value)

    fun writeField(index: Int, options: JsonReaderOptions, value: Float): GhostJsonFlatWriter =
        writeField(options.writerHeaders[index], value.toDouble())

    // ── writeFieldWithComma (leading comma pre-baked, no appendSeparator) ────

    @InternalGhostApi
    fun writeFieldWithComma(header: ByteString, value: Int): GhostJsonFlatWriter {
        buffer.write(header)
        writeIntValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeFieldWithComma(header: ByteString, value: Long): GhostJsonFlatWriter {
        buffer.write(header)
        writeLongValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeFieldWithComma(header: ByteString, value: String): GhostJsonFlatWriter {
        buffer.write(header)
        writeStringValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeFieldWithComma(header: ByteString, value: Boolean): GhostJsonFlatWriter {
        buffer.write(header)
        writeBooleanValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeFieldWithComma(header: ByteString, value: Double): GhostJsonFlatWriter {
        buffer.write(header)
        writeDoubleValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeFieldWithComma(header: ByteString, value: Float): GhostJsonFlatWriter =
        writeFieldWithComma(header, value.toDouble())

    @InternalGhostApi
    fun writeNameRawWithComma(header: ByteString): GhostJsonFlatWriter {
        buffer.write(header)
        needsComma = false
        return this
    }

    // ── value() public API ────────────────────────────────────────────────────

    fun value(text: String): GhostJsonFlatWriter {
        appendSeparator()
        writeStringValueRaw(text)
        needsComma = true
        return this
    }

    fun value(number: Int): GhostJsonFlatWriter {
        appendSeparator()
        writeIntValueRaw(number)
        needsComma = true
        return this
    }

    fun value(number: Long): GhostJsonFlatWriter {
        appendSeparator()
        writeLongValueRaw(number)
        needsComma = true
        return this
    }

    fun value(number: Double): GhostJsonFlatWriter {
        appendSeparator()
        writeDoubleValueRaw(number)
        needsComma = true
        return this
    }

    fun value(number: Float): GhostJsonFlatWriter = value(number.toDouble())

    fun value(value: Boolean): GhostJsonFlatWriter {
        appendSeparator()
        buffer.write(if (value) TRUE_BS else FALSE_BS)
        needsComma = true
        return this
    }

    fun nullValue(): GhostJsonFlatWriter {
        appendSeparator()
        buffer.write(NULL_BS)
        needsComma = true
        return this
    }

    // ── Raw value writers (called by generated code) ──────────────────────────

    @InternalGhostApi
    fun writeBooleanValueRaw(value: Boolean) {
        buffer.write(if (value) TRUE_BS else FALSE_BS)
    }

    @InternalGhostApi
    fun writeIntValueRaw(value: Int) {
        when (value) {
            0 -> buffer.writeByte(ZERO_INT)
            1 -> buffer.writeByte(ONE_INT)
            2 -> buffer.writeByte(TWO_INT)
            -1 -> buffer.write(MINUS_ONE_BS)
            Int.MIN_VALUE -> buffer.write(MIN_INT_BS)
            else -> writeLongValueRawInternal(value.toLong())
        }
    }

    /**
     * Writes a long value without a field name or separator.
     * Uses a fast `when` dispatch for common values.
     */
    @InternalGhostApi
    fun writeLongValueRaw(value: Long) {
        when (value) {
            0L -> buffer.writeByte(ZERO_INT)
            1L -> buffer.writeByte(ONE_INT)
            2L -> buffer.writeByte(TWO_INT)
            -1L -> buffer.write(MINUS_ONE_BS)
            else -> writeLongValueRawInternal(value)
        }
    }

    /**
     * Internal implementation for writing Long values into the scratch buffer.
     * Optimized for 2-digit divisions using a lookup table and backward writing.
     * Final output is a single bulk array copy into [buffer].
     */
    private fun writeLongValueRawInternal(value: Long) {
        val scratchBuf = scratch ?: acquireScratch()
        val scratchEnd = LONG_SCRATCH_SIZE
        var writePosition = scratchEnd
        var remainingValue = value
        val isNegative = remainingValue < 0
        if (isNegative) {
            if (remainingValue == Long.MIN_VALUE) {
                buffer.write(MIN_LONG_BS)
                return
            }
            remainingValue = -remainingValue
        }

        while (remainingValue >= HUNDRED_LONG) {
            val twoDigitIndex = (remainingValue % HUNDRED_LONG).toInt() * 2
            scratchBuf[--writePosition] = DOUBLE_DIGIT_LUT[twoDigitIndex + 1]
            scratchBuf[--writePosition] = DOUBLE_DIGIT_LUT[twoDigitIndex]
            remainingValue /= HUNDRED_LONG
        }

        if (remainingValue < TEN_LONG) {
            scratchBuf[--writePosition] = (ZERO_INT + remainingValue.toInt()).toByte()
        } else {
            val twoDigitIndex = remainingValue.toInt() * 2
            scratchBuf[--writePosition] = DOUBLE_DIGIT_LUT[twoDigitIndex + 1]
            scratchBuf[--writePosition] = DOUBLE_DIGIT_LUT[twoDigitIndex]
        }

        if (isNegative) {
            scratchBuf[--writePosition] = MINUS
        }

        buffer.write(scratchBuf, writePosition, scratchEnd - writePosition)
    }

    @InternalGhostApi
    fun writeDoubleValueRaw(number: Double) {
        if (number >= MIN_SAFE_INTEGER_DOUBLE &&
            number <= MAX_SAFE_INTEGER_DOUBLE &&
            number % WHOLE_NUMBER_CHECK == ZERO_DOUBLE
        ) {
            writeLongValueRawInternal(number.toLong())
            buffer.write(DOT_ZERO)
            return
        }

        val scratchBuf = acquireScratch()
        val bytesWrittenLength = GhostDoubleFormatter.writeDoubleDirect(
            value = number,
            scratch = scratchBuf,
            offset = 0,
            fallback = { fallbackNum ->
                if (!fallbackNum.isFinite()) {
                    throw GhostJsonException(ERR_NON_FINITE, 0, 0)
                }
                buffer.writeUtf8(fallbackNum.toString())
                -1
            }
        )

        if (bytesWrittenLength > 0) {
            buffer.write(scratchBuf, 0, bytesWrittenLength)
        }
    }

    @InternalGhostApi
    fun writeNullValueRaw() {
        buffer.write(NULL_BS)
    }

    @PublishedApi
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun appendSeparator() {
        if (needsComma) {
            buffer.writeByte(COMMA_INT)
            needsComma = false
        }
    }

    @InternalGhostApi
    fun writeRaw(byte: Int) {
        buffer.writeByte(byte)
    }

    @InternalGhostApi
    fun writeRaw(bytes: ByteArray) {
        buffer.write(bytes)
    }

    /**
     * Writes a string value with quotes and proper escaping.
     * Uses "Quote Fusion" to batch quotes with content in a single buffer write.
     */
    @InternalGhostApi
    fun writeStringValueRaw(value: String) {
        val length = value.length
        if (length == 0) {
            buffer.write(EMPTY_STRING_BS)
            return
        }

        val scratchBuf = scratch ?: acquireScratch()
        if (length + STRING_QUOTE_PAIR_BYTES > scratchBuf.size) {
            buffer.writeByte(QUOTE_INT)
            writeEscaped(value)
            buffer.writeByte(QUOTE_INT)
            return
        }

        scratchBuf[0] = QUOTE_BYTE
        writeEscapedIntoScratch(value, length, scratchBuf)
    }

    private fun writeEscaped(text: String, start: Int = 0) {
        val length = text.length
        val remaining = length - start
        if (remaining <= 0) return

        val maskLow = NEEDS_ESCAPE_MASK_LOW
        val maskHigh = NEEDS_ESCAPE_MASK_HIGH
        val replacements = ESCAPE_REPLACEMENTS
        val scratchBuf = acquireScratch()
        val scratchSize = scratchBuf.size

        if (remaining <= scratchSize) {
            var scratchPos = 0
            var index = start
            while (index < length) {
                val charCode = text[index].code
                if (charCode < ASCII_LIMIT && ((if (charCode < BITMASK_WIDTH) maskLow else maskHigh) shr (charCode and BITMASK_INDEX_MASK)) and 1L == 0L) {
                    scratchBuf[scratchPos++] = charCode.toByte()
                    index++
                    continue
                }
                if (scratchPos > 0) {
                    buffer.write(scratchBuf, 0, scratchPos)
                    scratchPos = 0
                }
                if (charCode < ASCII_LIMIT) {
                    val replacement = replacements[charCode]
                    if (replacement != null) buffer.write(replacement)
                    else writeUnicodeEscape(text[index], scratchBuf)
                } else {
                    val char = text[index]
                    if (char.isHighSurrogate() && index + 1 < length && text[index + 1].isLowSurrogate()) {
                        buffer.writeUtf8(text, index, index + SURROGATE_PAIR_LENGTH)
                        index++
                    } else {
                        buffer.writeUtf8(text, index, index + 1)
                    }
                }
                index++
            }
            if (scratchPos > 0) buffer.write(scratchBuf, 0, scratchPos)
            return
        }

        var scratchPos = 0
        var index = start
        while (index < length) {
            val charCode = text[index].code
            if (charCode < ASCII_LIMIT && ((if (charCode < BITMASK_WIDTH) maskLow else maskHigh) shr (charCode and BITMASK_INDEX_MASK)) and 1L == 0L) {
                scratchBuf[scratchPos++] = charCode.toByte()
                if (scratchPos == scratchSize) {
                    buffer.write(scratchBuf, 0, scratchPos)
                    scratchPos = 0
                }
                index++
                continue
            }

            if (scratchPos > 0) {
                buffer.write(scratchBuf, 0, scratchPos)
                scratchPos = 0
            }

            if (charCode < ASCII_LIMIT) {
                val replacement = replacements[charCode]
                if (replacement != null) buffer.write(replacement)
                else writeUnicodeEscape(text[index], scratchBuf)
            } else {
                val char = text[index]
                if (char.isHighSurrogate() && index + 1 < length && text[index + 1].isLowSurrogate()) {
                    buffer.writeUtf8(text, index, index + SURROGATE_PAIR_LENGTH)
                    index++
                } else {
                    buffer.writeUtf8(text, index, index + 1)
                }
            }
            index++
        }

        if (scratchPos > 0) buffer.write(scratchBuf, 0, scratchPos)
    }

    /**
     * Optimized escaping that fuses opening/closing quotes into a single scratch buffer flush.
     * If an escape character is found, it falls back to standard [writeEscaped].
     */
    private fun writeEscapedIntoScratch(text: String, length: Int, scratchBuf: ByteArray) {
        var scratchPos = 1 // Start after the opening quote already written at index 0.
        var index = 0
        val maskLow = NEEDS_ESCAPE_MASK_LOW
        val maskHigh = NEEDS_ESCAPE_MASK_HIGH

        while (index < length) {
            val charCode = text[index].code
            if (charCode < ASCII_LIMIT && ((if (charCode < BITMASK_WIDTH) maskLow else maskHigh) shr (charCode and BITMASK_INDEX_MASK)) and 1L == 0L) {
                scratchBuf[scratchPos++] = charCode.toByte()
                index++
                continue
            }

            // Flush what we have (including the opening quote) and fall back.
            buffer.write(scratchBuf, 0, scratchPos)

            if (charCode < ASCII_LIMIT) {
                val replacement = ESCAPE_REPLACEMENTS[charCode]
                if (replacement != null) buffer.write(replacement)
                else writeUnicodeEscape(text[index], scratchBuf)
            } else {
                val char = text[index]
                if (char.isHighSurrogate() && index + 1 < length && text[index + 1].isLowSurrogate()) {
                    buffer.writeUtf8(text, index, index + SURROGATE_PAIR_LENGTH)
                    index++
                } else {
                    buffer.writeUtf8(text, index, index + 1)
                }
            }
            index++

            if (index < length) writeEscaped(text, index)

            buffer.writeByte(QUOTE_INT)
            return
        }

        scratchBuf[scratchPos++] = QUOTE_BYTE
        buffer.write(scratchBuf, 0, scratchPos)
    }

    private fun checkDepth() {
        if (depth >= MAX_DEPTH) throwDepthError()
    }

    private fun throwDepthError() {
        throw GhostJsonException("$ERR_DEPTH_EXCEEDED (${MAX_DEPTH})", 0, 0)
    }

    private fun writeUnicodeEscape(char: Char, scratchBuf: ByteArray) {
        val codePoint = char.code
        val hexChars = HEX_CHARS

        scratchBuf[0] = BACKSLASH
        scratchBuf[1] = UNICODE_PREFIX_U
        scratchBuf[2] = hexChars[(codePoint shr SHIFT_12) and HEX_MASK]
        scratchBuf[3] = hexChars[(codePoint shr SHIFT_8) and HEX_MASK]
        scratchBuf[4] = hexChars[(codePoint shr SHIFT_4) and HEX_MASK]
        scratchBuf[5] = hexChars[codePoint and HEX_MASK]

        buffer.write(scratchBuf, 0, UNICODE_ESCAPE_LENGTH)
    }
}

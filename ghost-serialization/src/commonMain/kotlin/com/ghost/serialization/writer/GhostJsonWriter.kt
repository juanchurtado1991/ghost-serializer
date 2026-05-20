@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.writer

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.GhostJsonConstants.ASCII_LIMIT
import com.ghost.serialization.parser.GhostJsonConstants.BACKSLASH
import com.ghost.serialization.parser.GhostJsonConstants.BITMASK_INDEX_MASK
import com.ghost.serialization.parser.GhostJsonConstants.BITMASK_SHIFT
import com.ghost.serialization.parser.GhostJsonConstants.BITMASK_UNIT
import com.ghost.serialization.parser.GhostJsonConstants.CLOSE_ARR_INT
import com.ghost.serialization.parser.GhostJsonConstants.CLOSE_OBJ_INT
import com.ghost.serialization.parser.GhostJsonConstants.COLON_QUOTE_BS
import com.ghost.serialization.parser.GhostJsonConstants.COMMA_INT
import com.ghost.serialization.parser.GhostJsonConstants.DOT_ZERO
import com.ghost.serialization.parser.GhostJsonConstants.DOUBLE_DIGIT_LUT
import com.ghost.serialization.parser.GhostJsonConstants.EMPTY_STRING_BS
import com.ghost.serialization.parser.GhostJsonConstants.ERR_DEPTH_EXCEEDED
import com.ghost.serialization.parser.GhostJsonConstants.ERR_NON_FINITE
import com.ghost.serialization.parser.GhostJsonConstants.ESCAPE_MASKS
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
import com.ghost.serialization.parser.GhostJsonConstants.NULL_BS
import com.ghost.serialization.parser.GhostJsonConstants.ONE_INT
import com.ghost.serialization.parser.GhostJsonConstants.OPEN_ARR_INT
import com.ghost.serialization.parser.GhostJsonConstants.OPEN_OBJ_INT
import com.ghost.serialization.parser.GhostJsonConstants.QUOTE_BYTE
import com.ghost.serialization.parser.GhostJsonConstants.QUOTE_INT
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_12
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_4
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_8
import com.ghost.serialization.parser.GhostJsonConstants.TEN_LONG
import com.ghost.serialization.parser.GhostJsonConstants.TRUE_BS
import com.ghost.serialization.parser.GhostJsonConstants.TWO_INT
import com.ghost.serialization.parser.GhostJsonConstants.UNICODE_PREFIX_U
import com.ghost.serialization.parser.GhostJsonConstants.WHOLE_NUMBER_CHECK
import com.ghost.serialization.parser.GhostJsonConstants.WRITER_SCRATCH_SIZE
import com.ghost.serialization.parser.GhostJsonConstants.ZERO_DOUBLE
import com.ghost.serialization.parser.GhostJsonConstants.ZERO_INT
import com.ghost.serialization.releaseScratchBuffer
import okio.BufferedSink
import okio.ByteString

/**
 * A highly optimized, low-allocation JSON writer for Kotlin Multiplatform.
 *
 * This writer is designed for maximum throughput by:
 * - Writing directly to [okio.Buffer] segments to avoid intermediate copies.
 * - Reusing a [scratch] buffer for numeric formatting and string escaping.
 * - Using "Fast Paths" for common scenarios (e.g., small strings, whole numbers).
 * - Implementing "Quote Fusion" to batch opening/closing quotes with content.
 * - Minimizing virtual dispatch and branching in the hot path.
 */
class GhostJsonWriter(
    internal val sink: BufferedSink
) {

    @PublishedApi
    internal val buffer = sink.buffer

    internal var needsComma = false

    private var depth = 0

    internal var scratch: ByteArray? = null

    internal fun acquireScratch(): ByteArray = scratch
        ?: acquireScratchBuffer(WRITER_SCRATCH_SIZE)
            .also { scratch = it }

    /**
     * Releases the internal scratch buffer back to the pool.
     * Must be called at the end of the root serialization process.
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
     * Resets writer state for reuse from a pool.
     * Does NOT release the scratch buffer — it is kept warm for the next call.
     */
    @InternalGhostApi
    fun reset() {
        needsComma = false
        depth = 0
    }

    /**
     * Ensures all buffered bytes are pushed to the underlying [BufferedSink].
     *
     * Because this writer no longer emits complete segments on every
     * [endObject]/[endArray] (a deliberate optimization for batched encodes), a
     * final flush is required so callers receive every byte we produced.
     * Internally this is a single [BufferedSink.emit] which moves any partial
     * segment to the underlying [okio.Sink] without touching the OS-level flush
     * unless the underlying sink chooses to.
     */
    @InternalGhostApi
    fun flush() {
        sink.emit()
    }

    // ── Structural ────────────────────────────────────────────────────────────

    /**
     * Starts a new JSON object.
     * Automatically handles comma insertion and indentation tracking.
     */
    fun beginObject(): GhostJsonWriter {
        if (depth >= MAX_DEPTH) throwDepthError()
        appendSeparator()
        buffer.writeByte(OPEN_OBJ_INT)
        needsComma = false
        depth++
        return this
    }

    /**
     * Ends the current JSON object.
     *
     * Note: complete segments are NOT emitted here. The okio [BufferedSink]
     * will naturally flush a full 8 KiB segment to its underlying sink the next
     * time a write does not fit, and a final [flush] at the end of the encode
     * drains any remaining bytes. Avoiding a per-object `emitCompleteSegments()`
     * call removes a virtual dispatch + segment scan per object, which is a
     * significant cost when serializing large lists.
     */
    fun endObject(): GhostJsonWriter {
        buffer.writeByte(CLOSE_OBJ_INT)
        needsComma = true
        depth--
        return this
    }

    fun beginArray(): GhostJsonWriter {
        if (depth >= MAX_DEPTH) throwDepthError()
        appendSeparator()
        buffer.writeByte(OPEN_ARR_INT)
        needsComma = false
        depth++
        return this
    }

    fun endArray(): GhostJsonWriter {
        buffer.writeByte(CLOSE_ARR_INT)
        needsComma = true
        depth--
        return this
    }

    /**
     * Writes a field name as a string.
     * Escapes the key and appends the colon separator.
     */
    fun name(key: String): GhostJsonWriter {
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
    fun name(key: ByteString): GhostJsonWriter {
        appendSeparator()
        buffer.write(key)
        needsComma = false
        return this
    }

    @InternalGhostApi
    fun writeNameRaw(header: ByteString): GhostJsonWriter = name(header)


    /**
     * Fused name + value with automatic comma handling.
     * Used by KSP-generated serializers for subsequent object fields.
     */
    @InternalGhostApi
    fun writeField(header: ByteString, value: Int): GhostJsonWriter {
        appendSeparator()
        buffer.write(header)
        writeIntValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: ByteString, value: Long): GhostJsonWriter {
        appendSeparator()
        buffer.write(header)
        writeLongValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: ByteString, value: String): GhostJsonWriter {
        appendSeparator()
        buffer.write(header)
        writeStringValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: ByteString, value: Boolean): GhostJsonWriter {
        appendSeparator()
        buffer.write(header)
        writeBooleanValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: ByteString, value: Double): GhostJsonWriter {
        appendSeparator()
        buffer.write(header)
        writeDoubleValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: ByteString, value: Float): GhostJsonWriter =
        writeField(header, value.toDouble())


    // ── value() public API ────────────────────────────────────────────────────

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
        buffer.write(if (value) TRUE_BS else FALSE_BS)
        needsComma = true
        return this
    }

    fun nullValue(): GhostJsonWriter {
        appendSeparator()
        buffer.write(NULL_BS)
        needsComma = true
        return this
    }

    /**
     * Writes a boolean value without a field name or separator.
     * Used by KSP-generated serializers for array elements or raw values.
     */
    @InternalGhostApi
    fun writeBooleanValueRaw(value: Boolean) {
        buffer.write(if (value) TRUE_BS else FALSE_BS)
    }

    /**
     * Writes an integer value without a field name or separator.
     * Optimized with a lookup table and fast-paths for common small integers.
     * Used by KSP-generated serializers for array elements or raw values.
     */
    @InternalGhostApi
    @Suppress("CascadeIf")
    fun writeIntValueRaw(value: Int) {
        if (value == 0) {
            buffer.writeByte(ZERO_INT)
        } else if (value == 1) {
            buffer.writeByte(ONE_INT)
        } else if (value == 2) {
            buffer.writeByte(TWO_INT)
        } else if (value == -1) {
            buffer.write(MINUS_ONE_BS)
        } else if (value == Int.MIN_VALUE) {
            buffer.write(MIN_INT_BS)
        } else {
            writeLongValueRawInternal(value.toLong())
        }
    }

    /**
     * Writes a long value without a field name or separator.
     * Uses a fast `when` dispatch for common values.
     * Used by KSP-generated serializers for array elements or raw values.
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
     * Final output is a single System.arraycopy into Okio segments.
     */
    private fun writeLongValueRawInternal(value: Long) {
        val scratchBuf = acquireScratch()
        writeLongValueRawInternalImpl(
            value = value,
            scratchBuf = scratchBuf,
            writeBytes = { bytes, offset, length -> buffer.write(bytes, offset, length) },
            writeByteString = { byteString -> buffer.write(byteString) }
        )
    }

    /**
     * Writes a double value without a field name or separator.
     * Optimized for whole numbers.
     * Used by KSP-generated serializers for array elements or raw values.
     */
    @InternalGhostApi
    fun writeDoubleValueRaw(number: Double) {
        val scratchBuf = acquireScratch()
        writeDoubleValueRawImpl(
            number = number,
            scratchBuf = scratchBuf,
            writeLongValueRawInternal = { writeLongValueRawInternal(it) },
            writeBytes = { bytes, offset, length -> buffer.write(bytes, offset, length) },
            writeUtf8 = { str -> buffer.writeUtf8(str) }
        )
    }

    /**
     * Writes the null literal.
     * Used by KSP-generated serializers for nullable properties.
     */
    @Suppress("unused")
    @InternalGhostApi
    fun writeNullValueRaw() {
        buffer.write(NULL_BS)
    }

    @Suppress("NOTHING_TO_INLINE")
    internal inline fun appendSeparator() {
        if (needsComma) {
            buffer.writeByte(COMMA_INT)
            needsComma = false
        }
    }


    /**
     * Writes a string value with quotes and proper escaping.
     * Uses "Quote Fusion" to batch quotes with content in a single buffer write.
     * Used by KSP-generated serializers for array elements or raw values.
     */
    @InternalGhostApi
    fun writeStringValueRaw(value: String) {
        val length = value.length
        if (length == 0) {
            buffer.write(EMPTY_STRING_BS)
            return
        }

        val scratchBuf = acquireScratch()
        if (length + 2 > scratchBuf.size) {
            buffer.writeByte(QUOTE_INT)
            writeEscaped(value)
            buffer.writeByte(QUOTE_INT)
            return
        }

        scratchBuf[0] = QUOTE_BYTE
        writeEscapedIntoScratch(value, length, scratchBuf)
    }


    /**
     * Core string escaping logic.
     * Uses a single-pass scan with scratch accumulation to minimize buffer interactions.
     * Optimized with Path Splitting (Fast path for strings <= 512 chars).
     */
    private fun writeEscaped(text: String, start: Int = 0) {
        val scratchBuf = acquireScratch()
        writeEscapedImpl(
            text = text,
            start = start,
            scratchBuf = scratchBuf,
            writeBytes = { bytes, offset, len -> buffer.write(bytes, offset, len) },
            writeReplacementBytes = { bytes -> buffer.write(bytes) },
            writeUtf8 = { str, begin, end -> buffer.writeUtf8(str, begin, end) },
            writeUnicodeEscape = { code -> writeUnicodeEscape(code, scratchBuf) }
        )
    }

    /**
     * Optimized escaping that fuses opening/closing quotes into a single scratch buffer flush.
     * If an escape character is found, it falls back to standard [writeEscaped].
     */
    private fun writeEscapedIntoScratch(text: String, length: Int, scratchBuf: ByteArray) {
        writeEscapedIntoScratchImpl(
            text = text,
            length = length,
            scratchBuf = scratchBuf,
            writeBytes = { bytes, offset, len -> buffer.write(bytes, offset, len) },
            writeByte = { b -> buffer.writeByte(b) },
            writeReplacementBytes = { bytes -> buffer.write(bytes) },
            writeUtf8 = { str, begin, end -> buffer.writeUtf8(str, begin, end) },
            writeUnicodeEscape = { code -> writeUnicodeEscape(code, scratchBuf) }
        )
    }

    private fun throwDepthError() {
        throw GhostJsonException(
            "$ERR_DEPTH_EXCEEDED (${MAX_DEPTH})",
            0,
            0
        )
    }

    private fun writeUnicodeEscape(code: Int, scratchBuf: ByteArray) {
        writeUnicodeEscapeImpl(
            code = code,
            scratchBuf = scratchBuf,
            writeBytes = { bytes, offset, length -> buffer.write(bytes, offset, length) }
        )
    }
}

@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.writer

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.GhostJsonConstants
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

    internal var scratch: ByteArray? = null

    internal fun acquireScratch(): ByteArray = scratch
        ?: acquireScratchBuffer(WRITER_SCRATCH_SIZE)
            .also { scratch = it }

    /**
     * Returns the scratch buffer to its pool. Must be called once at the end
     * of the root encode so subsequent encodes (potentially on other threads)
     * can reuse the buffer.
     */
    @Suppress("unused")
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
    fun flush() {
        /* No Ops */
    }

    // ── Structural ────────────────────────────────────────────────────────────

    /**
     * Starts a new JSON object.
     * Automatically handles comma insertion and depth tracking.
     */
    fun beginObject(): GhostJsonFlatWriter {
        beginObjectImpl(
            getDepth = { depth },
            setDepth = { depth = it },
            maxDepth = MAX_DEPTH,
            appendSeparator = { appendSeparator() },
            writeByte = { buffer.writeByte(it) },
            setNeedsComma = { needsComma = it },
            throwDepthError = { throwDepthError() }
        )
        return this
    }

    /** Ends the current JSON object. */
    fun endObject(): GhostJsonFlatWriter {
        endObjectImpl(
            getDepth = { depth },
            setDepth = { depth = it },
            writeByte = { buffer.writeByte(it) },
            setNeedsComma = { needsComma = it }
        )
        return this
    }

    fun beginArray(): GhostJsonFlatWriter {
        beginArrayImpl(
            getDepth = { depth },
            setDepth = { depth = it },
            maxDepth = MAX_DEPTH,
            appendSeparator = { appendSeparator() },
            writeByte = { buffer.writeByte(it) },
            setNeedsComma = { needsComma = it },
            throwDepthError = { throwDepthError() }
        )
        return this
    }

    fun endArray(): GhostJsonFlatWriter {
        endArrayImpl(
            getDepth = { depth },
            setDepth = { depth = it },
            writeByte = { buffer.writeByte(it) },
            setNeedsComma = { needsComma = it }
        )
        return this
    }

    /**
     * Writes a field name as a string.
     * Escapes the key and appends the colon separator.
     */
    fun name(key: String): GhostJsonFlatWriter {
        nameStringImpl(
            appendSeparator = { appendSeparator() },
            writeByte = { buffer.writeByte(it) },
            writeEscaped = { writeEscaped(it) },
            writeBytes = { buffer.write(it) },
            setNeedsComma = { needsComma = it },
            key = key
        )
        return this
    }

    /**
     * Writes a pre-encoded field name [ByteString].
     * This is the fastest way to write field names as it avoids runtime escaping.
     */
    fun name(key: ByteString): GhostJsonFlatWriter {
        nameByteStringImpl(
            appendSeparator = { appendSeparator() },
            writeBytes = { buffer.write(it) },
            setNeedsComma = { needsComma = it },
            key = key
        )
        return this
    }

    @InternalGhostApi
    fun writeNameRaw(header: ByteString): GhostJsonFlatWriter = name(header)

    /**
     * Fused name + value with automatic comma handling.
     * Used by KSP-generated serializers for subsequent object fields.
     */
    @InternalGhostApi
    fun writeField(header: ByteString, value: Int): GhostJsonFlatWriter {
        writeFieldIntImpl(
            appendSeparator = { appendSeparator() },
            writeBytes = { buffer.write(it) },
            writeIntValueRaw = { writeIntValueRaw(it) },
            setNeedsComma = { needsComma = it },
            header = header,
            value = value
        )
        return this
    }

    @InternalGhostApi
    fun writeField(header: ByteString, value: Long): GhostJsonFlatWriter {
        writeFieldLongImpl(
            appendSeparator = { appendSeparator() },
            writeBytes = { buffer.write(it) },
            writeLongValueRaw = { writeLongValueRaw(it) },
            setNeedsComma = { needsComma = it },
            header = header,
            value = value
        )
        return this
    }

    @InternalGhostApi
    fun writeField(header: ByteString, value: String): GhostJsonFlatWriter {
        writeFieldStringImpl(
            appendSeparator = { appendSeparator() },
            writeBytes = { buffer.write(it) },
            writeStringValueRaw = { writeStringValueRaw(it) },
            setNeedsComma = { needsComma = it },
            header = header,
            value = value
        )
        return this
    }

    @InternalGhostApi
    fun writeField(header: ByteString, value: Boolean): GhostJsonFlatWriter {
        writeFieldBooleanImpl(
            appendSeparator = { appendSeparator() },
            writeBytes = { buffer.write(it) },
            writeBooleanValueRaw = { writeBooleanValueRaw(it) },
            setNeedsComma = { needsComma = it },
            header = header,
            value = value
        )
        return this
    }

    @InternalGhostApi
    fun writeField(header: ByteString, value: Double): GhostJsonFlatWriter {
        writeFieldDoubleImpl(
            appendSeparator = { appendSeparator() },
            writeBytes = { buffer.write(it) },
            writeDoubleValueRaw = { writeDoubleValueRaw(it) },
            setNeedsComma = { needsComma = it },
            header = header,
            value = value
        )
        return this
    }

    @InternalGhostApi
    fun writeField(header: ByteString, value: Float): GhostJsonFlatWriter =
        writeField(header, value.toDouble())


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
     * Final output is a single bulk array copy into [buffer].
     */
    private fun writeLongValueRawInternal(value: Long) {
        val scratchBuf = scratch ?: acquireScratch()
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

    @PublishedApi
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

    private fun checkDepth() {
        if (depth >= MAX_DEPTH) throwDepthError()
    }

    private fun throwDepthError() {
        throw GhostJsonException("$ERR_DEPTH_EXCEEDED (${MAX_DEPTH})", 0, 0)
    }

    private fun writeUnicodeEscape(code: Int, scratchBuf: ByteArray) {
        writeUnicodeEscapeImpl(
            code = code,
            scratchBuf = scratchBuf,
            writeBytes = { bytes, offset, length -> buffer.write(bytes, offset, length) }
        )
    }
}

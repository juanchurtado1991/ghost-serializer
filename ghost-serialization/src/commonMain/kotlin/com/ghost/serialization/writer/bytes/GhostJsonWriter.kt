@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.writer.bytes

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.types.RawJson
import com.ghost.serialization.parser.common.GhostJsonConstants.COLON_QUOTE_BS
import com.ghost.serialization.parser.common.GhostJsonConstants.COMMA_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.ERR_DEPTH_EXCEEDED
import com.ghost.serialization.parser.common.GhostJsonConstants.ERR_NON_FINITE
import com.ghost.serialization.parser.common.GhostJsonConstants.MAX_DEPTH
import com.ghost.serialization.parser.common.GhostJsonConstants.MIN_INT_BS
import com.ghost.serialization.parser.common.GhostJsonConstants.MIN_LONG_BS
import com.ghost.serialization.parser.common.GhostJsonConstants.PLAIN_ASCII_FAST_PATH_LIMIT
import com.ghost.serialization.parser.common.GhostJsonConstants.QUOTE_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.WRITER_SCRATCH_SIZE
import com.ghost.serialization.releaseScratchBuffer
import com.ghost.serialization.writer.common.GhostJsonEscapeHelpers
import okio.BufferedSink
import okio.ByteString

/**
 * A highly optimized, low-allocation JSON writer for Kotlin Multiplatform.
 *
 * Backed by either a streaming Okio [BufferedSink] or, for in-memory encodes,
 * a [FlatByteArrayWriter] — both funnel through [GhostByteSink], so the
 * body of this class is written once and shared by both channels.
 */
class GhostJsonWriter private constructor(
    @PublishedApi internal val sink: GhostByteSink
) {

    /** Streaming constructor — writes flow through an Okio [BufferedSink]. */
    constructor(sink: BufferedSink) : this(BufferGhostByteSink(sink))

    /** In-memory constructor — writes accumulate in a [FlatByteArrayWriter]. */
    @InternalGhostApi
    constructor(flatBuffer: FlatByteArrayWriter) : this(flatBuffer as GhostByteSink)

    internal var needsComma = false

    private var depth = 0

    internal var scratch: ByteArray? = null

    internal fun acquireScratch(): ByteArray {
        val currentScratch = scratch
        if (currentScratch != null) {
            return currentScratch
        }
        val newScratch = acquireScratchBuffer(WRITER_SCRATCH_SIZE)
        scratch = newScratch
        return newScratch
    }

    /**
     * Releases the internal scratch buffer back to the pool.
     * Must be called at the end of the root serialization process.
     */
    @InternalGhostApi
    fun release() {
        val currentScratch = scratch
        if (currentScratch != null) {
            releaseScratchBuffer(currentScratch)
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
     * Ensures all buffered bytes are pushed to the underlying sink.
     * No-op for the in-memory (flat) channel.
     */
    @InternalGhostApi
    fun flush() {
        sink.flush()
    }

    // ── Structural ────────────────────────────────────────────────────────────

    /**
     * Starts a new JSON object.
     * Automatically handles comma insertion and indentation tracking.
     */
    fun beginObject(): GhostJsonWriter {
        GhostJsonWriterHelpers.beginObjectCore(
            depth = depth,
            maxDepth = MAX_DEPTH,
            appendSeparator = { appendSeparator() },
            writeByte = { sink.writeByte(it) },
            setDepth = { depth = it },
            throwDepthError = { throwDepthError() },
        )
        needsComma = false
        return this
    }

    /**
     * Ends the current JSON object.
     */
    fun endObject(): GhostJsonWriter {
        GhostJsonWriterHelpers.endObjectCore(
            depth = depth,
            writeByte = { sink.writeByte(it) },
            setDepth = { depth = it },
        )
        needsComma = true
        return this
    }

    /**
     * Starts a new JSON array.
     */
    fun beginArray(): GhostJsonWriter {
        GhostJsonWriterHelpers.beginArrayCore(
            depth = depth,
            maxDepth = MAX_DEPTH,
            appendSeparator = { appendSeparator() },
            writeByte = { sink.writeByte(it) },
            setDepth = { depth = it },
            throwDepthError = { throwDepthError() },
        )
        needsComma = false
        return this
    }

    /**
     * Ends the current JSON array.
     */
    fun endArray(): GhostJsonWriter {
        GhostJsonWriterHelpers.endArrayCore(
            depth = depth,
            writeByte = { sink.writeByte(it) },
            setDepth = { depth = it },
        )
        needsComma = true
        return this
    }

    /**
     * Writes a field name as a string.
     * Escapes the key and appends the colon separator.
     */
    fun name(key: String): GhostJsonWriter {
        appendSeparator()
        sink.writeByte(QUOTE_INT)
        writeEscaped(key)
        sink.write(COLON_QUOTE_BS)
        needsComma = false
        return this
    }

    /**
     * Writes a pre-encoded field name [ByteString].
     * This is the fastest way to write field names as it avoids runtime escaping.
     */
    fun name(key: ByteString): GhostJsonWriter {
        appendSeparator()
        sink.write(key)
        needsComma = false
        return this
    }

    /**
     * Writes a field name raw [ByteString] without validating or escaping.
     */
    @InternalGhostApi
    fun writeNameRaw(header: ByteString): GhostJsonWriter {
        return name(header)
    }

    /**
     * Fused name + value with automatic comma handling.
     * Used by KSP-generated serializers for subsequent object fields.
     */
    @InternalGhostApi
    fun writeField(header: ByteString, value: Int): GhostJsonWriter {
        appendSeparator()
        sink.write(header)
        writeIntValueRaw(value)
        needsComma = true
        return this
    }

    /**
     * Fused name + value with automatic comma handling.
     * Used by KSP-generated serializers for subsequent object fields.
     */
    @InternalGhostApi
    fun writeField(header: ByteString, value: Long): GhostJsonWriter {
        appendSeparator()
        sink.write(header)
        writeLongValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: ByteString, value: ULong): GhostJsonWriter {
        appendSeparator()
        sink.write(header)
        writeULongValueRaw(value)
        needsComma = true
        return this
    }

    /**
     * Fused name + value with automatic comma handling.
     * Used by KSP-generated serializers for subsequent object fields.
     */
    @InternalGhostApi
    fun writeField(header: ByteString, value: String): GhostJsonWriter {
        appendSeparator()
        sink.write(header)
        writeStringValueRaw(value)
        needsComma = true
        return this
    }

    /**
     * Fused name + value with automatic comma handling.
     * Used by KSP-generated serializers for subsequent object fields.
     */
    @InternalGhostApi
    fun writeField(header: ByteString, value: Boolean): GhostJsonWriter {
        appendSeparator()
        sink.write(header)
        writeBooleanValueRaw(value)
        needsComma = true
        return this
    }

    /**
     * Fused name + value with automatic comma handling.
     * Used by KSP-generated serializers for subsequent object fields.
     */
    @InternalGhostApi
    fun writeField(header: ByteString, value: Double): GhostJsonWriter {
        appendSeparator()
        sink.write(header)
        writeDoubleValueRaw(value)
        needsComma = true
        return this
    }

    /**
     * Fused name + value with automatic comma handling.
     * Used by KSP-generated serializers for subsequent object fields.
     */
    @InternalGhostApi
    fun writeField(header: ByteString, value: Float): GhostJsonWriter {
        appendSeparator()
        sink.write(header)
        writeFloatValueRaw(value)
        needsComma = true
        return this
    }

    // ── value() public API ────────────────────────────────────────────────────

    /**
     * Writes a string value into the JSON stream.
     */
    fun value(text: String): GhostJsonWriter {
        appendSeparator()
        writeStringValueRaw(text)
        needsComma = true
        return this
    }

    /**
     * Writes an integer value into the JSON stream.
     */
    fun value(number: Int): GhostJsonWriter {
        appendSeparator()
        writeIntValueRaw(number)
        needsComma = true
        return this
    }

    /**
     * Writes a long value into the JSON stream.
     */
    fun value(number: Long): GhostJsonWriter {
        appendSeparator()
        writeLongValueRaw(number)
        needsComma = true
        return this
    }

    fun value(number: ULong): GhostJsonWriter {
        appendSeparator()
        writeULongValueRaw(number)
        needsComma = true
        return this
    }

    /**
     * Writes a double value into the JSON stream.
     */
    fun value(number: Double): GhostJsonWriter {
        appendSeparator()
        writeDoubleValueRaw(number)
        needsComma = true
        return this
    }

    /**
     * Writes a float value into the JSON stream.
     */
    fun value(number: Float): GhostJsonWriter {
        appendSeparator()
        writeFloatValueRaw(number)
        needsComma = true
        return this
    }

    /**
     * Writes a boolean value into the JSON stream.
     */
    fun value(value: Boolean): GhostJsonWriter {
        appendSeparator()
        if (value) {
            sink.writeTrue()
        } else {
            sink.writeFalse()
        }
        needsComma = true
        return this
    }

    /**
     * Writes a single [Char] as a JSON string without allocating an intermediate [String].
     */
    fun value(char: Char): GhostJsonWriter {
        appendSeparator()
        sink.writeQuotedBmpCodeUnit(char.code)
        needsComma = true
        return this
    }

    /**
     * Writes a null value into the JSON stream.
     */
    fun nullValue(): GhostJsonWriter {
        appendSeparator()
        sink.writeNull()
        needsComma = true
        return this
    }

    /**
     * Writes raw JSON bytes directly into the stream without quoting or escaping.
     * Use this to emit a pre-serialized JSON fragment captured via
     * `captureRawJsonBytes`.
     */
    fun rawValue(bytes: ByteArray): GhostJsonWriter {
        appendSeparator()
        sink.write(bytes)
        needsComma = true
        return this
    }

    /**
     * Writes a slice of raw JSON bytes directly into the stream without quoting or escaping.
     */
    fun rawValue(bytes: ByteArray, offset: Int, length: Int): GhostJsonWriter {
        appendSeparator()
        sink.write(bytes, offset, length)
        needsComma = true
        return this
    }

    /** Writes [raw] without copying slice data when possible. */
    fun rawValue(raw: RawJson): GhostJsonWriter =
        rawValue(raw.storage, raw.storageOffset, raw.storageLength)

    /**
     * Writes a boolean value without a field name or separator.
     */
    @InternalGhostApi
    fun writeBooleanValueRaw(value: Boolean) {
        if (value) {
            sink.writeTrue()
        } else {
            sink.writeFalse()
        }
    }

    /**
     * Writes an integer value without a field name or separator.
     */
    @InternalGhostApi
    fun writeIntValueRaw(value: Int) {
        GhostJsonWriterHelpers.writeIntValueRawCore(
            value = value,
            writeByte = { sink.writeByte(it) },
            write2Bytes = { a, b -> sink.write2Bytes(a, b) },
            writeMinIntBs = { sink.write(MIN_INT_BS) },
            writeLongValueRawInternal = { writeLongValueRawInternal(it) },
        )
    }

    /**
     * Writes a long value without a field name or separator.
     */
    @InternalGhostApi
    fun writeLongValueRaw(value: Long) {
        GhostJsonWriterHelpers.writeLongValueRawCore(
            value = value,
            writeByte = { sink.writeByte(it) },
            write2Bytes = { a, b -> sink.write2Bytes(a, b) },
            writeMinIntBs = { sink.write(MIN_INT_BS) },
            writeMinLongBs = { sink.write(MIN_LONG_BS) },
            writeLongValueRawInternal = { writeLongValueRawInternal(it) },
        )
    }

    @InternalGhostApi
    fun writeULongValueRaw(value: ULong) {
        GhostJsonWriterHelpers.writeULongValueRawCore(
            value = value,
            writeLongValueRaw = { writeLongValueRaw(it) },
            writeStringValueRaw = { writeStringValueRaw(it) },
        )
    }

    /**
     * Internal implementation for writing Long values into the scratch buffer.
     */
    private fun writeLongValueRawInternal(value: Long) {
        GhostJsonWriterHelpers.writeLongValueRawInternalCore(
            value = value,
            scratch = scratch,
            acquireScratch = { acquireScratch() },
            writeMinLongBs = { sink.write(MIN_LONG_BS) },
            writeBytes = { buf, offset, length -> sink.write(buf, offset, length) },
        )
    }

    /**
     * Writes a double value without a field name or separator.
     */
    @InternalGhostApi
    fun writeDoubleValueRaw(number: Double) {
        GhostJsonWriterHelpers.writeDoubleValueRawCore(
            number = number,
            writeLongValueRawInternal = { writeLongValueRawInternal(it) },
            writeDotZero = { sink.writeDotZero() },
            acquireScratch = { acquireScratch() },
            writeBytes = { buf, offset, length -> sink.write(buf, offset, length) },
            writeUtf8 = { sink.writeUtf8(it) },
            throwNonFinite = { throw GhostJsonException(ERR_NON_FINITE, 0, 0) },
        )
    }

    @InternalGhostApi
    fun writeFloatValueRaw(number: Float) {
        GhostJsonWriterHelpers.writeFloatValueRawCore(
            number = number,
            writeLongValueRawInternal = { writeLongValueRawInternal(it) },
            writeDotZero = { sink.writeDotZero() },
            acquireScratch = { acquireScratch() },
            writeBytes = { buf, offset, length -> sink.write(buf, offset, length) },
            writeUtf8 = { sink.writeUtf8(it) },
            throwNonFinite = { throw GhostJsonException(ERR_NON_FINITE, 0, 0) },
        )
    }

    /**
     * Appends the separator comma if needsComma is true.
     */
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun appendSeparator() {
        if (needsComma) {
            sink.writeByte(COMMA_INT)
            needsComma = false
        }
    }

    /**
     * Writes a string value with quotes and proper escaping.
     */
    @InternalGhostApi
    fun writeStringValueRaw(value: String) {
        val length = value.length
        if (length == 0) {
            sink.write2Bytes(QUOTE_INT, QUOTE_INT)
            return
        }

        // Short strings: scan for the first char that needs escaping / UTF-8. All-plain →
        // writeQuotedAscii. Mixed → keep the ASCII prefix (breakIndex) so the slow path does
        // not re-scan it (same shape as GhostJsonStringWriter).
        var breakIndex = 0
        if (length <= PLAIN_ASCII_FAST_PATH_LIMIT) {
            while (breakIndex < length) {
                if (!GhostJsonEscapeHelpers.isPlainAsciiSafe(value[breakIndex].code)) {
                    break
                }
                breakIndex++
            }
            if (breakIndex == length) {
                sink.writeQuotedAscii(value, length)
                return
            }
        }

        writeStringValueRawSlow(value, length, breakIndex)
    }

    private fun writeStringValueRawSlow(value: String, length: Int, breakIndex: Int) {
        GhostJsonWriterHelpers.writeStringValueRawSlowCore(
            value = value,
            length = length,
            breakIndex = breakIndex,
            scratchBuf = acquireScratch(),
            writeQuoteByte = { sink.writeByte(QUOTE_INT) },
            writeUtf8Range = { text, begin, end -> sink.writeUtf8(text, begin, end) },
            writeEscapedIntoScratch = { text, len, buf -> writeEscapedIntoScratch(text, len, buf) },
            writeEscaped = { text, start -> writeEscaped(text, start) },
        )
    }

    /**
     * Helper to write escaped character bytes into the destination sink.
     */
    private fun writeEscaped(text: String, start: Int = 0) {
        GhostJsonEscapeHelpers.writeEscapedBytes(
            text = text,
            start = start,
            scratchBuf = acquireScratch(),
            writeBytes = { buf, offset, len -> sink.write(buf, offset, len) },
            writeReplacement = { replacement -> sink.write(replacement) },
            writeUtf8Range = { s, begin, end -> sink.writeUtf8(s, begin, end) },
        )
    }

    /**
     * Helper to write escaped character bytes directly into the scratch buffer.
     */
    private fun writeEscapedIntoScratch(text: String, length: Int, scratchBuf: ByteArray) {
        GhostJsonEscapeHelpers.writeEscapedIntoByteScratch(
            text = text,
            length = length,
            scratchBuf = scratchBuf,
            writeBytes = { buf, offset, len -> sink.write(buf, offset, len) },
            writeReplacement = { replacement -> sink.write(replacement) },
            writeUtf8Range = { s, begin, end -> sink.writeUtf8(s, begin, end) },
            writeQuoteByte = { sink.writeByte(QUOTE_INT) },
        )
    }

    /**
     * Throws an exception when max depth limits are exceeded.
     */
    private fun throwDepthError(): Nothing =
        throw GhostJsonException(
            "$ERR_DEPTH_EXCEEDED (${MAX_DEPTH})",
            0,
            0
        )
}

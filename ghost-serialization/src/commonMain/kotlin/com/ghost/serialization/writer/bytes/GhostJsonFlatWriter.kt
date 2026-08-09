@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.writer.bytes

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.types.RawJson
import com.ghost.serialization.parser.common.GhostJsonConstants.ASCII_LIMIT
import com.ghost.serialization.parser.common.GhostJsonConstants.BACKSLASH_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.CLOSE_ARR_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.CLOSE_OBJ_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.COLON_QUOTE_BS
import com.ghost.serialization.parser.common.GhostJsonConstants.COMMA_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.ERR_DEPTH_EXCEEDED
import com.ghost.serialization.parser.common.GhostJsonConstants.ERR_NON_FINITE
import com.ghost.serialization.parser.common.GhostJsonConstants.LONG_SCRATCH_SIZE
import com.ghost.serialization.parser.common.GhostJsonConstants.MAX_DEPTH
import com.ghost.serialization.parser.common.GhostJsonConstants.MAX_SAFE_INTEGER_DOUBLE
import com.ghost.serialization.parser.common.GhostJsonConstants.MINUS_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.MIN_INT_BS
import com.ghost.serialization.parser.common.GhostJsonConstants.MIN_LONG_BS
import com.ghost.serialization.parser.common.GhostJsonConstants.MIN_SAFE_INTEGER_DOUBLE
import com.ghost.serialization.parser.common.GhostJsonConstants.OPEN_ARR_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.OPEN_OBJ_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.PLAIN_ASCII_FAST_PATH_LIMIT
import com.ghost.serialization.parser.common.GhostJsonConstants.QUOTE_BYTE
import com.ghost.serialization.parser.common.GhostJsonConstants.QUOTE_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.SPACE_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.STRING_QUOTE_PAIR_BYTES
import com.ghost.serialization.parser.common.GhostJsonConstants.WHOLE_NUMBER_CHECK
import com.ghost.serialization.parser.common.GhostJsonConstants.WRITER_SCRATCH_SIZE
import com.ghost.serialization.parser.common.GhostJsonConstants.ZERO_DOUBLE
import com.ghost.serialization.parser.common.GhostJsonConstants.ZERO_INT
import com.ghost.serialization.writer.common.GhostDoubleFormatter
import com.ghost.serialization.writer.common.GhostJsonEscapeHelpers
import com.ghost.serialization.writer.common.GhostWriterLongDigits
import okio.ByteString


/**
 * In-memory specialization of [GhostJsonWriter] backed by a contiguous
 * [FlatByteArrayWriter] instead of the segmented `okio.Buffer` used by the
 * streaming path.
 */
class GhostJsonFlatWriter @InternalGhostApi constructor(
    @InternalGhostApi val buffer: FlatByteArrayWriter
) {

    @PublishedApi
    internal var needsComma: Boolean = false

    private var depth: Int = 0

    internal var scratch: ByteArray? = null

    /**
     * Acquires the temporary scratch buffer for numeric/string conversions.
     */
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
     * No-op for the flat-array path.
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
        val currentDepth = depth
        if (currentDepth >= MAX_DEPTH) {
            throwDepthError()
        }
        appendSeparator()
        buffer.writeByte(OPEN_OBJ_INT)
        needsComma = false
        depth = currentDepth + 1
        return this
    }

    /** Ends the current JSON object. */
    fun endObject(): GhostJsonFlatWriter {
        buffer.writeByte(CLOSE_OBJ_INT)
        needsComma = true
        depth--
        return this
    }

    /**
     * Starts a new JSON array.
     * Automatically handles comma insertion and depth tracking.
     */
    fun beginArray(): GhostJsonFlatWriter {
        val currentDepth = depth
        if (currentDepth >= MAX_DEPTH) {
            throwDepthError()
        }
        appendSeparator()
        buffer.writeByte(OPEN_ARR_INT)
        needsComma = false
        depth = currentDepth + 1
        return this
    }

    /**
     * Ends the current JSON array.
     */
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

    /**
     * Writes a field name raw [ByteString] without validating or escaping.
     */
    @InternalGhostApi
    fun writeNameRaw(header: ByteString): GhostJsonFlatWriter {
        return name(header)
    }

    /**
     * Fused name + value with automatic comma handling.
     * Used by KSP-generated serializers for subsequent object fields.
     */
    @InternalGhostApi
    fun writeField(header: ByteString, value: Int): GhostJsonFlatWriter {
        appendSeparator()
        buffer.write(header)
        writeIntValueRaw(value)
        needsComma = true
        return this
    }

    /**
     * Fused name + value with automatic comma handling.
     * Used by KSP-generated serializers for subsequent object fields.
     */
    @InternalGhostApi
    fun writeField(header: ByteString, value: Long): GhostJsonFlatWriter {
        appendSeparator()
        buffer.write(header)
        writeLongValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: ByteString, value: ULong): GhostJsonFlatWriter {
        appendSeparator()
        buffer.write(header)
        writeULongValueRaw(value)
        needsComma = true
        return this
    }

    /**
     * Fused name + value with automatic comma handling.
     * Used by KSP-generated serializers for subsequent object fields.
     */
    @InternalGhostApi
    fun writeField(header: ByteString, value: String): GhostJsonFlatWriter {
        appendSeparator()
        buffer.write(header)
        writeStringValueRaw(value)
        needsComma = true
        return this
    }

    /**
     * Fused name + value with automatic comma handling.
     * Used by KSP-generated serializers for subsequent object fields.
     */
    @InternalGhostApi
    fun writeField(header: ByteString, value: Boolean): GhostJsonFlatWriter {
        appendSeparator()
        buffer.write(header)
        writeBooleanValueRaw(value)
        needsComma = true
        return this
    }

    /**
     * Fused name + value with automatic comma handling.
     * Used by KSP-generated serializers for subsequent object fields.
     */
    @InternalGhostApi
    fun writeField(header: ByteString, value: Double): GhostJsonFlatWriter {
        appendSeparator()
        buffer.write(header)
        writeDoubleValueRaw(value)
        needsComma = true
        return this
    }

    /**
     * Fused name + value with automatic comma handling.
     * Used by KSP-generated serializers for subsequent object fields.
     */
    @InternalGhostApi
    fun writeField(header: ByteString, value: Float): GhostJsonFlatWriter {
        appendSeparator()
        buffer.write(header)
        writeFloatValueRaw(value)
        needsComma = true
        return this
    }

    // ── value() public API ────────────────────────────────────────────────────

    /**
     * Writes a string value into the JSON stream.
     */
    fun value(text: String): GhostJsonFlatWriter {
        appendSeparator()
        writeStringValueRaw(text)
        needsComma = true
        return this
    }

    /**
     * Writes an integer value into the JSON stream.
     */
    fun value(number: Int): GhostJsonFlatWriter {
        appendSeparator()
        writeIntValueRaw(number)
        needsComma = true
        return this
    }

    /**
     * Writes a long value into the JSON stream.
     */
    fun value(number: Long): GhostJsonFlatWriter {
        appendSeparator()
        writeLongValueRaw(number)
        needsComma = true
        return this
    }

    fun value(number: ULong): GhostJsonFlatWriter {
        appendSeparator()
        writeULongValueRaw(number)
        needsComma = true
        return this
    }

    /**
     * Writes a double value into the JSON stream.
     */
    fun value(number: Double): GhostJsonFlatWriter {
        appendSeparator()
        writeDoubleValueRaw(number)
        needsComma = true
        return this
    }

    /**
     * Writes a float value into the JSON stream.
     */
    fun value(number: Float): GhostJsonFlatWriter {
        appendSeparator()
        writeFloatValueRaw(number)
        needsComma = true
        return this
    }

    /**
     * Writes a boolean value into the JSON stream.
     */
    fun value(value: Boolean): GhostJsonFlatWriter {
        appendSeparator()
        if (value) {
            buffer.writeTrue()
        } else {
            buffer.writeFalse()
        }
        needsComma = true
        return this
    }

    /**
     * Writes a single [Char] as a JSON string without allocating an intermediate [String].
     */
    fun value(char: Char): GhostJsonFlatWriter {
        appendSeparator()
        buffer.writeQuotedBmpCodeUnit(char.code)
        needsComma = true
        return this
    }

    /**
     * Writes a null value into the JSON stream.
     */
    fun nullValue(): GhostJsonFlatWriter {
        appendSeparator()
        buffer.writeNull()
        needsComma = true
        return this
    }

    /**
     * Writes raw JSON bytes directly into the stream without quoting or escaping.
     * Use this to emit a pre-serialized JSON fragment (object, array, or primitive)
     * captured via `captureRawJsonBytes`.
     */
    fun rawValue(bytes: ByteArray): GhostJsonFlatWriter {
        appendSeparator()
        buffer.write(bytes)
        needsComma = true
        return this
    }

    /**
     * Writes a slice of raw JSON bytes without quoting or escaping.
     */
    fun rawValue(bytes: ByteArray, offset: Int, length: Int): GhostJsonFlatWriter {
        appendSeparator()
        buffer.write(bytes, offset, length)
        needsComma = true
        return this
    }

    /**
     * Writes [raw] without copying when it aliases the parse input buffer.
     */
    fun rawValue(raw: RawJson): GhostJsonFlatWriter =
        rawValue(raw.storage, raw.storageOffset, raw.storageLength)

    /**
     * Writes a boolean value without a field name or separator.
     */
    @InternalGhostApi
    fun writeBooleanValueRaw(value: Boolean) {
        if (value) {
            buffer.writeTrue()
        } else {
            buffer.writeFalse()
        }
    }

    /**
     * Writes an integer value without a field name or separator.
     */
    @InternalGhostApi
    fun writeIntValueRaw(value: Int) {
        // Fast-path: single digit positive (most common: IDs, counts, status codes)
        if (value in 0..9) {
            buffer.writeByte(ZERO_INT + value)
            return
        }
        if (value in -9..-1) {
            // Single-digit negative: two bytes, one bounds-check
            buffer.write2Bytes(MINUS_INT, ZERO_INT - value)
            return
        }
        if (value == Int.MIN_VALUE) {
            buffer.write(MIN_INT_BS)
            return
        }
        writeLongValueRawInternal(value.toLong())
    }

    /**
     * Writes a long value without a field name or separator.
     */
    @InternalGhostApi
    fun writeLongValueRaw(value: Long) {
        // Fast-path: single digit positive
        if (value in 0L..9L) {
            val intVal = value.toInt()
            buffer.writeByte(ZERO_INT + intVal)
            return
        }
        if (value in -9L..-1L) {
            // Single-digit negative: two bytes, one bounds-check
            val intVal = value.toInt()
            buffer.write2Bytes(MINUS_INT, ZERO_INT - intVal)
            return
        }
        if (value == Int.MIN_VALUE.toLong()) {
            buffer.write(MIN_INT_BS)
            return
        }
        if (value == Long.MIN_VALUE) {
            buffer.write(MIN_LONG_BS)
            return
        }
        writeLongValueRawInternal(value)
    }

    @InternalGhostApi
    fun writeULongValueRaw(value: ULong) {
        if (value <= Long.MAX_VALUE.toULong()) {
            writeLongValueRaw(value.toLong())
        } else {
            writeStringValueRaw(value.toString())
        }
    }

    /**
     * Internal implementation for writing Long values into the scratch buffer.
     */
    private fun writeLongValueRawInternal(value: Long) {
        val scratchBuf = scratch ?: acquireScratch()
        var localValue = value
        val isNegative = localValue < 0
        if (isNegative) {
            if (localValue == Long.MIN_VALUE) {
                buffer.write(MIN_LONG_BS)
                return
            }
            localValue = -localValue
        }

        val scratchEnd = LONG_SCRATCH_SIZE
        val pos = GhostWriterLongDigits.writeDigitsBytes(
            absoluteValue = localValue,
            negative = isNegative,
            scratch = scratchBuf,
            scratchEnd = scratchEnd,
        )
        buffer.write(scratchBuf, pos, scratchEnd - pos)
    }

    /**
     * Writes a double value without a field name or separator.
     */
    @InternalGhostApi
    fun writeDoubleValueRaw(number: Double) {
        if (number in MIN_SAFE_INTEGER_DOUBLE..MAX_SAFE_INTEGER_DOUBLE &&
            number % WHOLE_NUMBER_CHECK == ZERO_DOUBLE &&
            !(number == 0.0 && number.toRawBits() < 0)
        ) {
            writeLongValueRawInternal(number.toLong())
            buffer.writeDotZero()
            return
        }

        val scratchBuf = acquireScratch()
        val bytesWrittenLength = GhostDoubleFormatter.writeDoubleDirect(
            value = number,
            scratch = scratchBuf,
            offset = 0,
        )
        if (bytesWrittenLength == GhostDoubleFormatter.FALLBACK_REQUIRED) {
            if (!number.isFinite()) {
                throw GhostJsonException(ERR_NON_FINITE, 0, 0)
            }
            buffer.writeUtf8(number.toString())
        } else if (bytesWrittenLength > 0) {
            buffer.write(scratchBuf, 0, bytesWrittenLength)
        }
    }

    @InternalGhostApi
    fun writeFloatValueRaw(number: Float) {
        val doubleVal = number.toDouble()
        if (doubleVal in MIN_SAFE_INTEGER_DOUBLE..MAX_SAFE_INTEGER_DOUBLE &&
            doubleVal % WHOLE_NUMBER_CHECK == ZERO_DOUBLE &&
            !(number == 0.0f && number.toRawBits() < 0)
        ) {
            writeLongValueRawInternal(doubleVal.toLong())
            buffer.writeDotZero()
            return
        }

        val scratchBuf = acquireScratch()
        val bytesWrittenLength = GhostDoubleFormatter.writeFloatDirect(
            value = number,
            scratch = scratchBuf,
            offset = 0,
        )
        if (bytesWrittenLength == GhostDoubleFormatter.FALLBACK_REQUIRED) {
            if (!number.isFinite()) {
                throw GhostJsonException(ERR_NON_FINITE, 0, 0)
            }
            buffer.writeUtf8(number.toString())
        } else if (bytesWrittenLength > 0) {
            buffer.write(scratchBuf, 0, bytesWrittenLength)
        }
    }

    /**
     * Appends a separator comma if a comma is needed before the next entry.
     */
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
     */
    @InternalGhostApi
    fun writeStringValueRaw(value: String) {
        val length = value.length
        if (length == 0) {
            // Two quotes, one bounds-check
            buffer.write2Bytes(QUOTE_INT, QUOTE_INT)
            return
        }

        // Short strings: scan for the first char that needs escaping / UTF-8. All-plain →
        // writeQuotedAscii. Mixed → keep the ASCII prefix (breakIndex) so the slow path does
        // not re-scan it (same shape as GhostJsonStringWriter).
        var breakIndex = 0
        if (length <= PLAIN_ASCII_FAST_PATH_LIMIT) {
            while (breakIndex < length) {
                val code = value[breakIndex].code
                if (code !in SPACE_INT..<ASCII_LIMIT || code == QUOTE_INT || code == BACKSLASH_INT) {
                    break
                }
                breakIndex++
            }
            if (breakIndex == length) {
                buffer.writeQuotedAscii(value, length)
                return
            }
        }

        writeStringValueRawSlow(value, length, breakIndex)
    }

    private fun writeStringValueRawSlow(value: String, length: Int, breakIndex: Int) {
        val scratchBuf = acquireScratch()
        if (breakIndex == 0 && length + STRING_QUOTE_PAIR_BYTES <= scratchBuf.size) {
            scratchBuf[0] = QUOTE_BYTE
            writeEscapedIntoScratch(value, length, scratchBuf)
            return
        }

        buffer.writeByte(QUOTE_INT)
        if (breakIndex > 0) {
            // Prefix already verified plain ASCII — writeUtf8 collapses to 1 byte/char.
            buffer.writeUtf8(value, 0, breakIndex)
        }
        writeEscaped(value, start = breakIndex)
        buffer.writeByte(QUOTE_INT)
    }

    /**
     * Utility method to write escaped strings when size exceeds the quick buffer.
     */
    private fun writeEscaped(text: String, start: Int = 0) {
        GhostJsonEscapeHelpers.writeEscapedBytes(
            text = text,
            start = start,
            scratchBuf = acquireScratch(),
            writeBytes = { buf, offset, len -> buffer.write(buf, offset, len) },
            writeReplacement = { replacement -> buffer.write(replacement) },
            writeUtf8Range = { s, begin, end -> buffer.writeUtf8(s, begin, end) },
        )
    }

    /**
     * Escape strings directly into the scratch buffer.
     */
    private fun writeEscapedIntoScratch(text: String, length: Int, scratchBuf: ByteArray) {
        GhostJsonEscapeHelpers.writeEscapedIntoByteScratch(
            text = text,
            length = length,
            scratchBuf = scratchBuf,
            writeBytes = { buf, offset, len -> buffer.write(buf, offset, len) },
            writeReplacement = { replacement -> buffer.write(replacement) },
            writeUtf8Range = { s, begin, end -> buffer.writeUtf8(s, begin, end) },
            writeQuoteByte = { buffer.writeByte(QUOTE_INT) },
        )
    }

    /**
     * Throws an exception when max depth limits are exceeded.
     */
    private fun throwDepthError() {
        throw GhostJsonException("$ERR_DEPTH_EXCEEDED (${MAX_DEPTH})", 0, 0)
    }
}

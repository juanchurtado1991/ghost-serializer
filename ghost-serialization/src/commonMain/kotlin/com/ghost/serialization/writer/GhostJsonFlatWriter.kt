@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.writer

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.GhostJsonConstants.ASCII_LIMIT
import com.ghost.serialization.parser.GhostJsonConstants.BACKSLASH
import com.ghost.serialization.parser.GhostJsonConstants.BACKSLASH_INT
import com.ghost.serialization.parser.GhostJsonConstants.BITMASK_INDEX_MASK
import com.ghost.serialization.parser.GhostJsonConstants.BITMASK_SHIFT
import com.ghost.serialization.parser.GhostJsonConstants.BITMASK_UNIT
import com.ghost.serialization.parser.GhostJsonConstants.CLOSE_ARR_INT
import com.ghost.serialization.parser.GhostJsonConstants.CLOSE_OBJ_INT
import com.ghost.serialization.parser.GhostJsonConstants.COLON_QUOTE_BS
import com.ghost.serialization.parser.GhostJsonConstants.COMMA_INT
import com.ghost.serialization.parser.GhostJsonConstants.DOUBLE_DIGIT_LUT
import com.ghost.serialization.parser.GhostJsonConstants.ERR_DEPTH_EXCEEDED
import com.ghost.serialization.parser.GhostJsonConstants.ERR_NON_FINITE
import com.ghost.serialization.parser.GhostJsonConstants.ESCAPE_MASKS
import com.ghost.serialization.parser.GhostJsonConstants.ESCAPE_REPLACEMENTS
import com.ghost.serialization.parser.GhostJsonConstants.HEX_CHARS
import com.ghost.serialization.parser.GhostJsonConstants.HEX_MASK
import com.ghost.serialization.parser.GhostJsonConstants.HUNDRED_LONG
import com.ghost.serialization.parser.GhostJsonConstants.LONG_SCRATCH_SIZE
import com.ghost.serialization.parser.GhostJsonConstants.MAX_DEPTH
import com.ghost.serialization.parser.GhostJsonConstants.MAX_SAFE_INTEGER_DOUBLE
import com.ghost.serialization.parser.GhostJsonConstants.MINUS
import com.ghost.serialization.parser.GhostJsonConstants.MINUS_INT
import com.ghost.serialization.parser.GhostJsonConstants.MIN_INT_BS
import com.ghost.serialization.parser.GhostJsonConstants.MIN_LONG_BS
import com.ghost.serialization.parser.GhostJsonConstants.MIN_SAFE_INTEGER_DOUBLE
import com.ghost.serialization.parser.GhostJsonConstants.OPEN_ARR_INT
import com.ghost.serialization.parser.GhostJsonConstants.OPEN_OBJ_INT
import com.ghost.serialization.parser.GhostJsonConstants.PLAIN_ASCII_FAST_PATH_LIMIT
import com.ghost.serialization.parser.GhostJsonConstants.QUOTE_BYTE
import com.ghost.serialization.parser.GhostJsonConstants.QUOTE_INT
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_12
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_4
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_8
import com.ghost.serialization.parser.GhostJsonConstants.SPACE_INT
import com.ghost.serialization.parser.GhostJsonConstants.STRING_QUOTE_PAIR_BYTES
import com.ghost.serialization.parser.GhostJsonConstants.TEN_LONG
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
 */
@Suppress("CascadeIf")
class GhostJsonFlatWriter @InternalGhostApi internal constructor(
    @PublishedApi internal val buffer: FlatByteArrayWriter
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
     * Returns the scratch buffer to its pool. Must be called once at the end
     * of the root encode so subsequent encodes (potentially on other threads)
     * can reuse the buffer.
     */
    @Suppress("unused")
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
        val d = depth
        if (d >= MAX_DEPTH) {
            throwDepthError()
        }
        appendSeparator()
        buffer.writeByte(OPEN_OBJ_INT)
        needsComma = false
        depth = d + 1
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
        val d = depth
        if (d >= MAX_DEPTH) {
            throwDepthError()
        }
        appendSeparator()
        buffer.writeByte(OPEN_ARR_INT)
        needsComma = false
        depth = d + 1
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
        return writeField(header, value.toDouble())
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
        return value(number.toDouble())
    }

    /**
     * Writes a boolean value into the JSON stream.
     */
    fun value(value: Boolean): GhostJsonFlatWriter {
        appendSeparator()
        if (value) { buffer.writeTrue() } else { buffer.writeFalse() }
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
     * Writes a boolean value without a field name or separator.
     */
    @InternalGhostApi
    fun writeBooleanValueRaw(value: Boolean) {
        if (value) { buffer.writeTrue() } else { buffer.writeFalse() }
    }

    /**
     * Writes an integer value without a field name or separator.
     */
    @InternalGhostApi
    fun writeIntValueRaw(value: Int) {
        // Fast-path: single digit positive (most common: IDs, counts, status codes)
        if (value in 0..9) {
            buffer.writeByte(ZERO_INT + value)
        } else if (value in -9..-1) {
            // Single-digit negative: two bytes, one bounds-check
            buffer.write2Bytes(MINUS_INT, ZERO_INT - value)
        } else if (value == Int.MIN_VALUE) {
            buffer.write(MIN_INT_BS)
        } else {
            writeLongValueRawInternal(value.toLong())
        }
    }

    /**
     * Writes a long value without a field name or separator.
     */
    @InternalGhostApi
    fun writeLongValueRaw(value: Long) {
        // Fast-path: single digit positive
        if (value in 0L..9L) {
            buffer.writeByte((ZERO_INT + value).toInt())
        } else if (value in -9L..-1L) {
            // Single-digit negative: two bytes, one bounds-check
            buffer.write2Bytes(MINUS_INT, (ZERO_INT - value).toInt())
        } else if (value == Int.MIN_VALUE.toLong()) {
            buffer.write(MIN_INT_BS)
        } else if (value == Long.MIN_VALUE) {
            buffer.write(MIN_LONG_BS)
        } else {
            writeLongValueRawInternal(value)
        }
    }

    /**
     * Internal implementation for writing Long values into the scratch buffer.
     */
    private fun writeLongValueRawInternal(value: Long) {
        val scratchBuf = scratch ?: acquireScratch()
        val scratchEnd = LONG_SCRATCH_SIZE
        var pos = scratchEnd
        var localValue = value
        val isNegative = localValue < 0
        if (isNegative) {
            if (localValue == Long.MIN_VALUE) {
                buffer.write(MIN_LONG_BS)
                return
            }
            localValue = -localValue
        }

        while (localValue >= HUNDRED_LONG) {
            val rem = (localValue % HUNDRED_LONG).toInt() * 2
            scratchBuf[--pos] = DOUBLE_DIGIT_LUT[rem + 1] // ones
            scratchBuf[--pos] = DOUBLE_DIGIT_LUT[rem]     // tens
            localValue /= HUNDRED_LONG
        }

        if (localValue < TEN_LONG) {
            scratchBuf[--pos] = (ZERO_INT + localValue.toInt()).toByte()
        } else {
            val rem = localValue.toInt() * 2
            scratchBuf[--pos] = DOUBLE_DIGIT_LUT[rem + 1] // ones
            scratchBuf[--pos] = DOUBLE_DIGIT_LUT[rem]     // tens
        }

        if (isNegative) {
            scratchBuf[--pos] = MINUS
        }

        buffer.write(scratchBuf, pos, scratchEnd - pos)
    }

    /**
     * Writes a double value without a field name or separator.
     */
    @InternalGhostApi
    fun writeDoubleValueRaw(number: Double) {
        if (number in MIN_SAFE_INTEGER_DOUBLE..MAX_SAFE_INTEGER_DOUBLE &&
            number % WHOLE_NUMBER_CHECK == ZERO_DOUBLE
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

    /**
     * Writes the null literal.
     */
    @Suppress("unused")
    @InternalGhostApi
    fun writeNullValueRaw() {
        buffer.writeNull()
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

        // Fast path: plain ASCII with no characters needing escaping.
        // A single scan (cheaper than the ESCAPE_MASKS bitmask lookup) to decide.
        // If all chars qualify, writeQuotedAscii writes directly into the backing
        // array with a single ensureCapacity and an unrolled loop — no scratch buffer.
        if (length <= PLAIN_ASCII_FAST_PATH_LIMIT) {
            var allPlain = true
            var i = 0
            while (i < length) {
                val c = value[i].code
                if (c < SPACE_INT || c >= ASCII_LIMIT || c == QUOTE_INT || c == BACKSLASH_INT) {
                    allPlain = false
                    break
                }
                i++
            }
            if (allPlain) {
                buffer.writeQuotedAscii(value, length)
                return
            }
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

    /**
     * Utility method to write escaped strings when size exceeds the quick buffer.
     */
    private fun writeEscaped(text: String, start: Int = 0) {
        val scratchBuf = acquireScratch()
        val length = text.length
        val remaining = length - start
        if (remaining <= 0) {
            return
        }

        val replacements = ESCAPE_REPLACEMENTS
        val scratchSize = scratchBuf.size

        if (remaining <= scratchSize) {
            var scratchPos = 0
            var index = start
            while (index < length) {
                val charCode = text[index].code

                // Unrolled fast path for plain ASCII
                if (charCode < ASCII_LIMIT) {
                    val maskIdx = charCode shr BITMASK_SHIFT
                    val bitIdx = charCode and BITMASK_INDEX_MASK
                    if ((ESCAPE_MASKS[maskIdx] shr bitIdx) and BITMASK_UNIT == 0L) {
                        scratchBuf[scratchPos++] = charCode.toByte()
                        index++
                        continue
                    }
                }

                if (scratchPos > 0) {
                    buffer.write(scratchBuf, 0, scratchPos)
                    scratchPos = 0
                }

                if (charCode < ASCII_LIMIT) {
                    val replacement = replacements[charCode]
                    if (replacement != null) {
                        buffer.write(replacement)
                    } else {
                        writeUnicodeEscape(charCode, scratchBuf)
                    }
                } else {
                    val c = text[index]
                    if (c.isHighSurrogate() && index + 1 < length && text[index + 1].isLowSurrogate()) {
                        buffer.writeUtf8(text, index, index + 2)
                        index++
                    } else {
                        buffer.writeUtf8(text, index, index + 1)
                    }
                }
                index++
            }
            if (scratchPos > 0) {
                buffer.write(scratchBuf, 0, scratchPos)
            }
            return
        }

        var scratchPos = 0
        var index = start

        while (index < length) {
            val charCode = text[index].code

            if (
                charCode < ASCII_LIMIT &&
                (ESCAPE_MASKS[charCode shr BITMASK_SHIFT] shr
                        (charCode and BITMASK_INDEX_MASK)) and BITMASK_UNIT == 0L
            ) {
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
                if (replacement != null) {
                    buffer.write(replacement)
                } else {
                    writeUnicodeEscape(charCode, scratchBuf)
                }
            } else {
                val char = text[index]
                if (char.isHighSurrogate() && index + 1 < length && text[index + 1].isLowSurrogate()) {
                    buffer.writeUtf8(text, index, index + 2)
                    index++
                } else {
                    buffer.writeUtf8(text, index, index + 1)
                }
            }
            index++
        }

        if (scratchPos > 0) {
            buffer.write(scratchBuf, 0, scratchPos)
        }
    }

    /**
     * Escape strings directly into the scratch buffer.
     */
    private fun writeEscapedIntoScratch(text: String, length: Int, scratchBuf: ByteArray) {
        var scratchPos = 1 // Start after the opening quote already written at index 0.
        var index = 0

        while (index < length) {
            val charCode = text[index].code

            if (
                charCode < ASCII_LIMIT &&
                (ESCAPE_MASKS[charCode shr BITMASK_SHIFT] shr
                        (charCode and BITMASK_INDEX_MASK)) and BITMASK_UNIT == 0L
            ) {
                scratchBuf[scratchPos++] = charCode.toByte()
                index++
                continue
            }

            // Flush what we have so far
            if (scratchPos > 0) {
                buffer.write(scratchBuf, 0, scratchPos)
                scratchPos = 0
            }

            // Handle the escape
            if (charCode < ASCII_LIMIT) {
                val replacement = ESCAPE_REPLACEMENTS[charCode]
                if (replacement != null) {
                    buffer.write(replacement)
                } else {
                    writeUnicodeEscape(charCode, scratchBuf)
                }
            } else {
                val c = text[index]
                if (c.isHighSurrogate() && index + 1 < length && text[index + 1].isLowSurrogate()) {
                    buffer.writeUtf8(text, index, index + 2)
                    index++
                } else {
                    buffer.writeUtf8(text, index, index + 1)
                }
            }
            index++
        }

        // Add the closing quote and final flush
        if (scratchPos + 1 > scratchBuf.size) {
            if (scratchPos > 0) {
                buffer.write(scratchBuf, 0, scratchPos)
            }
            buffer.writeByte(QUOTE_INT)
        } else {
            scratchBuf[scratchPos++] = QUOTE_BYTE
            buffer.write(scratchBuf, 0, scratchPos)
        }
    }

    /**
     * Throws an exception when max depth limits are exceeded.
     */
    private fun throwDepthError() {
        throw GhostJsonException("$ERR_DEPTH_EXCEEDED (${MAX_DEPTH})", 0, 0)
    }

    /**
     * Formats unicode characters to standard JSON unicode escape string values.
     */
    private fun writeUnicodeEscape(code: Int, scratchBuf: ByteArray) {
        val hexChars = HEX_CHARS

        scratchBuf[0] = BACKSLASH
        scratchBuf[1] = UNICODE_PREFIX_U
        scratchBuf[2] = hexChars[(code shr SHIFT_12) and HEX_MASK]
        scratchBuf[3] = hexChars[(code shr SHIFT_8) and HEX_MASK]
        scratchBuf[4] = hexChars[(code shr SHIFT_4) and HEX_MASK]
        scratchBuf[5] = hexChars[code and HEX_MASK]

        buffer.write(scratchBuf, 0, 6)
    }
}

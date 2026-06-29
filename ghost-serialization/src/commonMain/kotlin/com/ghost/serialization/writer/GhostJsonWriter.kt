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
import com.ghost.serialization.parser.GhostJsonConstants.MINUS_INT
import com.ghost.serialization.parser.GhostJsonConstants.MIN_INT_BS
import com.ghost.serialization.parser.GhostJsonConstants.MIN_LONG_BS
import com.ghost.serialization.parser.GhostJsonConstants.MIN_SAFE_INTEGER_DOUBLE
import com.ghost.serialization.parser.GhostJsonConstants.NULL_BS
import com.ghost.serialization.parser.GhostJsonConstants.OPEN_ARR_INT
import com.ghost.serialization.parser.GhostJsonConstants.OPEN_OBJ_INT
import com.ghost.serialization.parser.GhostJsonConstants.QUOTE_BYTE
import com.ghost.serialization.parser.GhostJsonConstants.QUOTE_INT
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_12
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_4
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_8
import com.ghost.serialization.parser.GhostJsonConstants.STRING_QUOTE_PAIR_BYTES
import com.ghost.serialization.parser.GhostJsonConstants.TEN_LONG
import com.ghost.serialization.parser.GhostJsonConstants.TRUE_BS
import com.ghost.serialization.parser.GhostJsonConstants.UNICODE_PREFIX_U
import com.ghost.serialization.parser.GhostJsonConstants.WHOLE_NUMBER_CHECK
import com.ghost.serialization.parser.GhostJsonConstants.WRITER_SCRATCH_SIZE
import com.ghost.serialization.parser.GhostJsonConstants.ZERO_DOUBLE
import com.ghost.serialization.parser.GhostJsonConstants.ZERO_INT
import com.ghost.serialization.parser.GhostJsonConstants.PLAIN_ASCII_FAST_PATH_LIMIT
import com.ghost.serialization.parser.GhostJsonConstants.SPACE_INT
import com.ghost.serialization.parser.GhostJsonConstants.BACKSLASH_INT
import com.ghost.serialization.releaseScratchBuffer
import okio.BufferedSink
import okio.ByteString

/**
 * A highly optimized, low-allocation JSON writer for Kotlin Multiplatform.
 */
class GhostJsonWriter(
    internal val sink: BufferedSink
) {

    @PublishedApi
    internal val buffer = sink.buffer

    internal var needsComma = false

    private var depth = 0

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
     * Ensures all buffered bytes are pushed to the underlying [BufferedSink].
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
        val localDepth = depth
        if (localDepth >= MAX_DEPTH) throwDepthError()
        appendSeparator()
        buffer.writeByte(OPEN_OBJ_INT)
        needsComma = false
        depth = localDepth + 1
        return this
    }

    /**
     * Ends the current JSON object.
     */
    fun endObject(): GhostJsonWriter {
        buffer.writeByte(CLOSE_OBJ_INT)
        needsComma = true
        depth--
        return this
    }

    /**
     * Starts a new JSON array.
     */
    fun beginArray(): GhostJsonWriter {
        val localDepth = depth
        if (localDepth >= MAX_DEPTH) {
            throwDepthError()
        }
        appendSeparator()
        buffer.writeByte(OPEN_ARR_INT)
        needsComma = false
        depth = localDepth + 1
        return this
    }

    /**
     * Ends the current JSON array.
     */
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
    fun writeField(header: ByteString, value: Long): GhostJsonWriter {
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
    fun writeField(header: ByteString, value: String): GhostJsonWriter {
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
    fun writeField(header: ByteString, value: Boolean): GhostJsonWriter {
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
    fun writeField(header: ByteString, value: Double): GhostJsonWriter {
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
    fun writeField(header: ByteString, value: Float): GhostJsonWriter {
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
        buffer.write(if (value) {
            TRUE_BS
        } else {
            FALSE_BS
        })
        needsComma = true
        return this
    }

    /**
     * Writes a null value into the JSON stream.
     */
    fun nullValue(): GhostJsonWriter {
        appendSeparator()
        buffer.write(NULL_BS)
        needsComma = true
        return this
    }

    /**
     * Writes raw JSON bytes directly into the stream without quoting or escaping.
     * Use this to emit a pre-serialized JSON fragment captured via
     * [com.ghost.serialization.parser.captureRawJsonBytes].
     */
    fun rawValue(bytes: ByteArray): GhostJsonWriter {
        appendSeparator()
        buffer.write(bytes)
        needsComma = true
        return this
    }

    /**
     * Writes a boolean value without a field name or separator.
     */
    @InternalGhostApi
    fun writeBooleanValueRaw(value: Boolean) {
        buffer.write(if (value) {
            TRUE_BS
        } else {
            FALSE_BS
        })
    }

    /**
     * Writes an integer value without a field name or separator.
     */
    @InternalGhostApi
    fun writeIntValueRaw(value: Int) {
        if (value in 0..9) {
            buffer.writeByte(ZERO_INT + value)
            return
        }
        if (value in -9..-1) {
            buffer.writeByte(MINUS_INT)
            buffer.writeByte(ZERO_INT - value)
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
        if (value in 0L..9L) {
            val intVal = value.toInt()
            buffer.writeByte(ZERO_INT + intVal)
            return
        }
        if (value in -9L..-1L) {
            val intVal = value.toInt()
            buffer.writeByte(MINUS_INT)
            buffer.writeByte(ZERO_INT - intVal)
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
            number % WHOLE_NUMBER_CHECK == ZERO_DOUBLE &&
            !(number == 0.0 && number.toRawBits() < 0)
        ) {
            writeLongValueRawInternal(number.toLong())
            buffer.write(DOT_ZERO, 0, DOT_ZERO.size)
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
    fun writeFloatValueRaw(number: Float) {
        val doubleVal = number.toDouble()
        if (doubleVal in MIN_SAFE_INTEGER_DOUBLE..MAX_SAFE_INTEGER_DOUBLE &&
            doubleVal % WHOLE_NUMBER_CHECK == ZERO_DOUBLE &&
            !(number == 0.0f && number.toRawBits() < 0)
        ) {
            writeLongValueRawInternal(doubleVal.toLong())
            buffer.write(DOT_ZERO, 0, DOT_ZERO.size)
            return
        }

        val scratchBuf = acquireScratch()
        val bytesWrittenLength = GhostDoubleFormatter.writeFloatDirect(
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
        buffer.write(NULL_BS)
    }

    /**
     * Appends the separator comma if needsComma is true.
     */
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
            buffer.write(EMPTY_STRING_BS)
            return
        }

        if (length <= PLAIN_ASCII_FAST_PATH_LIMIT) {
            var allPlain = true
            var index = 0
            val escapeMasks = ESCAPE_MASKS
            while (index < length) {
                val code = value[index].code
                if (code >= ASCII_LIMIT || ((escapeMasks[code shr BITMASK_SHIFT] shr (code and BITMASK_INDEX_MASK)) and BITMASK_UNIT) != 0L) {
                    allPlain = false
                    break
                }
                index++
            }
            if (allPlain) {
                buffer.writeByte(QUOTE_INT)
                buffer.writeUtf8(value)
                buffer.writeByte(QUOTE_INT)
                return
            }
        }

        writeStringValueRawSlow(value, length)
    }

    private fun writeStringValueRawSlow(value: String, length: Int) {
        val scratchBuf = acquireScratch()
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
     * Helper to write escaped character bytes into the destination sink buffer.
     */
    private fun writeEscaped(text: String, start: Int = 0) {
        val scratchBuf = acquireScratch()
        val length = text.length
        val remaining = length - start
        if (remaining <= 0) {
            return
        }

        val replacements = ESCAPE_REPLACEMENTS
        val escapeMasks = ESCAPE_MASKS
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
                    if ((escapeMasks[maskIdx] shr bitIdx) and BITMASK_UNIT == 0L) {
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
                (escapeMasks[charCode shr BITMASK_SHIFT] shr
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
     * Helper to write escaped character bytes directly into the scratch buffer.
     */
    private fun writeEscapedIntoScratch(text: String, length: Int, scratchBuf: ByteArray) {
        val escapeMasks = ESCAPE_MASKS
        val escapeReplacements = ESCAPE_REPLACEMENTS
        var scratchPos = 1 // Start after the opening quote already written at index 0.
        var index = 0

        while (index < length) {
            val charCode = text[index].code

            if (
                charCode < ASCII_LIMIT &&
                (escapeMasks[charCode shr BITMASK_SHIFT] shr
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
                val replacement = escapeReplacements[charCode]
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
        throw GhostJsonException(
            "$ERR_DEPTH_EXCEEDED (${MAX_DEPTH})",
            0,
            0
        )
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

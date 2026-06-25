@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.writer

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.GhostJsonConstants as C
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
        val newScratch = acquireScratchBuffer(C.WRITER_SCRATCH_SIZE)
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
        if (localDepth >= C.MAX_DEPTH) throwDepthError()
        appendSeparator()
        buffer.writeByte(C.OPEN_OBJ_INT)
        needsComma = false
        depth = localDepth + 1
        return this
    }

    /**
     * Ends the current JSON object.
     */
    fun endObject(): GhostJsonWriter {
        buffer.writeByte(C.CLOSE_OBJ_INT)
        needsComma = true
        depth--
        return this
    }

    /**
     * Starts a new JSON array.
     */
    fun beginArray(): GhostJsonWriter {
        val localDepth = depth
        if (localDepth >= C.MAX_DEPTH) {
            throwDepthError()
        }
        appendSeparator()
        buffer.writeByte(C.OPEN_ARR_INT)
        needsComma = false
        depth = localDepth + 1
        return this
    }

    /**
     * Ends the current JSON array.
     */
    fun endArray(): GhostJsonWriter {
        buffer.writeByte(C.CLOSE_ARR_INT)
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
        buffer.writeByte(C.QUOTE_INT)
        writeEscaped(key)
        buffer.write(C.COLON_QUOTE_BS)
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
            C.TRUE_BS
        } else {
            C.FALSE_BS
        })
        needsComma = true
        return this
    }

    /**
     * Writes a null value into the JSON stream.
     */
    fun nullValue(): GhostJsonWriter {
        appendSeparator()
        buffer.write(C.NULL_BS)
        needsComma = true
        return this
    }

    /**
     * Writes a boolean value without a field name or separator.
     */
    @InternalGhostApi
    fun writeBooleanValueRaw(value: Boolean) {
        buffer.write(if (value) {
            C.TRUE_BS
        } else {
            C.FALSE_BS
        })
    }

    /**
     * Writes an integer value without a field name or separator.
     */
    @InternalGhostApi
    fun writeIntValueRaw(value: Int) {
        if (value in 0..9) {
            buffer.writeByte(C.ZERO_INT + value)
            return
        }
        if (value in -9..-1) {
            buffer.writeByte(C.MINUS_INT)
            buffer.writeByte(C.ZERO_INT - value)
            return
        }
        if (value == Int.MIN_VALUE) {
            buffer.write(C.MIN_INT_BS)
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
            buffer.writeByte(C.ZERO_INT + intVal)
            return
        }
        if (value in -9L..-1L) {
            val intVal = value.toInt()
            buffer.writeByte(C.MINUS_INT)
            buffer.writeByte(C.ZERO_INT - intVal)
            return
        }
        if (value == Int.MIN_VALUE.toLong()) {
            buffer.write(C.MIN_INT_BS)
            return
        }
        if (value == Long.MIN_VALUE) {
            buffer.write(C.MIN_LONG_BS)
            return
        }
        writeLongValueRawInternal(value)
    }

    /**
     * Internal implementation for writing Long values into the scratch buffer.
     */
    private fun writeLongValueRawInternal(value: Long) {
        val scratchBuf = scratch ?: acquireScratch()
        val scratchEnd = C.LONG_SCRATCH_SIZE
        var pos = scratchEnd
        var localValue = value
        val isNegative = localValue < 0
        if (isNegative) {
            if (localValue == Long.MIN_VALUE) {
                buffer.write(C.MIN_LONG_BS)
                return
            }
            localValue = -localValue
        }

        while (localValue >= C.HUNDRED_LONG) {
            val rem = (localValue % C.HUNDRED_LONG).toInt() * 2
            scratchBuf[--pos] = C.DOUBLE_DIGIT_LUT[rem + 1] // ones
            scratchBuf[--pos] = C.DOUBLE_DIGIT_LUT[rem]     // tens
            localValue /= C.HUNDRED_LONG
        }

        if (localValue < C.TEN_LONG) {
            scratchBuf[--pos] = (C.ZERO_INT + localValue.toInt()).toByte()
        } else {
            val rem = localValue.toInt() * 2
            scratchBuf[--pos] = C.DOUBLE_DIGIT_LUT[rem + 1] // ones
            scratchBuf[--pos] = C.DOUBLE_DIGIT_LUT[rem]     // tens
        }

        if (isNegative) {
            scratchBuf[--pos] = C.MINUS
        }

        buffer.write(scratchBuf, pos, scratchEnd - pos)
    }

    /**
     * Writes a double value without a field name or separator.
     */
    @InternalGhostApi
    fun writeDoubleValueRaw(number: Double) {
        if (number in C.MIN_SAFE_INTEGER_DOUBLE..C.MAX_SAFE_INTEGER_DOUBLE &&
            number % C.WHOLE_NUMBER_CHECK == C.ZERO_DOUBLE &&
            !(number == 0.0 && number.toRawBits() < 0)
        ) {
            writeLongValueRawInternal(number.toLong())
            buffer.write(C.DOT_ZERO, 0, C.DOT_ZERO.size)
            return
        }

        val scratchBuf = acquireScratch()
        val bytesWrittenLength = GhostDoubleFormatter.writeDoubleDirect(
            value = number,
            scratch = scratchBuf,
            offset = 0,
            fallback = { fallbackNum ->
                if (!fallbackNum.isFinite()) {
                    throw GhostJsonException(C.ERR_NON_FINITE, 0, 0)
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
        if (doubleVal in C.MIN_SAFE_INTEGER_DOUBLE..C.MAX_SAFE_INTEGER_DOUBLE &&
            doubleVal % C.WHOLE_NUMBER_CHECK == C.ZERO_DOUBLE &&
            !(number == 0.0f && number.toRawBits() < 0)
        ) {
            writeLongValueRawInternal(doubleVal.toLong())
            buffer.write(C.DOT_ZERO, 0, C.DOT_ZERO.size)
            return
        }

        val scratchBuf = acquireScratch()
        val bytesWrittenLength = GhostDoubleFormatter.writeFloatDirect(
            value = number,
            scratch = scratchBuf,
            offset = 0,
            fallback = { fallbackNum ->
                if (!fallbackNum.isFinite()) {
                    throw GhostJsonException(C.ERR_NON_FINITE, 0, 0)
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
        buffer.write(C.NULL_BS)
    }

    /**
     * Appends the separator comma if needsComma is true.
     */
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun appendSeparator() {
        if (needsComma) {
            buffer.writeByte(C.COMMA_INT)
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
            buffer.write(C.EMPTY_STRING_BS)
            return
        }

        if (length <= C.PLAIN_ASCII_FAST_PATH_LIMIT) {
            var allPlain = true
            var index = 0
            val escapeMasks = C.ESCAPE_MASKS
            while (index < length) {
                val code = value[index].code
                if (code >= C.ASCII_LIMIT || ((escapeMasks[code shr C.BITMASK_SHIFT] shr (code and C.BITMASK_INDEX_MASK)) and C.BITMASK_UNIT) != 0L) {
                    allPlain = false
                    break
                }
                index++
            }
            if (allPlain) {
                buffer.writeByte(C.QUOTE_INT)
                buffer.writeUtf8(value)
                buffer.writeByte(C.QUOTE_INT)
                return
            }
        }

        writeStringValueRawSlow(value, length)
    }

    private fun writeStringValueRawSlow(value: String, length: Int) {
        val scratchBuf = acquireScratch()
        if (length + C.STRING_QUOTE_PAIR_BYTES > scratchBuf.size) {
            buffer.writeByte(C.QUOTE_INT)
            writeEscaped(value)
            buffer.writeByte(C.QUOTE_INT)
            return
        }

        scratchBuf[0] = C.QUOTE_BYTE
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

        val replacements = C.ESCAPE_REPLACEMENTS
        val escapeMasks = C.ESCAPE_MASKS
        val scratchSize = scratchBuf.size

        if (remaining <= scratchSize) {
            var scratchPos = 0
            var index = start
            while (index < length) {
                val charCode = text[index].code

                // Unrolled fast path for plain ASCII
                if (charCode < C.ASCII_LIMIT) {
                    val maskIdx = charCode shr C.BITMASK_SHIFT
                    val bitIdx = charCode and C.BITMASK_INDEX_MASK
                    if ((escapeMasks[maskIdx] shr bitIdx) and C.BITMASK_UNIT == 0L) {
                        scratchBuf[scratchPos++] = charCode.toByte()
                        index++
                        continue
                    }
                }

                if (scratchPos > 0) {
                    buffer.write(scratchBuf, 0, scratchPos)
                    scratchPos = 0
                }

                if (charCode < C.ASCII_LIMIT) {
                    val replacement = replacements[charCode]
                    if (replacement != null) {
                        buffer.write(replacement)
                    } else {
                        writeUnicodeEscape(charCode, scratchBuf)
                    }
                } else {
                    val currChar = text[index]
                    if (currChar.isHighSurrogate() && index + 1 < length && text[index + 1].isLowSurrogate()) {
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
                charCode < C.ASCII_LIMIT &&
                (escapeMasks[charCode shr C.BITMASK_SHIFT] shr
                        (charCode and C.BITMASK_INDEX_MASK)) and C.BITMASK_UNIT == 0L
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

            if (charCode < C.ASCII_LIMIT) {
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
        val escapeMasks = C.ESCAPE_MASKS
        val escapeReplacements = C.ESCAPE_REPLACEMENTS
        var scratchPos = 1 // Start after the opening quote already written at index 0.
        var index = 0

        while (index < length) {
            val charCode = text[index].code

            if (
                charCode < C.ASCII_LIMIT &&
                (escapeMasks[charCode shr C.BITMASK_SHIFT] shr
                        (charCode and C.BITMASK_INDEX_MASK)) and C.BITMASK_UNIT == 0L
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
            if (charCode < C.ASCII_LIMIT) {
                val replacement = escapeReplacements[charCode]
                if (replacement != null) {
                    buffer.write(replacement)
                } else {
                    writeUnicodeEscape(charCode, scratchBuf)
                }
            } else {
                val currChar = text[index]
                if (currChar.isHighSurrogate() && index + 1 < length && text[index + 1].isLowSurrogate()) {
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
            buffer.writeByte(C.QUOTE_INT)
        } else {
            scratchBuf[scratchPos++] = C.QUOTE_BYTE
            buffer.write(scratchBuf, 0, scratchPos)
        }
    }

    /**
     * Throws an exception when max depth limits are exceeded.
     */
    private fun throwDepthError() {
        throw GhostJsonException(C.ERR_DEPTH_EXCEEDED_MSG, 0, 0)
    }

    /**
     * Formats unicode characters to standard JSON unicode escape string values.
     */
    private fun writeUnicodeEscape(code: Int, scratchBuf: ByteArray) {
        val hexChars = C.HEX_CHARS

        scratchBuf[0] = C.BACKSLASH
        scratchBuf[1] = C.UNICODE_PREFIX_U
        scratchBuf[2] = hexChars[(code shr C.SHIFT_12) and C.HEX_MASK]
        scratchBuf[3] = hexChars[(code shr C.SHIFT_8) and C.HEX_MASK]
        scratchBuf[4] = hexChars[(code shr C.SHIFT_4) and C.HEX_MASK]
        scratchBuf[5] = hexChars[code and C.HEX_MASK]

        buffer.write(scratchBuf, 0, 6)
    }
}

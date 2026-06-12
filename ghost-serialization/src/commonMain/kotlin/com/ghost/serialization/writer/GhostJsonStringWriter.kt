@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.writer

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.GhostJsonConstants.ASCII_LIMIT
import com.ghost.serialization.parser.GhostJsonConstants.BACKSLASH_INT
import com.ghost.serialization.parser.GhostJsonConstants.BITMASK_INDEX_MASK
import com.ghost.serialization.parser.GhostJsonConstants.BITMASK_SHIFT
import com.ghost.serialization.parser.GhostJsonConstants.BITMASK_UNIT
import com.ghost.serialization.parser.GhostJsonConstants.COLON_INT
import com.ghost.serialization.parser.GhostJsonConstants.CLOSE_ARR_INT
import com.ghost.serialization.parser.GhostJsonConstants.CLOSE_OBJ_INT
import com.ghost.serialization.parser.GhostJsonConstants.COMMA_INT
import com.ghost.serialization.parser.GhostJsonConstants.DOUBLE_DIGIT_LUT_CHARS
import com.ghost.serialization.parser.GhostJsonConstants.ERR_DEPTH_EXCEEDED
import com.ghost.serialization.parser.GhostJsonConstants.ERR_NON_FINITE
import com.ghost.serialization.parser.GhostJsonConstants.ESCAPE_MASKS
import com.ghost.serialization.parser.GhostJsonConstants.HEX_CHARS_CHARS
import com.ghost.serialization.parser.GhostJsonConstants.HEX_MASK
import com.ghost.serialization.parser.GhostJsonConstants.HUNDRED_LONG
import com.ghost.serialization.parser.GhostJsonConstants.LONG_SCRATCH_SIZE
import com.ghost.serialization.parser.GhostJsonConstants.MAX_DEPTH
import com.ghost.serialization.parser.GhostJsonConstants.MAX_SAFE_INTEGER_DOUBLE
import com.ghost.serialization.parser.GhostJsonConstants.MIN_SAFE_INTEGER_DOUBLE
import com.ghost.serialization.parser.GhostJsonConstants.OPEN_ARR_INT
import com.ghost.serialization.parser.GhostJsonConstants.OPEN_OBJ_INT
import com.ghost.serialization.parser.GhostJsonConstants.QUOTE_INT
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_12
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_4
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_8
import com.ghost.serialization.parser.GhostJsonConstants.STRING_QUOTE_PAIR_BYTES
import com.ghost.serialization.parser.GhostJsonConstants.TEN_LONG
import com.ghost.serialization.parser.GhostJsonConstants.WHOLE_NUMBER_CHECK
import com.ghost.serialization.parser.GhostJsonConstants.WRITER_SCRATCH_SIZE
import com.ghost.serialization.parser.GhostJsonConstants.MINUS_INT
import com.ghost.serialization.parser.GhostJsonConstants.ZERO_DOUBLE
import com.ghost.serialization.parser.GhostJsonConstants.ZERO_INT
import com.ghost.serialization.parser.GhostJsonConstants.BS_INT
import com.ghost.serialization.parser.GhostJsonConstants.FF_INT
import com.ghost.serialization.parser.GhostJsonConstants.LF_INT
import com.ghost.serialization.parser.GhostJsonConstants.CR_INT
import com.ghost.serialization.parser.GhostJsonConstants.TAB_INT
import com.ghost.serialization.parser.GhostJsonConstants.MIN_SINGLE_DIGIT
import com.ghost.serialization.parser.GhostJsonConstants.MAX_SINGLE_DIGIT
import com.ghost.serialization.parser.GhostJsonConstants.MIN_SINGLE_DIGIT_NEG
import com.ghost.serialization.parser.GhostJsonConstants.MAX_SINGLE_DIGIT_NEG
import com.ghost.serialization.parser.GhostJsonConstants.MIN_SINGLE_DIGIT_L
import com.ghost.serialization.parser.GhostJsonConstants.MAX_SINGLE_DIGIT_L
import com.ghost.serialization.parser.GhostJsonConstants.MIN_SINGLE_DIGIT_NEG_L
import com.ghost.serialization.parser.GhostJsonConstants.MAX_SINGLE_DIGIT_NEG_L
import com.ghost.serialization.parser.GhostJsonConstants.MIN_INT_STR
import com.ghost.serialization.parser.GhostJsonConstants.MIN_LONG_STR
import com.ghost.serialization.parser.GhostJsonConstants.CHAR_QUOTE
import com.ghost.serialization.parser.GhostJsonConstants.CHAR_BACKSLASH
import com.ghost.serialization.parser.GhostJsonConstants.CHAR_U
import com.ghost.serialization.parser.GhostJsonConstants.ESCAPE_QUOTE
import com.ghost.serialization.parser.GhostJsonConstants.ESCAPE_BACKSLASH
import com.ghost.serialization.parser.GhostJsonConstants.ESCAPE_BACKSPACE
import com.ghost.serialization.parser.GhostJsonConstants.ESCAPE_FORM_FEED
import com.ghost.serialization.parser.GhostJsonConstants.ESCAPE_NEWLINE
import com.ghost.serialization.parser.GhostJsonConstants.ESCAPE_CARRIAGE_RETURN
import com.ghost.serialization.parser.GhostJsonConstants.ESCAPE_TAB
import okio.ByteString

class GhostJsonStringWriter @InternalGhostApi constructor(
    @InternalGhostApi val buffer: FlatCharArrayWriter
) {

    @PublishedApi
    internal var needsComma: Boolean = false

    private var depth: Int = 0

    internal var scratch: CharArray? = null

    internal fun acquireScratch(): CharArray {
        val currentScratch = scratch
        if (currentScratch != null) return currentScratch

        val newScratch = CharArray(WRITER_SCRATCH_SIZE)
        scratch = newScratch
        return newScratch
    }

    @Suppress("unused")
    @InternalGhostApi
    fun release() {
        scratch = null
        needsComma = false
        depth = 0
    }

    @InternalGhostApi
    fun reset() {
        needsComma = false
        depth = 0
    }

    @InternalGhostApi
    @Suppress("EmptyFunctionBlock")
    fun flush() {
        /* No Ops */
    }

    // ── Structural ────────────────────────────────────────────────────────────

    fun beginObject(): GhostJsonStringWriter {
        val currentDepth = depth
        if (currentDepth >= MAX_DEPTH) {
            throwDepthError()
        }
        appendSeparator()
        buffer.writeChar(OPEN_OBJ_INT)
        needsComma = false
        depth = currentDepth + 1
        return this
    }

    fun endObject(): GhostJsonStringWriter {
        buffer.writeChar(CLOSE_OBJ_INT)
        needsComma = true
        depth--
        return this
    }

    fun beginArray(): GhostJsonStringWriter {
        val currentDepth = depth
        if (currentDepth >= MAX_DEPTH) {
            throwDepthError()
        }
        appendSeparator()
        buffer.writeChar(OPEN_ARR_INT)
        needsComma = false
        depth = currentDepth + 1
        return this
    }

    fun endArray(): GhostJsonStringWriter {
        buffer.writeChar(CLOSE_ARR_INT)
        needsComma = true
        depth--
        return this
    }

    fun name(key: String): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeChar(QUOTE_INT)
        writeEscaped(key)
        buffer.write2Chars(QUOTE_INT, COLON_INT)
        needsComma = false
        return this
    }

    fun name(key: ByteString): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeAscii(key)
        needsComma = false
        return this
    }

    @InternalGhostApi
    fun writeNameRaw(header: ByteString): GhostJsonStringWriter {
        return name(header)
    }

    @InternalGhostApi
    fun writeField(header: ByteString, value: Int): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeAscii(header)
        writeIntValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: ByteString, value: Long): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeAscii(header)
        writeLongValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: ByteString, value: String): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeAscii(header)
        writeStringValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: ByteString, value: Boolean): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeAscii(header)
        writeBooleanValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: ByteString, value: Double): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeAscii(header)
        writeDoubleValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: ByteString, value: Float): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeAscii(header)
        writeFloatValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeNameRaw(header: String): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeString(header)
        needsComma = false
        return this
    }

    @InternalGhostApi
    fun writeField(header: String, value: Int): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeString(header)
        writeIntValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: String, value: Long): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeString(header)
        writeLongValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: String, value: String): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeString(header)
        writeStringValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: String, value: Boolean): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeString(header)
        writeBooleanValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: String, value: Double): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeString(header)
        writeDoubleValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: String, value: Float): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeString(header)
        writeFloatValueRaw(value)
        needsComma = true
        return this
    }

    // ── value() public API ────────────────────────────────────────────────────

    fun value(text: String): GhostJsonStringWriter {
        appendSeparator()
        writeStringValueRaw(text)
        needsComma = true
        return this
    }

    fun value(number: Int): GhostJsonStringWriter {
        appendSeparator()
        writeIntValueRaw(number)
        needsComma = true
        return this
    }

    fun value(number: Long): GhostJsonStringWriter {
        appendSeparator()
        writeLongValueRaw(number)
        needsComma = true
        return this
    }

    fun value(number: Double): GhostJsonStringWriter {
        appendSeparator()
        writeDoubleValueRaw(number)
        needsComma = true
        return this
    }

    fun value(number: Float): GhostJsonStringWriter {
        appendSeparator()
        writeFloatValueRaw(number)
        needsComma = true
        return this
    }

    fun value(value: Boolean): GhostJsonStringWriter {
        appendSeparator()
        if (value) {
            buffer.writeTrue()
        } else {
            buffer.writeFalse()
        }
        needsComma = true
        return this
    }

    fun nullValue(): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeNull()
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeBooleanValueRaw(value: Boolean) {
        if (value) {
            buffer.writeTrue()
        } else {
            buffer.writeFalse()
        }
    }

    @InternalGhostApi
    fun writeIntValueRaw(value: Int) {
        if (value in MIN_SINGLE_DIGIT..MAX_SINGLE_DIGIT) {
            buffer.writeChar(ZERO_INT + value)
            return
        }
        if (value in MIN_SINGLE_DIGIT_NEG..MAX_SINGLE_DIGIT_NEG) {
            buffer.write2Chars(MINUS_INT, ZERO_INT - value)
            return
        }
        if (value == Int.MIN_VALUE) {
            buffer.writeString(MIN_INT_STR)
            return
        }
        writeLongValueRawInternal(value.toLong())
    }

    @InternalGhostApi
    fun writeLongValueRaw(value: Long) {
        if (value in MIN_SINGLE_DIGIT_L..MAX_SINGLE_DIGIT_L) {
            val intVal = value.toInt()
            buffer.writeChar(ZERO_INT + intVal)
            return
        }
        if (value in MIN_SINGLE_DIGIT_NEG_L..MAX_SINGLE_DIGIT_NEG_L) {
            val intVal = value.toInt()
            buffer.write2Chars(MINUS_INT, ZERO_INT - intVal)
            return
        }
        if (value == Int.MIN_VALUE.toLong()) {
            buffer.writeString(MIN_INT_STR)
            return
        }
        if (value == Long.MIN_VALUE) {
            buffer.writeString(MIN_LONG_STR)
            return
        }
        writeLongValueRawInternal(value)
    }

    private fun writeLongValueRawInternal(value: Long) {
        val scratchBuf = scratch ?: acquireScratch()
        val scratchEnd = LONG_SCRATCH_SIZE
        var scratchIndex = scratchEnd
        var localValue = value
        val isNegative = localValue < 0
        if (isNegative) {
            if (localValue == Long.MIN_VALUE) {
                buffer.writeString(MIN_LONG_STR)
                return
            }
            localValue = -localValue
        }

        while (localValue >= HUNDRED_LONG) {
            val lutIndex = (localValue % HUNDRED_LONG).toInt() * 2
            scratchBuf[--scratchIndex] = DOUBLE_DIGIT_LUT_CHARS[lutIndex + 1]
            scratchBuf[--scratchIndex] = DOUBLE_DIGIT_LUT_CHARS[lutIndex]
            localValue /= HUNDRED_LONG
        }

        if (localValue < TEN_LONG) {
            scratchBuf[--scratchIndex] = (ZERO_INT + localValue.toInt()).toChar()
        } else {
            val lutIndex = localValue.toInt() * 2
            scratchBuf[--scratchIndex] = DOUBLE_DIGIT_LUT_CHARS[lutIndex + 1]
            scratchBuf[--scratchIndex] = DOUBLE_DIGIT_LUT_CHARS[lutIndex]
        }

        if (isNegative) {
            scratchBuf[--scratchIndex] = MINUS_INT.toChar()
        }

        buffer.write(scratchBuf, scratchIndex, scratchEnd - scratchIndex)
    }

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
        // Format double to a byte scratch then write it to flat char writer
        val byteScratch = acquireScratchBuffer(WRITER_SCRATCH_SIZE)
        try {
            val bytesWrittenLength = GhostDoubleFormatter.writeDoubleDirect(
                value = number,
                scratch = byteScratch,
                offset = 0,
                fallback = { fallbackNum ->
                    if (!fallbackNum.isFinite()) {
                        throw GhostJsonException(ERR_NON_FINITE, 0, 0)
                    }
                    buffer.writeString(fallbackNum.toString())
                    -1
                }
            )

            if (bytesWrittenLength > 0) {
                // Write formatted bytes as chars
                for (i in 0 until bytesWrittenLength) {
                    scratchBuf[i] = byteScratch[i].toInt().toChar()
                }
                buffer.write(scratchBuf, 0, bytesWrittenLength)
            }
        } finally {
            com.ghost.serialization.releaseScratchBuffer(byteScratch)
        }
    }

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
        val byteScratch = acquireScratchBuffer(WRITER_SCRATCH_SIZE)
        try {
            val bytesWrittenLength = GhostDoubleFormatter.writeFloatDirect(
                value = number,
                scratch = byteScratch,
                offset = 0,
                fallback = { fallbackNum ->
                    if (!fallbackNum.isFinite()) {
                        throw GhostJsonException(ERR_NON_FINITE, 0, 0)
                    }
                    buffer.writeString(fallbackNum.toString())
                    -1
                }
            )

            if (bytesWrittenLength > 0) {
                for (i in 0 until bytesWrittenLength) {
                    scratchBuf[i] = byteScratch[i].toInt().toChar()
                }
                buffer.write(scratchBuf, 0, bytesWrittenLength)
            }
        } finally {
            com.ghost.serialization.releaseScratchBuffer(byteScratch)
        }
    }

    @Suppress("unused")
    @InternalGhostApi
    fun writeNullValueRaw() {
        buffer.writeNull()
    }

    @PublishedApi
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun appendSeparator() {
        if (needsComma) {
            buffer.writeChar(COMMA_INT)
            needsComma = false
        }
    }

    @InternalGhostApi
    fun writeStringValueRaw(value: String) {
        val length = value.length
        if (length == 0) {
            buffer.write2Chars(QUOTE_INT, QUOTE_INT)
            return
        }

        var allPlain = true
        var index = 0
        val masks = ESCAPE_MASKS
        while (index < length) {
            val code = value[index].code
            if (code >= ASCII_LIMIT || ((masks[code shr BITMASK_SHIFT] shr (code and BITMASK_INDEX_MASK)) and BITMASK_UNIT) != 0L) {
                allPlain = false
                break
            }
            index++
        }
        if (allPlain) {
            buffer.writeQuotedAscii(value, length)
            return
        }

        val scratchBuf = scratch ?: acquireScratch()
        if (length + STRING_QUOTE_PAIR_BYTES > scratchBuf.size) {
            buffer.writeChar(QUOTE_INT)
            writeEscaped(value)
            buffer.writeChar(QUOTE_INT)
            return
        }

        scratchBuf[0] = CHAR_QUOTE
        writeEscapedIntoScratch(value, length, scratchBuf)
    }

    private fun writeEscaped(text: String, start: Int = 0) {
        val scratchBuf = acquireScratch()
        val length = text.length
        val remaining = length - start
        if (remaining <= 0) {
            return
        }

        val escapeMasks = ESCAPE_MASKS
        val scratchSize = scratchBuf.size

        if (remaining <= scratchSize) {
            var scratchIndex = 0
            var charIndex = start
            while (charIndex < length) {
                val charCode = text[charIndex].code

                if (charCode < ASCII_LIMIT) {
                    val maskIndex = charCode shr BITMASK_SHIFT
                    val bitIndex = charCode and BITMASK_INDEX_MASK
                    if ((escapeMasks[maskIndex] shr bitIndex) and BITMASK_UNIT == 0L) {
                        scratchBuf[scratchIndex++] = charCode.toChar()
                        charIndex++
                        continue
                    }
                }

                if (scratchIndex > 0) {
                    buffer.write(scratchBuf, 0, scratchIndex)
                    scratchIndex = 0
                }

                if (charCode < ASCII_LIMIT) {
                    val replacement = getEscapedChar(charCode)
                    if (replacement != null) {
                        buffer.writeString(replacement)
                    } else {
                        writeUnicodeEscape(charCode, scratchBuf)
                    }
                } else {
                    buffer.writeChar(charCode)
                }
                charIndex++
            }
            if (scratchIndex > 0) {
                buffer.write(scratchBuf, 0, scratchIndex)
            }
            return
        }

        var scratchIndex = 0
        var charIndex = start

        while (charIndex < length) {
            val charCode = text[charIndex].code

            if (
                charCode < ASCII_LIMIT &&
                (escapeMasks[charCode shr BITMASK_SHIFT] shr
                        (charCode and BITMASK_INDEX_MASK)) and BITMASK_UNIT == 0L
            ) {
                scratchBuf[scratchIndex++] = charCode.toChar()
                if (scratchIndex == scratchSize) {
                    buffer.write(scratchBuf, 0, scratchIndex)
                    scratchIndex = 0
                }
                charIndex++
                continue
            }

            if (scratchIndex > 0) {
                buffer.write(scratchBuf, 0, scratchIndex)
                scratchIndex = 0
            }

            if (charCode < ASCII_LIMIT) {
                val replacement = getEscapedChar(charCode)
                if (replacement != null) {
                    buffer.writeString(replacement)
                } else {
                    writeUnicodeEscape(charCode, scratchBuf)
                }
            } else {
                buffer.writeChar(charCode)
            }
            charIndex++
        }

        if (scratchIndex > 0) {
            buffer.write(scratchBuf, 0, scratchIndex)
        }
    }

    private fun writeEscapedIntoScratch(text: String, length: Int, scratchBuf: CharArray) {
        val escapeMasks = ESCAPE_MASKS
        var scratchIndex = 1
        var charIndex = 0

        while (charIndex < length) {
            val charCode = text[charIndex].code

            if (
                charCode < ASCII_LIMIT &&
                (escapeMasks[charCode shr BITMASK_SHIFT] shr
                        (charCode and BITMASK_INDEX_MASK)) and BITMASK_UNIT == 0L
            ) {
                scratchBuf[scratchIndex++] = charCode.toChar()
                charIndex++
                continue
            }

            if (scratchIndex > 0) {
                buffer.write(scratchBuf, 0, scratchIndex)
                scratchIndex = 0
            }

            if (charCode < ASCII_LIMIT) {
                val replacement = getEscapedChar(charCode)
                if (replacement != null) {
                    buffer.writeString(replacement)
                } else {
                    writeUnicodeEscape(charCode, scratchBuf)
                }
            } else {
                buffer.writeChar(charCode)
            }
            charIndex++
        }

        if (scratchIndex + 1 > scratchBuf.size) {
            if (scratchIndex > 0) {
                buffer.write(scratchBuf, 0, scratchIndex)
            }
            buffer.writeChar(QUOTE_INT)
        } else {
            scratchBuf[scratchIndex++] = CHAR_QUOTE
            buffer.write(scratchBuf, 0, scratchIndex)
        }
    }

    private fun getEscapedChar(code: Int): String? {
        return when (code) {
            QUOTE_INT -> ESCAPE_QUOTE
            BACKSLASH_INT -> ESCAPE_BACKSLASH
            BS_INT -> ESCAPE_BACKSPACE
            FF_INT -> ESCAPE_FORM_FEED
            LF_INT -> ESCAPE_NEWLINE
            CR_INT -> ESCAPE_CARRIAGE_RETURN
            TAB_INT -> ESCAPE_TAB
            else -> null
        }
    }

    private fun throwDepthError() {
        throw GhostJsonException("$ERR_DEPTH_EXCEEDED (${MAX_DEPTH})", 0, 0)
    }

    private fun writeUnicodeEscape(code: Int, scratchBuf: CharArray) {
        val hexChars = HEX_CHARS_CHARS

        scratchBuf[0] = CHAR_BACKSLASH
        scratchBuf[1] = CHAR_U
        scratchBuf[2] = hexChars[(code shr SHIFT_12) and HEX_MASK]
        scratchBuf[3] = hexChars[(code shr SHIFT_8) and HEX_MASK]
        scratchBuf[4] = hexChars[(code shr SHIFT_4) and HEX_MASK]
        scratchBuf[5] = hexChars[code and HEX_MASK]

        buffer.write(scratchBuf, 0, 6)
    }
}

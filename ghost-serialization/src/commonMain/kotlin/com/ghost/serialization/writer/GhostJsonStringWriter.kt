@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.writer

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.releaseScratchBuffer
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.GhostJsonConstants as C
import okio.ByteString

@Suppress("SameParameterValue", "NOTHING_TO_INLINE")
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

        val newScratch = CharArray(C.WRITER_SCRATCH_SIZE)
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
        if (currentDepth >= C.MAX_DEPTH) {
            throwDepthError()
        }
        appendSeparator()
        buffer.writeChar(C.OPEN_OBJ_INT)
        needsComma = false
        depth = currentDepth + 1
        return this
    }

    fun endObject(): GhostJsonStringWriter {
        buffer.writeChar(C.CLOSE_OBJ_INT)
        needsComma = true
        depth--
        return this
    }

    fun beginArray(): GhostJsonStringWriter {
        val currentDepth = depth
        if (currentDepth >= C.MAX_DEPTH) {
            throwDepthError()
        }
        appendSeparator()
        buffer.writeChar(C.OPEN_ARR_INT)
        needsComma = false
        depth = currentDepth + 1
        return this
    }

    fun endArray(): GhostJsonStringWriter {
        buffer.writeChar(C.CLOSE_ARR_INT)
        needsComma = true
        depth--
        return this
    }

    fun name(key: String): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeChar(C.QUOTE_INT)
        writeEscaped(key)
        buffer.write2Chars(C.QUOTE_INT, C.COLON_INT)
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
        if (value in C.MIN_SINGLE_DIGIT..C.MAX_SINGLE_DIGIT) {
            buffer.writeChar(C.ZERO_INT + value)
            return
        }
        if (value in C.MIN_SINGLE_DIGIT_NEG..C.MAX_SINGLE_DIGIT_NEG) {
            buffer.write2Chars(C.MINUS_INT, C.ZERO_INT - value)
            return
        }
        if (value == Int.MIN_VALUE) {
            buffer.writeString(C.MIN_INT_STR)
            return
        }
        writeLongValueRawInternal(value.toLong())
    }

    @InternalGhostApi
    fun writeLongValueRaw(value: Long) {
        if (value in C.MIN_SINGLE_DIGIT_L..C.MAX_SINGLE_DIGIT_L) {
            val intVal = value.toInt()
            buffer.writeChar(C.ZERO_INT + intVal)
            return
        }
        if (value in C.MIN_SINGLE_DIGIT_NEG_L..C.MAX_SINGLE_DIGIT_NEG_L) {
            val intVal = value.toInt()
            buffer.write2Chars(C.MINUS_INT, C.ZERO_INT - intVal)
            return
        }
        if (value == Int.MIN_VALUE.toLong()) {
            buffer.writeString(C.MIN_INT_STR)
            return
        }
        if (value == Long.MIN_VALUE) {
            buffer.writeString(C.MIN_LONG_STR)
            return
        }
        writeLongValueRawInternal(value)
    }

    private fun writeLongValueRawInternal(value: Long) {
        val scratchBuf = scratch ?: acquireScratch()
        val scratchEnd = C.LONG_SCRATCH_SIZE
        var scratchIndex = scratchEnd
        var localValue = value
        val isNegative = localValue < 0
        if (isNegative) {
            if (localValue == Long.MIN_VALUE) {
                buffer.writeString(C.MIN_LONG_STR)
                return
            }
            localValue = -localValue
        }

        while (localValue >= C.HUNDRED_LONG) {
            val lutIndex = (localValue % C.HUNDRED_LONG).toInt() * 2
            scratchBuf[--scratchIndex] = C.DOUBLE_DIGIT_LUT_CHARS[lutIndex + 1]
            scratchBuf[--scratchIndex] = C.DOUBLE_DIGIT_LUT_CHARS[lutIndex]
            localValue /= C.HUNDRED_LONG
        }

        if (localValue < C.TEN_LONG) {
            scratchBuf[--scratchIndex] = (C.ZERO_INT + localValue.toInt()).toChar()
        } else {
            val lutIndex = localValue.toInt() * 2
            scratchBuf[--scratchIndex] = C.DOUBLE_DIGIT_LUT_CHARS[lutIndex + 1]
            scratchBuf[--scratchIndex] = C.DOUBLE_DIGIT_LUT_CHARS[lutIndex]
        }

        if (isNegative) {
            scratchBuf[--scratchIndex] = C.MINUS_INT.toChar()
        }

        buffer.write(scratchBuf, scratchIndex, scratchEnd - scratchIndex)
    }

    @InternalGhostApi
    fun writeDoubleValueRaw(number: Double) {
        if (number in C.MIN_SAFE_INTEGER_DOUBLE..C.MAX_SAFE_INTEGER_DOUBLE &&
            number % C.WHOLE_NUMBER_CHECK == C.ZERO_DOUBLE &&
            !(number == 0.0 && number.toRawBits() < 0)
        ) {
            writeLongValueRawInternal(number.toLong())
            buffer.writeDotZero()
            return
        }

        val scratchBuf = acquireScratch()
        // Format double to a byte scratch then write it to flat char writer
        val byteScratch = acquireScratchBuffer(C.WRITER_SCRATCH_SIZE)
        try {
            val bytesWrittenLength = GhostDoubleFormatter.writeDoubleDirect(
                value = number,
                scratch = byteScratch,
                offset = 0,
                fallback = { fallbackNum ->
                    if (!fallbackNum.isFinite()) {
                        throw GhostJsonException(C.ERR_NON_FINITE, 0, 0)
                    }
                    buffer.writeString(fallbackNum.toString())
                    -1
                }
            )

            if (bytesWrittenLength > 0) {
                // Write formatted bytes as chars
                var idx = 0
                while (idx < bytesWrittenLength) {
                    scratchBuf[idx] = byteScratch[idx].toInt().toChar()
                    idx++
                }
                buffer.write(scratchBuf, 0, bytesWrittenLength)
            }
        } finally {
            releaseScratchBuffer(byteScratch)
        }
    }

    fun writeFloatValueRaw(number: Float) {
        val doubleVal = number.toDouble()
        if (doubleVal in C.MIN_SAFE_INTEGER_DOUBLE..C.MAX_SAFE_INTEGER_DOUBLE &&
            doubleVal % C.WHOLE_NUMBER_CHECK == C.ZERO_DOUBLE &&
            !(number == 0.0f && number.toRawBits() < 0)
        ) {
            writeLongValueRawInternal(doubleVal.toLong())
            buffer.writeDotZero()
            return
        }

        val scratchBuf = acquireScratch()
        val byteScratch = acquireScratchBuffer(C.WRITER_SCRATCH_SIZE)
        try {
            val bytesWrittenLength = GhostDoubleFormatter.writeFloatDirect(
                value = number,
                scratch = byteScratch,
                offset = 0,
                fallback = { fallbackNum ->
                    if (!fallbackNum.isFinite()) {
                        throw GhostJsonException(C.ERR_NON_FINITE, 0, 0)
                    }
                    buffer.writeString(fallbackNum.toString())
                    -1
                }
            )

            if (bytesWrittenLength > 0) {
                var idx = 0
                while (idx < bytesWrittenLength) {
                    scratchBuf[idx] = byteScratch[idx].toInt().toChar()
                    idx++
                }
                buffer.write(scratchBuf, 0, bytesWrittenLength)
            }
        } finally {
            releaseScratchBuffer(byteScratch)
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
            buffer.writeChar(C.COMMA_INT)
            needsComma = false
        }
    }

    @InternalGhostApi
    fun writeStringValueRaw(value: String) {
        val length = value.length
        if (length == 0) {
            buffer.write2Chars(C.QUOTE_INT, C.QUOTE_INT)
            return
        }

        val localAsciiLimit = C.ASCII_LIMIT
        val localEscapeMasks = C.ESCAPE_MASKS
        val localShift = C.BITMASK_SHIFT
        val localIndexMask = C.BITMASK_INDEX_MASK
        val localUnit = C.BITMASK_UNIT
        val resultNone = C.RESULT_NONE

        var index = 0
        while (index < length) {
            val code = value[index].code
            if (!isPlainAscii(
                    code,
                    localAsciiLimit,
                    localEscapeMasks,
                    localShift,
                    localIndexMask,
                    localUnit,
                    resultNone
                )
            ) {
                writeStringValueRawSlow(value, length, index)
                return
            }
            index++
        }
        buffer.writeQuotedAscii(value, length)
    }

    private fun writeStringValueRawSlow(value: String, length: Int, breakIndex: Int) {
        val scratchBuf = acquireScratch()
        if (breakIndex == 0 && length + C.STRING_QUOTE_PAIR_BYTES <= scratchBuf.size) {
            scratchBuf[0] = C.CHAR_QUOTE
            writeEscapedIntoScratch(value, length, scratchBuf)
            return
        }
        buffer.writeChar(C.QUOTE_INT)
        if (breakIndex > 0) {
            buffer.writeString(value, 0, breakIndex)
        }
        writeEscaped(value, start = breakIndex)
        buffer.writeChar(C.QUOTE_INT)
    }

    private inline fun getEscapeSecondChar(code: Int): Int {
        return when (code) {
            C.QUOTE_INT -> C.QUOTE_INT
            C.BACKSLASH_INT -> C.BACKSLASH_INT
            C.BS_INT -> C.ESC_B_INT
            C.FF_INT -> C.ESC_F_INT
            C.LF_INT -> C.ESC_N_INT
            C.CR_INT -> C.ESC_R_INT
            C.TAB_INT -> C.ESC_T_INT
            else -> 0
        }
    }

    private fun writeEscaped(text: String, start: Int = 0) {
        val scratchBuf = acquireScratch()
        val length = text.length
        val remaining = length - start
        if (remaining <= 0) {
            return
        }

        val scratchSize = scratchBuf.size
        val localAsciiLimit = C.ASCII_LIMIT
        val localEscapeMasks = C.ESCAPE_MASKS
        val localShift = C.BITMASK_SHIFT
        val localIndexMask = C.BITMASK_INDEX_MASK
        val localUnit = C.BITMASK_UNIT
        val resultNone = C.RESULT_NONE

        if (remaining <= scratchSize) {
            var scratchIndex = 0
            var charIndex = start
            while (charIndex < length) {
                val charCode = text[charIndex].code

                if (isPlainAscii(
                        charCode,
                        localAsciiLimit,
                        localEscapeMasks,
                        localShift,
                        localIndexMask,
                        localUnit,
                        resultNone
                    )
                ) {
                    scratchBuf[scratchIndex++] = charCode.toChar()
                    charIndex++
                    continue
                }

                if (scratchIndex > 0) {
                    buffer.write(scratchBuf, 0, scratchIndex)
                    scratchIndex = 0
                }

                if (charCode < localAsciiLimit) {
                    val esc = getEscapeSecondChar(charCode)
                    if (esc != 0) {
                        buffer.write2Chars(C.BACKSLASH_INT, esc)
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

            if (isPlainAscii(
                    charCode,
                    localAsciiLimit,
                    localEscapeMasks,
                    localShift,
                    localIndexMask,
                    localUnit,
                    resultNone
                )
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

            if (charCode < localAsciiLimit) {
                val esc = getEscapeSecondChar(charCode)
                if (esc != 0) {
                    buffer.write2Chars(C.BACKSLASH_INT, esc)
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
        var scratchIndex = 1
        var charIndex = 0
        val localAsciiLimit = C.ASCII_LIMIT
        val localEscapeMasks = C.ESCAPE_MASKS
        val localShift = C.BITMASK_SHIFT
        val localIndexMask = C.BITMASK_INDEX_MASK
        val localUnit = C.BITMASK_UNIT
        val resultNone = C.RESULT_NONE

        while (charIndex < length) {
            val charCode = text[charIndex].code

            if (isPlainAscii(
                    charCode,
                    localAsciiLimit,
                    localEscapeMasks,
                    localShift,
                    localIndexMask,
                    localUnit,
                    resultNone
                )
            ) {
                scratchBuf[scratchIndex++] = charCode.toChar()
                charIndex++
                continue
            }

            if (scratchIndex > 0) {
                buffer.write(scratchBuf, 0, scratchIndex)
                scratchIndex = 0
            }

            if (charCode < localAsciiLimit) {
                val esc = getEscapeSecondChar(charCode)
                if (esc != 0) {
                    buffer.write2Chars(C.BACKSLASH_INT, esc)
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
            buffer.writeChar(C.QUOTE_INT)
        } else {
            scratchBuf[scratchIndex++] = C.CHAR_QUOTE
            buffer.write(scratchBuf, 0, scratchIndex)
        }
    }

    private fun throwDepthError() {
        throw GhostJsonException(C.ERR_DEPTH_EXCEEDED_MSG, 0, 0)
    }

    private fun writeUnicodeEscape(code: Int, scratchBuf: CharArray) {
        val hexChars = C.HEX_CHARS_CHARS

        scratchBuf[0] = C.CHAR_BACKSLASH
        scratchBuf[1] = C.CHAR_U
        scratchBuf[2] = hexChars[(code shr C.SHIFT_12) and C.HEX_MASK]
        scratchBuf[3] = hexChars[(code shr C.SHIFT_8) and C.HEX_MASK]
        scratchBuf[4] = hexChars[(code shr C.SHIFT_4) and C.HEX_MASK]
        scratchBuf[5] = hexChars[code and C.HEX_MASK]

        buffer.write(scratchBuf, 0, 6)
    }

    private inline fun isPlainAscii(
        charCode: Int,
        asciiLimit: Int,
        escapeMasks: LongArray,
        shift: Int,
        indexMask: Int,
        unit: Long,
        resultNone: Long
    ): Boolean {
        return charCode < asciiLimit &&
                ((escapeMasks[charCode shr shift] shr (charCode and indexMask)) and unit) == resultNone
    }
}

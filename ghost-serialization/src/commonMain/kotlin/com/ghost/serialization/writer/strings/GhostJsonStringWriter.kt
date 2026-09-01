@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.writer.strings

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.types.RawJson
import com.ghost.serialization.parser.common.GhostJsonConstants.ASCII_LIMIT
import com.ghost.serialization.parser.common.GhostJsonConstants.BACKSLASH_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.BITMASK_INDEX_MASK
import com.ghost.serialization.parser.common.GhostJsonConstants.BITMASK_SHIFT
import com.ghost.serialization.parser.common.GhostJsonConstants.BITMASK_UNIT
import com.ghost.serialization.parser.common.GhostJsonConstants.BS_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.CHAR_QUOTE
import com.ghost.serialization.parser.common.GhostJsonConstants.CLOSE_ARR_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.CLOSE_OBJ_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.COLON_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.COMMA_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.CR_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.ERR_DEPTH_EXCEEDED
import com.ghost.serialization.parser.common.GhostJsonConstants.ERR_NON_FINITE
import com.ghost.serialization.parser.common.GhostJsonConstants.ESCAPE_MASKS
import com.ghost.serialization.parser.common.GhostJsonConstants.ESC_B_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.ESC_F_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.ESC_N_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.ESC_R_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.ESC_T_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.FF_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.LF_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.LONG_SCRATCH_SIZE
import com.ghost.serialization.parser.common.GhostJsonConstants.MAX_DEPTH
import com.ghost.serialization.parser.common.GhostJsonConstants.MAX_SAFE_INTEGER_DOUBLE
import com.ghost.serialization.parser.common.GhostJsonConstants.MAX_SINGLE_DIGIT
import com.ghost.serialization.parser.common.GhostJsonConstants.MAX_SINGLE_DIGIT_L
import com.ghost.serialization.parser.common.GhostJsonConstants.MAX_SINGLE_DIGIT_NEG
import com.ghost.serialization.parser.common.GhostJsonConstants.MAX_SINGLE_DIGIT_NEG_L
import com.ghost.serialization.parser.common.GhostJsonConstants.MINUS_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.MIN_INT_STR
import com.ghost.serialization.parser.common.GhostJsonConstants.MIN_LONG_STR
import com.ghost.serialization.parser.common.GhostJsonConstants.MIN_SAFE_INTEGER_DOUBLE
import com.ghost.serialization.parser.common.GhostJsonConstants.MIN_SINGLE_DIGIT
import com.ghost.serialization.parser.common.GhostJsonConstants.MIN_SINGLE_DIGIT_L
import com.ghost.serialization.parser.common.GhostJsonConstants.MIN_SINGLE_DIGIT_NEG
import com.ghost.serialization.parser.common.GhostJsonConstants.MIN_SINGLE_DIGIT_NEG_L
import com.ghost.serialization.parser.common.GhostJsonConstants.OPEN_ARR_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.OPEN_OBJ_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.QUOTE_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.RESULT_NONE
import com.ghost.serialization.parser.common.GhostJsonConstants.STRING_QUOTE_PAIR_BYTES
import com.ghost.serialization.parser.common.GhostJsonConstants.TAB_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.WHOLE_NUMBER_CHECK
import com.ghost.serialization.parser.common.GhostJsonConstants.WRITER_SCRATCH_SIZE
import com.ghost.serialization.parser.common.GhostJsonConstants.ZERO_DOUBLE
import com.ghost.serialization.parser.common.GhostJsonConstants.ZERO_INT
import com.ghost.serialization.writer.common.GhostDoubleFormatter
import com.ghost.serialization.writer.common.GhostJsonEscapeHelpers
import com.ghost.serialization.writer.common.GhostWriterLongDigits
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

        val newScratch = CharArray(WRITER_SCRATCH_SIZE)
        scratch = newScratch
        return newScratch
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
    fun writeField(header: ByteString, value: ULong): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeAscii(header)
        writeULongValueRaw(value)
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
    fun writeField(header: String, value: ULong): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeString(header)
        writeULongValueRaw(value)
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

    fun value(number: ULong): GhostJsonStringWriter {
        appendSeparator()
        writeULongValueRaw(number)
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

    /**
     * Writes a single [Char] as a JSON string without allocating an intermediate [String].
     */
    fun value(char: Char): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeChar(QUOTE_INT)
        buffer.writeChar(char.code)
        buffer.writeChar(QUOTE_INT)
        needsComma = true
        return this
    }

    fun nullValue(): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeNull()
        needsComma = true
        return this
    }

    /**
     * Writes raw JSON bytes directly into the stream without quoting or escaping.
     * The bytes are decoded from UTF-8 to the internal char buffer.
     */
    fun rawValue(bytes: ByteArray): GhostJsonStringWriter {
        appendSeparator()
        buffer.appendUtf8(bytes, 0, bytes.size)
        needsComma = true
        return this
    }

    /** Writes a slice of raw JSON bytes after decoding the UTF-8 range. */
    fun rawValue(bytes: ByteArray, offset: Int, length: Int): GhostJsonStringWriter {
        appendSeparator()
        buffer.appendUtf8(bytes, offset, length)
        needsComma = true
        return this
    }

    /** Writes [raw] using its storage slice. */
    fun rawValue(raw: RawJson): GhostJsonStringWriter =
        rawValue(raw.storage, raw.storageOffset, raw.storageLength)

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

    @InternalGhostApi
    fun writeULongValueRaw(value: ULong) {
        if (value <= Long.MAX_VALUE.toULong()) {
            writeLongValueRaw(value.toLong())
        } else {
            writeStringValueRaw(value.toString())
        }
    }

    private fun writeLongValueRawInternal(value: Long) {
        val scratchBuf = scratch ?: acquireScratch()
        var localValue = value
        val isNegative = localValue < 0
        if (isNegative) {
            if (localValue == Long.MIN_VALUE) {
                buffer.writeString(MIN_LONG_STR)
                return
            }
            localValue = -localValue
        }

        val scratchEnd = LONG_SCRATCH_SIZE
        val scratchIndex = GhostWriterLongDigits.writeDigitsChars(
            absoluteValue = localValue,
            negative = isNegative,
            scratch = scratchBuf,
            scratchEnd = scratchEnd,
        )
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
        val byteScratch = acquireScratchBuffer(WRITER_SCRATCH_SIZE)
        try {
            val bytesWrittenLength = GhostDoubleFormatter.writeDoubleDirect(
                value = number,
                scratch = byteScratch,
                offset = 0,
            )
            if (bytesWrittenLength == GhostDoubleFormatter.FALLBACK_REQUIRED) {
                if (!number.isFinite()) {
                    throw GhostJsonException(ERR_NON_FINITE, 0, 0)
                }
                buffer.writeString(number.toString())
            } else if (bytesWrittenLength > 0) {
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
            )
            if (bytesWrittenLength == GhostDoubleFormatter.FALLBACK_REQUIRED) {
                if (!number.isFinite()) {
                    throw GhostJsonException(ERR_NON_FINITE, 0, 0)
                }
                buffer.writeString(number.toString())
            } else if (bytesWrittenLength > 0) {
                for (i in 0 until bytesWrittenLength) {
                    scratchBuf[i] = byteScratch[i].toInt().toChar()
                }
                buffer.write(scratchBuf, 0, bytesWrittenLength)
            }
        } finally {
            com.ghost.serialization.releaseScratchBuffer(byteScratch)
        }
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

        val localAsciiLimit = ASCII_LIMIT
        val localEscapeMasks = ESCAPE_MASKS
        val localShift = BITMASK_SHIFT
        val localIndexMask = BITMASK_INDEX_MASK
        val localUnit = BITMASK_UNIT
        val resultNone = RESULT_NONE

        var index = 0
        while (index < length) {
            val code = value[index].code
            // Char-channel: BMP/supplementary code units (>= 128) need no JSON escape and can
            // ride the bulk copy path. Byte writers must keep the stricter ASCII gate (UTF-8).
            if (!isSafeUnescaped(
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
        if (breakIndex == 0 && length + STRING_QUOTE_PAIR_BYTES <= scratchBuf.size) {
            scratchBuf[0] = CHAR_QUOTE
            writeEscapedIntoScratch(value, length, scratchBuf)
            return
        }
        buffer.writeChar(QUOTE_INT)
        if (breakIndex > 0) {
            buffer.writeString(value, 0, breakIndex)
        }
        writeEscaped(value, start = breakIndex)
        buffer.writeChar(QUOTE_INT)
    }

    private inline fun getEscapeSecondChar(code: Int): Int {
        return when (code) {
            QUOTE_INT -> QUOTE_INT
            BACKSLASH_INT -> BACKSLASH_INT
            BS_INT -> ESC_B_INT
            FF_INT -> ESC_F_INT
            LF_INT -> ESC_N_INT
            CR_INT -> ESC_R_INT
            TAB_INT -> ESC_T_INT
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
        val localAsciiLimit = ASCII_LIMIT
        val localEscapeMasks = ESCAPE_MASKS
        val localShift = BITMASK_SHIFT
        val localIndexMask = BITMASK_INDEX_MASK
        val localUnit = BITMASK_UNIT
        val resultNone = RESULT_NONE

        if (remaining <= scratchSize) {
            var scratchIndex = 0
            var charIndex = start
            while (charIndex < length) {
                val charCode = text[charIndex].code

                if (isSafeUnescaped(
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

                // Not safe-unescaped ⇒ ASCII control / quote / backslash (see ESCAPE_MASKS).
                val esc = getEscapeSecondChar(charCode)
                if (esc != 0) {
                    buffer.write2Chars(BACKSLASH_INT, esc)
                } else {
                    writeUnicodeEscape(charCode, scratchBuf)
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

            if (isSafeUnescaped(
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

            val esc = getEscapeSecondChar(charCode)
            if (esc != 0) {
                buffer.write2Chars(BACKSLASH_INT, esc)
            } else {
                writeUnicodeEscape(charCode, scratchBuf)
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
        val localAsciiLimit = ASCII_LIMIT
        val localEscapeMasks = ESCAPE_MASKS
        val localShift = BITMASK_SHIFT
        val localIndexMask = BITMASK_INDEX_MASK
        val localUnit = BITMASK_UNIT
        val resultNone = RESULT_NONE

        while (charIndex < length) {
            val charCode = text[charIndex].code

            if (isSafeUnescaped(
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

            val esc = getEscapeSecondChar(charCode)
            if (esc != 0) {
                buffer.write2Chars(BACKSLASH_INT, esc)
            } else {
                writeUnicodeEscape(charCode, scratchBuf)
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

    private fun throwDepthError() {
        throw GhostJsonException("$ERR_DEPTH_EXCEEDED (${MAX_DEPTH})", 0, 0)
    }

    private fun writeUnicodeEscape(code: Int, scratchBuf: CharArray) {
        GhostJsonEscapeHelpers.writeUnicodeEscapeChars(code, scratchBuf) { buf, offset, len ->
            buffer.write(buf, offset, len)
        }
    }

    /**
     * True when [charCode] can be emitted verbatim inside a JSON string on the char channel.
     * Code units ≥ [asciiLimit] never need escaping (unlike the byte writers, which must UTF-8
     * encode them). Below that, [escapeMasks] rejects controls, `"` and `\`.
     */
    private inline fun isSafeUnescaped(
        charCode: Int,
        asciiLimit: Int,
        escapeMasks: LongArray,
        shift: Int,
        indexMask: Int,
        unit: Long,
        resultNone: Long
    ): Boolean {
        return charCode >= asciiLimit ||
                ((escapeMasks[charCode shr shift] shr (charCode and indexMask)) and unit) == resultNone
    }
}

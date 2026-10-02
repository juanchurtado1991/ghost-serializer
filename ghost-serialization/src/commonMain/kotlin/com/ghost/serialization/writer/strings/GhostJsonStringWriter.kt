@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.writer.strings

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.common.constants.GhostJsonErrorMessages.ERR_DEPTH_EXCEEDED
import com.ghost.serialization.parser.common.constants.GhostJsonErrorMessages.ERR_NON_FINITE
import com.ghost.serialization.parser.common.constants.GhostJsonNumericLimits.MAX_DEPTH
import com.ghost.serialization.parser.common.constants.GhostJsonNumericLimits.MAX_SAFE_INTEGER_DOUBLE
import com.ghost.serialization.parser.common.constants.GhostJsonNumericLimits.MIN_INT_STR
import com.ghost.serialization.parser.common.constants.GhostJsonNumericLimits.MIN_LONG_STR
import com.ghost.serialization.parser.common.constants.GhostJsonNumericLimits.MIN_SAFE_INTEGER_DOUBLE
import com.ghost.serialization.parser.common.constants.GhostJsonNumericLimits.WHOLE_NUMBER_CHECK
import com.ghost.serialization.parser.common.constants.GhostJsonNumericLimits.ZERO_DOUBLE
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.BACKSLASH_INT
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.BS_INT
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.B_BYTE_INT
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.CHAR_QUOTE
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.COLON_INT
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.COMMA_INT
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.CR_INT
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.FF_INT
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.F_BYTE_INT
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.LF_INT
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.N_BYTE_INT
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.QUOTE_INT
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.R_BYTE_INT
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.TAB_INT
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.T_BYTE_INT
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants.LONG_SCRATCH_SIZE
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants.STRING_QUOTE_PAIR_BYTES
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants.WRITER_SCRATCH_SIZE
import com.ghost.serialization.releaseScratchBuffer
import com.ghost.serialization.types.RawJson
import com.ghost.serialization.writer.common.GhostDoubleFormatter
import com.ghost.serialization.writer.common.GhostJsonEscapeHelpers
import com.ghost.serialization.writer.bytes.GhostJsonWriterHelpers
import com.ghost.serialization.writer.common.GhostWriterLongDigits
import okio.ByteString

/**
 * Char-channel JSON writer. Structural and int/long/ULong value writes reuse the inline kernels of
 * `GhostJsonWriterHelpers` shared with the byte writers (measured with `benchmarkTwitter`
 * before/after: Encode (String) 242.9±35.4 → 220.2±6.2 µs/op, all other rows within noise, same
 * KB/op). Long/double/float formatting stays local because those kernels are typed to a `ByteArray`
 * scratch while this channel writes into a `CharArray`.
 */
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
    @Suppress("EmptyFunctionBlock")
    fun flush() {
        /* No Ops */
    }

    @InternalGhostApi
    fun reset() {
        needsComma = false
        depth = 0
    }

    // ── Structural ────────────────────────────────────────────────────────────

    fun beginArray(): GhostJsonStringWriter {
        GhostJsonWriterHelpers.beginArrayCore(
            depth = depth,
            maxDepth = MAX_DEPTH,
            appendSeparator = { appendSeparator() },
            writeByte = { buffer.writeChar(charAsInt = it) },
            setDepth = { depth = it },
            throwDepthError = { throwDepthError() },
        )
        needsComma = false
        return this
    }

    fun beginObject(): GhostJsonStringWriter {
        GhostJsonWriterHelpers.beginObjectCore(
            depth = depth,
            maxDepth = MAX_DEPTH,
            appendSeparator = { appendSeparator() },
            writeByte = { buffer.writeChar(charAsInt = it) },
            setDepth = { depth = it },
            throwDepthError = { throwDepthError() },
        )
        needsComma = false
        return this
    }

    fun endArray(): GhostJsonStringWriter {
        GhostJsonWriterHelpers.endArrayCore(
            depth = depth,
            writeByte = { buffer.writeChar(charAsInt = it) },
            setDepth = { depth = it },
        )
        needsComma = true
        return this
    }

    fun endObject(): GhostJsonStringWriter {
        GhostJsonWriterHelpers.endObjectCore(
            depth = depth,
            writeByte = { buffer.writeChar(charAsInt = it) },
            setDepth = { depth = it },
        )
        needsComma = true
        return this
    }

    fun name(key: String): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeChar(charAsInt = QUOTE_INT)
        writeEscaped(key)
        buffer.write2Chars(firstChar = QUOTE_INT, secondChar = COLON_INT)
        needsComma = false
        return this
    }

    fun name(key: ByteString): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeAscii(byteString = key)
        needsComma = false
        return this
    }

    @InternalGhostApi
    fun writeField(header: ByteString, value: Int): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeAscii(byteString = header)
        writeIntValueRaw(value = value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: ByteString, value: Long): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeAscii(byteString = header)
        writeLongValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: ByteString, value: ULong): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeAscii(byteString = header)
        writeULongValueRaw(value = value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: ByteString, value: String): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeAscii(byteString = header)
        writeStringValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: ByteString, value: Boolean): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeAscii(byteString = header)
        writeBooleanValueRaw(value = value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: ByteString, value: Double): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeAscii(byteString = header)
        writeDoubleValueRaw(number = value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: ByteString, value: Float): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeAscii(byteString = header)
        writeFloatValueRaw(number = value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: String, value: Int): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeString(text = header)
        writeIntValueRaw(value = value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: String, value: Long): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeString(text = header)
        writeLongValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: String, value: ULong): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeString(text = header)
        writeULongValueRaw(value = value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: String, value: String): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeString(text = header)
        writeStringValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: String, value: Boolean): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeString(text = header)
        writeBooleanValueRaw(value = value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: String, value: Double): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeString(text = header)
        writeDoubleValueRaw(number = value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: String, value: Float): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeString(text = header)
        writeFloatValueRaw(number = value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeNameRaw(header: ByteString): GhostJsonStringWriter {
        return name(key = header)
    }

    @InternalGhostApi
    fun writeNameRaw(header: String): GhostJsonStringWriter {
        appendSeparator()
        buffer.writeString(text = header)
        needsComma = false
        return this
    }

    // ── value() public API ────────────────────────────────────────────────────

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
        buffer.appendUtf8(bytes = bytes, offset = 0, length = bytes.size)
        needsComma = true
        return this
    }

    /** Writes a slice of raw JSON bytes after decoding the UTF-8 range. */
    fun rawValue(bytes: ByteArray, offset: Int, length: Int): GhostJsonStringWriter {
        appendSeparator()
        buffer.appendUtf8(bytes = bytes, offset = offset, length = length)
        needsComma = true
        return this
    }

    /** Writes [raw] using its storage slice. */
    fun rawValue(raw: RawJson): GhostJsonStringWriter =
        rawValue(bytes = raw.storage, offset = raw.storageOffset, length = raw.storageLength)

    fun value(text: String): GhostJsonStringWriter {
        appendSeparator()
        writeStringValueRaw(text)
        needsComma = true
        return this
    }

    fun value(number: Int): GhostJsonStringWriter {
        appendSeparator()
        writeIntValueRaw(value = number)
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
        writeULongValueRaw(value = number)
        needsComma = true
        return this
    }

    fun value(number: Double): GhostJsonStringWriter {
        appendSeparator()
        writeDoubleValueRaw(number = number)
        needsComma = true
        return this
    }

    fun value(number: Float): GhostJsonStringWriter {
        appendSeparator()
        writeFloatValueRaw(number = number)
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
        buffer.writeChar(charAsInt = QUOTE_INT)
        buffer.writeChar(charAsInt = char.code)
        buffer.writeChar(charAsInt = QUOTE_INT)
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
        GhostJsonWriterHelpers.writeIntValueRawCore(
            value = value,
            writeByte = { buffer.writeChar(charAsInt = it) },
            write2Bytes = { first, second -> buffer.write2Chars(firstChar = first, secondChar = second) },
            writeMinIntBs = { buffer.writeString(text = MIN_INT_STR) },
            writeLongValueRawInternal = { writeLongValueRawInternal(it) },
        )
    }

    @InternalGhostApi
    fun writeLongValueRaw(value: Long) {
        GhostJsonWriterHelpers.writeLongValueRawCore(
            value = value,
            writeByte = { buffer.writeChar(charAsInt = it) },
            write2Bytes = { first, second -> buffer.write2Chars(firstChar = first, secondChar = second) },
            writeMinIntBs = { buffer.writeString(text = MIN_INT_STR) },
            writeMinLongBs = { buffer.writeString(text = MIN_LONG_STR) },
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

    private fun writeLongValueRawInternal(value: Long) {
        val scratchBuf = scratch ?: acquireScratch()
        var localValue = value
        val isNegative = localValue < 0
        if (isNegative) {
            if (localValue == Long.MIN_VALUE) {
                buffer.writeString(text = MIN_LONG_STR)
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
        val isIntegralDouble = number in MIN_SAFE_INTEGER_DOUBLE..MAX_SAFE_INTEGER_DOUBLE &&
            number % WHOLE_NUMBER_CHECK == ZERO_DOUBLE &&
            !(number == 0.0 && number.toRawBits() < 0)
        if (isIntegralDouble) {
            writeLongValueRawInternal(number.toLong())
            buffer.writeDotZero()
            return
        }

        val scratchBuf = acquireScratch()
        val byteScratch = acquireScratchBuffer(minSize = WRITER_SCRATCH_SIZE)
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
                buffer.writeString(text = number.toString())
            } else if (bytesWrittenLength > 0) {
                widenAsciiBytesToChars(source = byteScratch, dest = scratchBuf, length = bytesWrittenLength)
                buffer.write(scratchBuf, 0, bytesWrittenLength)
            }
        } finally {
            releaseScratchBuffer(buffer = byteScratch)
        }
    }

    fun writeFloatValueRaw(number: Float) {
        val doubleVal = number.toDouble()
        val isIntegralFloat = doubleVal in MIN_SAFE_INTEGER_DOUBLE..MAX_SAFE_INTEGER_DOUBLE &&
            doubleVal % WHOLE_NUMBER_CHECK == ZERO_DOUBLE &&
            !(number == 0.0f && number.toRawBits() < 0)
        if (isIntegralFloat) {
            writeLongValueRawInternal(doubleVal.toLong())
            buffer.writeDotZero()
            return
        }

        val scratchBuf = acquireScratch()
        val byteScratch = acquireScratchBuffer(minSize = WRITER_SCRATCH_SIZE)
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
                buffer.writeString(text = number.toString())
            } else if (bytesWrittenLength > 0) {
                widenAsciiBytesToChars(source = byteScratch, dest = scratchBuf, length = bytesWrittenLength)
                buffer.write(scratchBuf, 0, bytesWrittenLength)
            }
        } finally {
            releaseScratchBuffer(buffer = byteScratch)
        }
    }

    /** Widens the first [length] ASCII bytes of [source] into [dest] (double-formatter output). */
    private inline fun widenAsciiBytesToChars(source: ByteArray, dest: CharArray, length: Int) {
        for (i in 0 until length) {
            dest[i] = source[i].toInt().toChar()
        }
    }

    @PublishedApi
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun appendSeparator() {
        if (needsComma) {
            buffer.writeChar(charAsInt = COMMA_INT)
            needsComma = false
        }
    }

    @InternalGhostApi
    fun writeStringValueRaw(value: String) {
        val length = value.length
        if (length == 0) {
            buffer.write2Chars(firstChar = QUOTE_INT, secondChar = QUOTE_INT)
            return
        }

        var index = 0
        while (index < length) {
            val code = value[index].code
            // Char-channel: BMP/supplementary code units (>= 128) need no JSON escape and can
            // ride the bulk copy path. Byte writers must keep the stricter ASCII gate (UTF-8).
            if (!GhostJsonEscapeHelpers.isSafeUnescapedChar(code = code)) {
                writeStringValueRawSlow(value = value, length = length, breakIndex = index)
                return
            }
            index++
        }
        buffer.writeQuotedAscii(text = value, length = length)
    }

    private inline fun getEscapeSecondChar(code: Int): Int {
        return when (code) {
            QUOTE_INT -> QUOTE_INT
            BACKSLASH_INT -> BACKSLASH_INT
            BS_INT -> B_BYTE_INT
            FF_INT -> F_BYTE_INT
            LF_INT -> N_BYTE_INT
            CR_INT -> R_BYTE_INT
            TAB_INT -> T_BYTE_INT
            else -> 0
        }
    }

    private fun throwDepthError(): Nothing =
        throw GhostJsonException("$ERR_DEPTH_EXCEEDED (${MAX_DEPTH})", 0, 0)

    private fun writeEscaped(text: String, start: Int = 0) {
        val scratchBuf = acquireScratch()
        GhostJsonEscapeHelpers.writeEscapedChars(
            text = text,
            start = start,
            scratchBuf = scratchBuf,
            writeChars = { buf, offset, length -> buffer.write(buf, offset, length) },
            writeTwoChars = { first, second -> buffer.write2Chars(firstChar = first, secondChar = second) },
            getEscapeSecondChar = { code -> getEscapeSecondChar(code) },
            writeUnicodeEscape = { code, scratch -> writeUnicodeEscape(code, scratch) },
        )
    }

    private fun writeEscapedIntoScratch(text: String, length: Int, scratchBuf: CharArray) {
        GhostJsonEscapeHelpers.writeEscapedIntoCharScratch(
            text = text,
            length = length,
            scratchBuf = scratchBuf,
            writeChars = { buf, offset, len -> buffer.write(buf, offset, len) },
            writeTwoChars = { first, second -> buffer.write2Chars(firstChar = first, secondChar = second) },
            getEscapeSecondChar = { code -> getEscapeSecondChar(code) },
            writeUnicodeEscape = { code, scratch -> writeUnicodeEscape(code, scratch) },
            writeQuoteChar = { buffer.writeChar(charAsInt = QUOTE_INT) },
        )
    }

    private fun writeStringValueRawSlow(value: String, length: Int, breakIndex: Int) {
        val scratchBuf = acquireScratch()
        if (breakIndex == 0 && length + STRING_QUOTE_PAIR_BYTES <= scratchBuf.size) {
            scratchBuf[0] = CHAR_QUOTE
            writeEscapedIntoScratch(value, length, scratchBuf)
            return
        }
        buffer.writeChar(charAsInt = QUOTE_INT)
        if (breakIndex > 0) {
            buffer.writeString(text = value, beginIndex = 0, endIndex = breakIndex)
        }
        writeEscaped(value, start = breakIndex)
        buffer.writeChar(charAsInt = QUOTE_INT)
    }

    private fun writeUnicodeEscape(code: Int, scratchBuf: CharArray) {
        GhostJsonEscapeHelpers.writeUnicodeEscapeChars(code = code, scratchBuf = scratchBuf) { buf, offset, len ->
            buffer.write(buf, offset, len)
        }
    }
}

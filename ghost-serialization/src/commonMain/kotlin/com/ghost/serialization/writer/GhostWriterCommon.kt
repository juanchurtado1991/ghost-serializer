package com.ghost.serialization.writer

import com.ghost.serialization.parser.GhostJsonConstants.ASCII_LIMIT
import com.ghost.serialization.parser.GhostJsonConstants.BACKSLASH
import com.ghost.serialization.parser.GhostJsonConstants.BITMASK_INDEX_MASK
import com.ghost.serialization.parser.GhostJsonConstants.BITMASK_SHIFT
import com.ghost.serialization.parser.GhostJsonConstants.BITMASK_UNIT
import com.ghost.serialization.parser.GhostJsonConstants.ESCAPE_MASKS
import com.ghost.serialization.parser.GhostJsonConstants.ESCAPE_REPLACEMENTS
import com.ghost.serialization.parser.GhostJsonConstants.HEX_CHARS
import com.ghost.serialization.parser.GhostJsonConstants.HEX_MASK
import com.ghost.serialization.parser.GhostJsonConstants.QUOTE_BYTE
import com.ghost.serialization.parser.GhostJsonConstants.QUOTE_INT
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_12
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_4
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_8
import com.ghost.serialization.parser.GhostJsonConstants.UNICODE_PREFIX_U
import okio.ByteString
import com.ghost.serialization.parser.GhostJsonConstants as C
import com.ghost.serialization.exception.GhostJsonException

internal inline fun writeUnicodeEscapeImpl(
    code: Int,
    scratchBuf: ByteArray,
    writeBytes: (ByteArray, Int, Int) -> Unit
) {
    val hexChars = HEX_CHARS

    scratchBuf[0] = BACKSLASH
    scratchBuf[1] = UNICODE_PREFIX_U
    scratchBuf[2] = hexChars[(code shr SHIFT_12) and HEX_MASK]
    scratchBuf[3] = hexChars[(code shr SHIFT_8) and HEX_MASK]
    scratchBuf[4] = hexChars[(code shr SHIFT_4) and HEX_MASK]
    scratchBuf[5] = hexChars[code and HEX_MASK]

    writeBytes(scratchBuf, 0, 6)
}

internal inline fun writeEscapedImpl(
    text: String,
    start: Int,
    scratchBuf: ByteArray,
    writeBytes: (ByteArray, Int, Int) -> Unit,
    writeReplacementBytes: (ByteArray) -> Unit,
    writeUtf8: (String, Int, Int) -> Unit,
    writeUnicodeEscape: (Int) -> Unit
) {
    val length = text.length
    val remaining = length - start
    if (remaining <= 0) return

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
                writeBytes(scratchBuf, 0, scratchPos)
                scratchPos = 0
            }

            if (charCode < ASCII_LIMIT) {
                val replacement = replacements[charCode]
                if (replacement != null) writeReplacementBytes(replacement)
                else writeUnicodeEscape(charCode)
            } else {
                val c = text[index]
                if (c.isHighSurrogate() && index + 1 < length && text[index + 1].isLowSurrogate()) {
                    writeUtf8(text, index, index + 2)
                    index++
                } else {
                    writeUtf8(text, index, index + 1)
                }
            }
            index++
        }
        if (scratchPos > 0) {
            writeBytes(scratchBuf, 0, scratchPos)
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
                writeBytes(scratchBuf, 0, scratchPos)
                scratchPos = 0
            }
            index++
            continue
        }

        if (scratchPos > 0) {
            writeBytes(scratchBuf, 0, scratchPos)
            scratchPos = 0
        }

        if (charCode < ASCII_LIMIT) {
            val replacement = replacements[charCode]
            if (replacement != null) writeReplacementBytes(replacement)
            else writeUnicodeEscape(charCode)
        } else {
            val char = text[index]
            if (char.isHighSurrogate() && index + 1 < length && text[index + 1].isLowSurrogate()) {
                writeUtf8(text, index, index + 2)
                index++
            } else {
                writeUtf8(text, index, index + 1)
            }
        }
        index++
    }

    if (scratchPos > 0) writeBytes(scratchBuf, 0, scratchPos)
}

internal inline fun writeEscapedIntoScratchImpl(
    text: String,
    length: Int,
    scratchBuf: ByteArray,
    writeBytes: (ByteArray, Int, Int) -> Unit,
    writeByte: (Int) -> Unit,
    writeReplacementBytes: (ByteArray) -> Unit,
    writeUtf8: (String, Int, Int) -> Unit,
    writeUnicodeEscape: (Int) -> Unit
) {
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
            writeBytes(scratchBuf, 0, scratchPos)
            scratchPos = 0
        }

        // Handle the escape
        if (charCode < ASCII_LIMIT) {
            val replacement = ESCAPE_REPLACEMENTS[charCode]
            if (replacement != null) writeReplacementBytes(replacement)
            else writeUnicodeEscape(charCode)
        } else {
            val c = text[index]
            if (c.isHighSurrogate() && index + 1 < length && text[index + 1].isLowSurrogate()) {
                writeUtf8(text, index, index + 2)
                index++
            } else {
                writeUtf8(text, index, index + 1)
            }
        }
        index++
    }

    // Add the closing quote and final flush
    if (scratchPos + 1 > scratchBuf.size) {
        if (scratchPos > 0) writeBytes(scratchBuf, 0, scratchPos)
        writeByte(QUOTE_INT)
    } else {
        scratchBuf[scratchPos++] = QUOTE_BYTE
        writeBytes(scratchBuf, 0, scratchPos)
    }
}

internal inline fun writeLongValueRawInternalImpl(
    value: Long,
    scratchBuf: ByteArray,
    crossinline writeBytes: (ByteArray, Int, Int) -> Unit,
    crossinline writeByteString: (ByteString) -> Unit
) {
    val scratchEnd = C.LONG_SCRATCH_SIZE
    var pos = scratchEnd
    var localValue = value
    val isNegative = localValue < 0
    if (isNegative) {
        if (localValue == Long.MIN_VALUE) {
            writeByteString(C.MIN_LONG_BS)
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

    writeBytes(scratchBuf, pos, scratchEnd - pos)
}

internal inline fun writeDoubleValueRawImpl(
    number: Double,
    scratchBuf: ByteArray,
    crossinline writeLongValueRawInternal: (Long) -> Unit,
    crossinline writeBytes: (ByteArray, Int, Int) -> Unit,
    crossinline writeUtf8: (String) -> Unit
) {
    if (number in C.MIN_SAFE_INTEGER_DOUBLE..C.MAX_SAFE_INTEGER_DOUBLE &&
        number % C.WHOLE_NUMBER_CHECK == C.ZERO_DOUBLE
    ) {
        writeLongValueRawInternal(number.toLong())
        writeBytes(C.DOT_ZERO, 0, C.DOT_ZERO.size)
        return
    }

    val bytesWrittenLength = GhostDoubleFormatter.writeDoubleDirect(
        value = number,
        scratch = scratchBuf,
        offset = 0,
        fallback = { fallbackNum ->
            if (!fallbackNum.isFinite()) {
                throw GhostJsonException(C.ERR_NON_FINITE, 0, 0)
            }
            writeUtf8(fallbackNum.toString())
            -1
        }
    )

    if (bytesWrittenLength > 0) {
        writeBytes(scratchBuf, 0, bytesWrittenLength)
    }
}

internal inline fun beginObjectImpl(
    getDepth: () -> Int,
    setDepth: (Int) -> Unit,
    maxDepth: Int,
    appendSeparator: () -> Unit,
    writeByte: (Int) -> Unit,
    setNeedsComma: (Boolean) -> Unit,
    throwDepthError: () -> Unit
) {
    val d = getDepth()
    if (d >= maxDepth) throwDepthError()
    appendSeparator()
    writeByte(C.OPEN_OBJ_INT)
    setNeedsComma(false)
    setDepth(d + 1)
}

internal inline fun endObjectImpl(
    getDepth: () -> Int,
    setDepth: (Int) -> Unit,
    writeByte: (Int) -> Unit,
    setNeedsComma: (Boolean) -> Unit
) {
    writeByte(C.CLOSE_OBJ_INT)
    setNeedsComma(true)
    setDepth(getDepth() - 1)
}

internal inline fun beginArrayImpl(
    getDepth: () -> Int,
    setDepth: (Int) -> Unit,
    maxDepth: Int,
    appendSeparator: () -> Unit,
    writeByte: (Int) -> Unit,
    setNeedsComma: (Boolean) -> Unit,
    throwDepthError: () -> Unit
) {
    val d = getDepth()
    if (d >= maxDepth) throwDepthError()
    appendSeparator()
    writeByte(C.OPEN_ARR_INT)
    setNeedsComma(false)
    setDepth(d + 1)
}

internal inline fun endArrayImpl(
    getDepth: () -> Int,
    setDepth: (Int) -> Unit,
    writeByte: (Int) -> Unit,
    setNeedsComma: (Boolean) -> Unit
) {
    writeByte(C.CLOSE_ARR_INT)
    setNeedsComma(true)
    setDepth(getDepth() - 1)
}

internal inline fun nameStringImpl(
    appendSeparator: () -> Unit,
    writeByte: (Int) -> Unit,
    writeEscaped: (String) -> Unit,
    writeBytes: (ByteString) -> Unit,
    setNeedsComma: (Boolean) -> Unit,
    key: String
) {
    appendSeparator()
    writeByte(C.QUOTE_INT)
    writeEscaped(key)
    writeBytes(C.COLON_QUOTE_BS)
    setNeedsComma(false)
}

internal inline fun nameByteStringImpl(
    appendSeparator: () -> Unit,
    writeBytes: (ByteString) -> Unit,
    setNeedsComma: (Boolean) -> Unit,
    key: ByteString
) {
    appendSeparator()
    writeBytes(key)
    setNeedsComma(false)
}

internal inline fun writeFieldIntImpl(
    appendSeparator: () -> Unit,
    writeBytes: (ByteString) -> Unit,
    writeIntValueRaw: (Int) -> Unit,
    setNeedsComma: (Boolean) -> Unit,
    header: ByteString,
    value: Int
) {
    appendSeparator()
    writeBytes(header)
    writeIntValueRaw(value)
    setNeedsComma(true)
}

internal inline fun writeFieldLongImpl(
    appendSeparator: () -> Unit,
    writeBytes: (ByteString) -> Unit,
    writeLongValueRaw: (Long) -> Unit,
    setNeedsComma: (Boolean) -> Unit,
    header: ByteString,
    value: Long
) {
    appendSeparator()
    writeBytes(header)
    writeLongValueRaw(value)
    setNeedsComma(true)
}

internal inline fun writeFieldStringImpl(
    appendSeparator: () -> Unit,
    writeBytes: (ByteString) -> Unit,
    writeStringValueRaw: (String) -> Unit,
    setNeedsComma: (Boolean) -> Unit,
    header: ByteString,
    value: String
) {
    appendSeparator()
    writeBytes(header)
    writeStringValueRaw(value)
    setNeedsComma(true)
}

internal inline fun writeFieldBooleanImpl(
    appendSeparator: () -> Unit,
    writeBytes: (ByteString) -> Unit,
    writeBooleanValueRaw: (Boolean) -> Unit,
    setNeedsComma: (Boolean) -> Unit,
    header: ByteString,
    value: Boolean
) {
    appendSeparator()
    writeBytes(header)
    writeBooleanValueRaw(value)
    setNeedsComma(true)
}

internal inline fun writeFieldDoubleImpl(
    appendSeparator: () -> Unit,
    writeBytes: (ByteString) -> Unit,
    writeDoubleValueRaw: (Double) -> Unit,
    setNeedsComma: (Boolean) -> Unit,
    header: ByteString,
    value: Double
) {
    appendSeparator()
    writeBytes(header)
    writeDoubleValueRaw(value)
    setNeedsComma(true)
}

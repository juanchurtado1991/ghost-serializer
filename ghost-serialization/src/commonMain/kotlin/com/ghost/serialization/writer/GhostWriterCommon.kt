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

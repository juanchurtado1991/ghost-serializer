package com.ghost.serialization.writer.common

import com.ghost.serialization.parser.common.GhostJsonConstants.ASCII_LIMIT
import com.ghost.serialization.parser.common.GhostJsonConstants.BACKSLASH
import com.ghost.serialization.parser.common.GhostJsonConstants.BITMASK_INDEX_MASK
import com.ghost.serialization.parser.common.GhostJsonConstants.BITMASK_SHIFT
import com.ghost.serialization.parser.common.GhostJsonConstants.BITMASK_UNIT
import com.ghost.serialization.parser.common.GhostJsonConstants.ESCAPE_MASKS
import com.ghost.serialization.parser.common.GhostJsonConstants.ESCAPE_REPLACEMENTS
import com.ghost.serialization.parser.common.GhostJsonConstants.HEX_CHARS
import com.ghost.serialization.parser.common.GhostJsonConstants.HEX_CHARS_CHARS
import com.ghost.serialization.parser.common.GhostJsonConstants.HEX_MASK
import com.ghost.serialization.parser.common.GhostJsonConstants.QUOTE_BYTE
import com.ghost.serialization.parser.common.GhostJsonConstants.SHIFT_12
import com.ghost.serialization.parser.common.GhostJsonConstants.SHIFT_4
import com.ghost.serialization.parser.common.GhostJsonConstants.SHIFT_8
import com.ghost.serialization.parser.common.GhostJsonConstants.UNICODE_ESCAPE_LENGTH
import com.ghost.serialization.parser.common.GhostJsonConstants.UNICODE_PREFIX_U
import com.ghost.serialization.parser.common.GhostJsonConstants.CHAR_BACKSLASH
import com.ghost.serialization.parser.common.GhostJsonConstants.CHAR_U

/**
 * Shared JSON string-escape helpers for byte and char writers.
 *
 * Sink flushes stay at call sites (or are passed as inlined lambdas) so
 * byte JSON writers keep monomorphic buffer writes.
 */
internal object GhostJsonEscapeHelpers {

    /**
     * True when [code] is a plain ASCII character that can be emitted unescaped
     * (not a control char, `"`, `\`, or non-ASCII). Single source of truth for the
     * bitmask check duplicated across both byte JSON writers and this file.
     */
    fun isPlainAsciiSafe(code: Int): Boolean =
        code < ASCII_LIMIT &&
                (ESCAPE_MASKS[code shr BITMASK_SHIFT] shr (code and BITMASK_INDEX_MASK)) and BITMASK_UNIT == 0L

    /**
     * Formats `\uXXXX` into [scratchBuf] at indices `0..5` and flushes via [write].
     */
    inline fun writeUnicodeEscapeBytes(
        code: Int,
        scratchBuf: ByteArray,
        write: (scratch: ByteArray, offset: Int, length: Int) -> Unit,
    ) {
        val hexChars = HEX_CHARS
        scratchBuf[0] = BACKSLASH
        scratchBuf[1] = UNICODE_PREFIX_U
        scratchBuf[2] = hexChars[(code shr SHIFT_12) and HEX_MASK]
        scratchBuf[3] = hexChars[(code shr SHIFT_8) and HEX_MASK]
        scratchBuf[4] = hexChars[(code shr SHIFT_4) and HEX_MASK]
        scratchBuf[5] = hexChars[code and HEX_MASK]
        write(scratchBuf, 0, UNICODE_ESCAPE_LENGTH)
    }

    /**
     * Formats `\uXXXX` into [scratchBuf] at indices `0..5` and flushes via [write].
     */
    inline fun writeUnicodeEscapeChars(
        code: Int,
        scratchBuf: CharArray,
        write: (scratch: CharArray, offset: Int, length: Int) -> Unit,
    ) {
        val hexChars = HEX_CHARS_CHARS
        scratchBuf[0] = CHAR_BACKSLASH
        scratchBuf[1] = CHAR_U
        scratchBuf[2] = hexChars[(code shr SHIFT_12) and HEX_MASK]
        scratchBuf[3] = hexChars[(code shr SHIFT_8) and HEX_MASK]
        scratchBuf[4] = hexChars[(code shr SHIFT_4) and HEX_MASK]
        scratchBuf[5] = hexChars[code and HEX_MASK]
        write(scratchBuf, 0, UNICODE_ESCAPE_LENGTH)
    }

    /**
     * Escapes [text] from [start] into [scratchBuf], flushing through sink lambdas.
     * Used by both byte JSON writers when the string does not fit the quoted-scratch path.
     */
    inline fun writeEscapedBytes(
        text: String,
        start: Int,
        scratchBuf: ByteArray,
        writeBytes: (scratch: ByteArray, offset: Int, length: Int) -> Unit,
        writeReplacement: (replacement: ByteArray) -> Unit,
        writeUtf8Range: (text: String, beginIndex: Int, endIndex: Int) -> Unit,
    ) {
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
                if (isPlainAsciiSafe(charCode)) {
                    scratchBuf[scratchPos++] = charCode.toByte()
                    index++
                    continue
                }

                if (scratchPos > 0) {
                    writeBytes(scratchBuf, 0, scratchPos)
                    scratchPos = 0
                }

                if (charCode < ASCII_LIMIT) {
                    val replacement = replacements[charCode]
                    if (replacement != null) {
                        writeReplacement(replacement)
                    } else {
                        writeUnicodeEscapeBytes(charCode, scratchBuf, writeBytes)
                    }
                } else {
                    val c = text[index]
                    if (c.isHighSurrogate() && index + 1 < length && text[index + 1].isLowSurrogate()) {
                        writeUtf8Range(text, index, index + 2)
                        index++
                    } else {
                        writeUtf8Range(text, index, index + 1)
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

            if (isPlainAsciiSafe(charCode)) {
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
                if (replacement != null) {
                    writeReplacement(replacement)
                } else {
                    writeUnicodeEscapeBytes(charCode, scratchBuf, writeBytes)
                }
            } else {
                val char = text[index]
                if (char.isHighSurrogate() && index + 1 < length && text[index + 1].isLowSurrogate()) {
                    writeUtf8Range(text, index, index + 2)
                    index++
                } else {
                    writeUtf8Range(text, index, index + 1)
                }
            }
            index++
        }

        if (scratchPos > 0) {
            writeBytes(scratchBuf, 0, scratchPos)
        }
    }

    /**
     * Escapes [text] into [scratchBuf] (opening quote already at index 0) and flushes
     * through the provided sink lambdas. Used by both byte JSON writers.
     */
    inline fun writeEscapedIntoByteScratch(
        text: String,
        length: Int,
        scratchBuf: ByteArray,
        writeBytes: (scratch: ByteArray, offset: Int, length: Int) -> Unit,
        writeReplacement: (replacement: ByteArray) -> Unit,
        writeUtf8Range: (text: String, beginIndex: Int, endIndex: Int) -> Unit,
        writeQuoteByte: () -> Unit,
    ) {
        val escapeReplacements = ESCAPE_REPLACEMENTS
        var scratchPos = 1 // Start after the opening quote already written at index 0.
        var index = 0

        while (index < length) {
            val charCode = text[index].code

            if (isPlainAsciiSafe(charCode)) {
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
                val replacement = escapeReplacements[charCode]
                if (replacement != null) {
                    writeReplacement(replacement)
                } else {
                    writeUnicodeEscapeBytes(charCode, scratchBuf, writeBytes)
                }
            } else {
                val c = text[index]
                if (c.isHighSurrogate() && index + 1 < length && text[index + 1].isLowSurrogate()) {
                    writeUtf8Range(text, index, index + 2)
                    index++
                } else {
                    writeUtf8Range(text, index, index + 1)
                }
            }
            index++
        }

        // Add the closing quote and final flush
        if (scratchPos + 1 > scratchBuf.size) {
            if (scratchPos > 0) {
                writeBytes(scratchBuf, 0, scratchPos)
            }
            writeQuoteByte()
        } else {
            scratchBuf[scratchPos++] = QUOTE_BYTE
            writeBytes(scratchBuf, 0, scratchPos)
        }
    }
}

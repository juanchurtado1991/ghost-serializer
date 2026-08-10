package com.ghost.serialization.writer.yaml

import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.releaseScratchBuffer
import com.ghost.serialization.writer.common.GhostWriterLongDigits
import com.ghost.serialization.yaml.exception.GhostYamlException
import okio.ByteString
import com.ghost.serialization.yaml.GhostYamlConstants as C

/**
 * Shared YAML writer kernels used by [GhostYamlWriter] (okio Buffer) and
 * [GhostYamlFlatWriter] (FlatByteArrayWriter).
 *
 * Sink flushes stay at call sites via inlined lambdas so both backends keep
 * monomorphic writes. Flat-only key quoting stays on [GhostYamlFlatWriter].
 */
internal object GhostYamlWriterHelpers {

    /** Packed [prepareValue] result: bit0 justWroteDash, bit1 pendingSpace, bit2 incrementItemCount. */
    const val PREPARE_JUST_WROTE_DASH = 1
    const val PREPARE_PENDING_SPACE = 2
    const val PREPARE_INCREMENT_ITEM = 4

    fun newScratch(): ByteArray = acquireScratchBuffer(C.SCRATCH_BUFFER_SIZE)

    fun releaseScratch(current: ByteArray?) {
        if (current != null) {
            releaseScratchBuffer(current)
        }
    }

    fun extractKey(header: ByteString): String {
        val size = header.size
        if (size >= C.HEADER_MIN_SIZE &&
            header[C.HEADER_QUOTE_START_OFFSET] == C.DOUBLE_QUOTE_BYTE &&
            header[size - C.HEADER_QUOTE_END_OFFSET_SUB] == C.DOUBLE_QUOTE_BYTE &&
            header[size - C.HEADER_COLON_OFFSET_SUB] == C.COLON_BYTE
        ) {
            return header.substring(C.SUBSTRING_START_OFFSET, size - C.HEADER_QUOTE_END_OFFSET_SUB)
                .utf8()
        }
        return header.utf8()
    }

    inline fun writeIndentation(
        level: Int,
        writeByte: (Int) -> Unit,
    ) {
        val spacesCount = level * C.SPACES_PER_LEVEL
        var count = 0
        while (count < spacesCount) {
            writeByte(C.SPACE_INT)
            count++
        }
    }

    /**
     * Emits array-item dashes / pending key→value spaces.
     *
     * @return packed flags: [PREPARE_JUST_WROTE_DASH], [PREPARE_PENDING_SPACE],
     *   [PREPARE_INCREMENT_ITEM] (caller applies to writer fields / itemCounts).
     */
    inline fun prepareValue(
        isStructural: Boolean,
        depth: Int,
        contextAtDepth: Int,
        justWroteDash: Boolean,
        pendingSpace: Boolean,
        writeByte: (Int) -> Unit,
    ): Int {
        var dash = justWroteDash
        var space = pendingSpace
        var increment = false
        if (depth > 0 && contextAtDepth == C.TYPE_ARRAY) {
            if (justWroteDash) {
                writeByte(C.DASH_INT)
                writeByte(C.SPACE_INT)
            } else {
                writeByte(C.NEWLINE_INT)
                writeIndentation(depth - 1, writeByte)
                writeByte(C.DASH_INT)
                writeByte(C.SPACE_INT)
            }
            increment = true
            dash = isStructural
        } else {
            if (isStructural) {
                space = false
            } else if (space) {
                writeByte(C.SPACE_INT)
                space = false
            }
        }
        var flags = 0
        if (dash) flags = flags or PREPARE_JUST_WROTE_DASH
        if (space) flags = flags or PREPARE_PENDING_SPACE
        if (increment) flags = flags or PREPARE_INCREMENT_ITEM
        return flags
    }

    /**
     * Shared name() layout: validates depth, clears justWroteDash, writes newline+indent when needed.
     * Key bytes and Flat-only quoting stay at the call site.
     *
     * @return [depth] for the caller to index itemCounts after writing the key.
     */
    inline fun prepareNameLayout(
        depth: Int,
        itemCountAtDepth: Int,
        justWroteDash: Boolean,
        writeByte: (Int) -> Unit,
    ): Int {
        if (depth <= 0) {
            throw GhostYamlException(C.ERR_NAME_OUTSIDE_OBJECT)
        }
        if (!justWroteDash) {
            if (itemCountAtDepth > 0 || depth > 1) {
                writeByte(C.NEWLINE_INT)
                writeIndentation(depth - 1, writeByte)
            }
        }
        return depth
    }

    inline fun writeEmptyPlaceholderIfNeeded(
        depth: Int,
        itemCountAtDepth: Int,
        parentContext: Int,
        openInt: Int,
        closeInt: Int,
        writeByte: (Int) -> Unit,
        writeOpenClose: (Int, Int) -> Unit,
    ) {
        if (itemCountAtDepth != 0) return
        val parentDepth = depth - 1
        if (parentDepth > 0 && parentContext == C.TYPE_OBJECT) {
            writeByte(C.SPACE_INT)
        }
        writeOpenClose(openInt, closeInt)
    }

    inline fun writeUnicodeHex(
        code: Int,
        writeByte: (Int) -> Unit,
    ) {
        val hexChars = C.HEX_CHARS_ARR
        writeByte(hexChars[(code shr C.SHIFT_12) and C.HEX_MASK].toInt())
        writeByte(hexChars[(code shr C.SHIFT_8) and C.HEX_MASK].toInt())
        writeByte(hexChars[(code shr C.SHIFT_4) and C.HEX_MASK].toInt())
        writeByte(hexChars[code and C.HEX_MASK].toInt())
    }

    inline fun writeEscaped(
        text: String,
        writeByte: (Int) -> Unit,
        writeUtf8Range: (text: String, beginIndex: Int, endIndex: Int) -> Unit,
    ) {
        val length = text.length
        var index = 0

        while (index < length) {
            val charCode = text[index].code
            if (charCode == C.DOUBLE_QUOTE_INT) {
                writeByte(C.BACKSLASH_INT)
                writeByte(C.DOUBLE_QUOTE_INT)
            } else if (charCode == C.BACKSLASH_INT) {
                writeByte(C.BACKSLASH_INT)
                writeByte(C.BACKSLASH_INT)
            } else {
                when (charCode) {
                    C.CHAR_LF_INT -> {
                        writeByte(C.BACKSLASH_INT)
                        writeByte(C.CHAR_N_INT)
                    }

                    C.CHAR_CR_INT -> {
                        writeByte(C.BACKSLASH_INT)
                        writeByte(C.CHAR_R_INT)
                    }

                    C.CHAR_TAB_INT -> {
                        writeByte(C.BACKSLASH_INT)
                        writeByte(C.CHAR_T_INT)
                    }

                    C.CHAR_BS_INT -> {
                        writeByte(C.BACKSLASH_INT)
                        writeByte(C.CHAR_B_INT)
                    }

                    C.CHAR_FF_INT -> {
                        writeByte(C.BACKSLASH_INT)
                        writeByte(C.CHAR_F_INT)
                    }

                    else -> {
                        if (charCode < C.CHAR_SPACE_INT) {
                            writeByte(C.BACKSLASH_INT)
                            writeByte(C.CHAR_U_INT)
                            writeUnicodeHex(charCode, writeByte)
                        } else if (charCode < C.ASCII_LIMIT) {
                            writeByte(charCode)
                        } else {
                            val charVal = text[index]
                            if (charVal.isHighSurrogate() && index + 1 < length && text[index + 1].isLowSurrogate()) {
                                writeUtf8Range(text, index, index + 2)
                                index++
                            } else {
                                writeUtf8Range(text, index, index + 1)
                            }
                        }
                    }
                }
            }
            index++
        }
    }

    inline fun writeLong(
        value: Long,
        scratch: ByteArray?,
        acquireScratch: () -> ByteArray,
        writeByte: (Int) -> Unit,
        writeUtf8: (String) -> Unit,
        writeBytes: (buf: ByteArray, offset: Int, length: Int) -> Unit,
    ) {
        if (value == 0L) {
            writeByte(C.ZERO_INT)
            return
        }
        var remaining = value
        val isNegative = remaining < 0
        if (isNegative) {
            writeByte(C.DASH_INT)
            if (remaining == Long.MIN_VALUE) {
                writeUtf8(C.STR_MIN_LONG_ABS)
                return
            }
            remaining = -remaining
        }
        val scratchBuf = scratch ?: acquireScratch()
        val pos = GhostWriterLongDigits.writePositiveDigitsBytes(remaining, scratchBuf)
        writeBytes(scratchBuf, pos, scratchBuf.size - pos)
    }
}

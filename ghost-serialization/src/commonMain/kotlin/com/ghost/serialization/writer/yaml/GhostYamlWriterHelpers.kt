package com.ghost.serialization.writer.yaml

import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.releaseScratchBuffer
import com.ghost.serialization.writer.common.GhostWriterLongDigits
import com.ghost.serialization.yaml.GhostYamlConstants as C

/**
 * Shared YAML writer kernels used by [GhostYamlWriter] (okio Buffer) and
 * [GhostYamlFlatWriter] (FlatByteArrayWriter).
 *
 * Sink flushes stay at call sites via inlined lambdas so both backends keep
 * monomorphic writes. Mutable layout state (prepareValue, name indent, etc.)
 * stays on each writer — those paths diverge slightly (e.g. key quoting,
 * write2Bytes) and are not worth a shared state machine.
 */
internal object GhostYamlWriterHelpers {

    fun newScratch(): ByteArray = acquireScratchBuffer(C.SCRATCH_BUFFER_SIZE)

    fun releaseScratch(current: ByteArray?) {
        if (current != null) {
            releaseScratchBuffer(current)
        }
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

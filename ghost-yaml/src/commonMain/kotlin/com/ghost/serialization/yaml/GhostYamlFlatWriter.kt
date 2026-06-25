package com.ghost.serialization.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.releaseScratchBuffer
import com.ghost.serialization.writer.FlatByteArrayWriter
import com.ghost.serialization.yaml.GhostYamlConstants as C
import okio.ByteString

/**
 * Contiguous in-memory specialized YAML writer backed by FlatByteArrayWriter.
 */
@Suppress("CascadeIf")
class GhostYamlFlatWriter @InternalGhostApi constructor(
    @InternalGhostApi val buffer: FlatByteArrayWriter
) {
    internal var depth = 0
    internal var scratch: ByteArray? = null

    private val contexts = IntArray(C.MAX_DEPTH)
    private val itemCounts = IntArray(C.MAX_DEPTH)
    private var pendingSpace = false
    private var justWroteDash = false

    companion object {
        private const val SPACES_PER_LEVEL = 2
        private const val TYPE_ROOT = 0
        private const val TYPE_OBJECT = 1
        private const val TYPE_ARRAY = 2
        private const val PLAIN_ASCII_LIMIT = 64
        private const val SHIFT_12 = 12
        private const val SHIFT_8 = 8
        private const val SHIFT_4 = 4
        private const val HEX_MASK = 0x0F
        private const val TEN_LONG = 10L
        private const val ASCII_LIMIT = 128

        private const val STR_HEX_CHARS = "0123456789abcdef"
        private const val STR_NULL = "null"
        private const val STR_MIN_LONG_ABS = "9223372036854775808"

        private val HEX_CHARS = STR_HEX_CHARS.encodeToByteArray()

        private const val CHAR_LF_INT = 10
        private const val CHAR_CR_INT = 13
        private const val CHAR_TAB_INT = 9
        private const val CHAR_BS_INT = 8
        private const val CHAR_FF_INT = 12
        private const val CHAR_SPACE_INT = 32
    }

    internal fun acquireScratch(): ByteArray {
        val currentScratch = scratch
        if (currentScratch != null) return currentScratch
        val newScratch = acquireScratchBuffer(256)
        scratch = newScratch
        return newScratch
    }

    @InternalGhostApi
    fun release() {
        val currentScratch = scratch
        if (currentScratch != null) {
            releaseScratchBuffer(currentScratch)
            scratch = null
        }
        depth = 0
        pendingSpace = false
        justWroteDash = false
    }

    @InternalGhostApi
    fun reset() {
        depth = 0
        pendingSpace = false
        justWroteDash = false
    }

    private fun writeIndentation(level: Int) {
        val spacesCount = level * SPACES_PER_LEVEL
        var count = 0
        while (count < spacesCount) {
            buffer.writeByte(C.SPACE_BYTE.toInt())
            count++
        }
    }

    private fun prepareValue(isStructural: Boolean) {
        val currentDepth = depth
        if (currentDepth > 0 && contexts[currentDepth] == TYPE_ARRAY) {
            if (justWroteDash) {
                buffer.writeByte(C.DASH_BYTE.toInt())
                buffer.writeByte(C.SPACE_BYTE.toInt())
            } else {
                buffer.writeByte(C.NEWLINE_BYTE.toInt())
                writeIndentation(currentDepth - 1)
                buffer.writeByte(C.DASH_BYTE.toInt())
                buffer.writeByte(C.SPACE_BYTE.toInt())
            }
            itemCounts[currentDepth]++
            justWroteDash = isStructural
        } else {
            if (isStructural) {
                pendingSpace = false
            } else {
                if (pendingSpace) {
                    buffer.writeByte(C.SPACE_BYTE.toInt())
                    pendingSpace = false
                }
            }
        }
    }

    fun beginObject(): GhostYamlFlatWriter {
        val currentDepth = depth
        if (currentDepth >= C.MAX_DEPTH) {
            throw GhostYamlException("Max depth exceeded")
        }
        prepareValue(isStructural = true)
        val nextDepth = currentDepth + 1
        contexts[nextDepth] = TYPE_OBJECT
        itemCounts[nextDepth] = 0
        depth = nextDepth
        return this
    }

    fun endObject(): GhostYamlFlatWriter {
        depth--
        justWroteDash = false
        return this
    }

    fun beginArray(): GhostYamlFlatWriter {
        val currentDepth = depth
        if (currentDepth >= C.MAX_DEPTH) {
            throw GhostYamlException("Max depth exceeded")
        }
        prepareValue(isStructural = true)
        val nextDepth = currentDepth + 1
        contexts[nextDepth] = TYPE_ARRAY
        itemCounts[nextDepth] = 0
        depth = nextDepth
        return this
    }

    fun endArray(): GhostYamlFlatWriter {
        depth--
        justWroteDash = false
        return this
    }

    fun name(key: String): GhostYamlFlatWriter {
        val currentDepth = depth
        if (currentDepth <= 0) {
            throw GhostYamlException("Cannot write name outside of object scope")
        }
        if (justWroteDash) {
            justWroteDash = false
        } else {
            val count = itemCounts[currentDepth]
            if (count > 0) {
                buffer.writeByte(C.NEWLINE_BYTE.toInt())
                writeIndentation(currentDepth - 1)
            } else if (currentDepth > 1) {
                buffer.writeByte(C.NEWLINE_BYTE.toInt())
                writeIndentation(currentDepth - 1)
            }
        }
        buffer.writeUtf8(key)
        buffer.writeByte(C.COLON_BYTE.toInt())
        itemCounts[currentDepth]++
        pendingSpace = true
        return this
    }

    fun name(key: ByteString): GhostYamlFlatWriter {
        val currentDepth = depth
        if (currentDepth <= 0) {
            throw GhostYamlException("Cannot write name outside of object scope")
        }
        if (justWroteDash) {
            justWroteDash = false
        } else {
            val count = itemCounts[currentDepth]
            if (count > 0) {
                buffer.writeByte(C.NEWLINE_BYTE.toInt())
                writeIndentation(currentDepth - 1)
            } else if (currentDepth > 1) {
                buffer.writeByte(C.NEWLINE_BYTE.toInt())
                writeIndentation(currentDepth - 1)
            }
        }
        buffer.write(key)
        itemCounts[currentDepth]++
        pendingSpace = false
        return this
    }

    fun value(text: String): GhostYamlFlatWriter {
        prepareValue(isStructural = false)
        writeStringValueRaw(text)
        return this
    }

    fun value(number: Int): GhostYamlFlatWriter {
        prepareValue(isStructural = false)
        writeLong(number.toLong())
        return this
    }

    fun value(number: Long): GhostYamlFlatWriter {
        prepareValue(isStructural = false)
        writeLong(number)
        return this
    }

    fun value(number: Double): GhostYamlFlatWriter {
        prepareValue(isStructural = false)
        buffer.writeUtf8(number.toString())
        return this
    }

    fun value(number: Float): GhostYamlFlatWriter {
        prepareValue(isStructural = false)
        buffer.writeUtf8(number.toString())
        return this
    }

    fun value(value: Boolean): GhostYamlFlatWriter {
        prepareValue(isStructural = false)
        if (value) {
            buffer.writeUtf8(C.STR_TRUE)
        } else {
            buffer.writeUtf8(C.STR_FALSE)
        }
        return this
    }

    fun nullValue(): GhostYamlFlatWriter {
        prepareValue(isStructural = false)
        buffer.writeUtf8(STR_NULL)
        return this
    }

    @InternalGhostApi
    fun writeStringValueRaw(value: String) {
        val length = value.length
        if (length == 0) {
            buffer.write2Bytes(C.DOUBLE_QUOTE_BYTE.toInt(), C.DOUBLE_QUOTE_BYTE.toInt())
            return
        }

        val spaceInt = C.SPACE_BYTE.toInt()
        val tildeInt = C.TILDE_BYTE.toInt()
        val doubleQuoteInt = C.DOUBLE_QUOTE_BYTE.toInt()
        val backslashInt = C.BACKSLASH_BYTE.toInt()

        if (length <= PLAIN_ASCII_LIMIT) {
            var allPlain = true
            var index = 0
            while (index < length) {
                val code = value[index].code
                if (code !in spaceInt..tildeInt ||
                    code == doubleQuoteInt ||
                    code == backslashInt
                ) {
                    allPlain = false
                    break
                }
                index++
            }
            if (allPlain) {
                buffer.writeByte(doubleQuoteInt)
                buffer.writeUtf8(value)
                buffer.writeByte(doubleQuoteInt)
                return
            }
        }

        buffer.writeByte(doubleQuoteInt)
        writeEscaped(value)
        buffer.writeByte(doubleQuoteInt)
    }

    private fun writeEscaped(text: String) {
        val length = text.length
        var index = 0
        val doubleQuoteInt = C.DOUBLE_QUOTE_BYTE.toInt()
        val backslashInt = C.BACKSLASH_BYTE.toInt()

        while (index < length) {
            val charCode = text[index].code
            if (charCode == doubleQuoteInt) {
                buffer.writeByte(backslashInt)
                buffer.writeByte(doubleQuoteInt)
            } else if (charCode == backslashInt) {
                buffer.writeByte(backslashInt)
                buffer.writeByte(backslashInt)
            } else {
                when (charCode) {
                    CHAR_LF_INT -> {
                        buffer.writeByte(backslashInt)
                        buffer.writeByte(C.CHAR_N_BYTE.toInt())
                    }
                    CHAR_CR_INT -> {
                        buffer.writeByte(backslashInt)
                        buffer.writeByte(C.CHAR_R_BYTE.toInt())
                    }
                    CHAR_TAB_INT -> {
                        buffer.writeByte(backslashInt)
                        buffer.writeByte(C.CHAR_T_BYTE.toInt())
                    }
                    CHAR_BS_INT -> {
                        buffer.writeByte(backslashInt)
                        buffer.writeByte(C.CHAR_B_BYTE.toInt())
                    }
                    CHAR_FF_INT -> {
                        buffer.writeByte(backslashInt)
                        buffer.writeByte(C.CHAR_F_BYTE.toInt())
                    }
                    else -> {
                        if (charCode < CHAR_SPACE_INT) {
                            buffer.writeByte(backslashInt)
                            buffer.writeByte(C.CHAR_U_BYTE.toInt())
                            writeUnicodeHex(charCode)
                        } else if (charCode < ASCII_LIMIT) {
                            buffer.writeByte(charCode)
                        } else {
                            val charVal = text[index]
                            if (charVal.isHighSurrogate() && index + 1 < length && text[index + 1].isLowSurrogate()) {
                                buffer.writeUtf8(text, index, index + 2)
                                index++
                            } else {
                                buffer.writeUtf8(text, index, index + 1)
                            }
                        }
                    }
                }
            }
            index++
        }
    }

    private fun writeUnicodeHex(code: Int) {
        val hexChars = HEX_CHARS
        buffer.writeByte(hexChars[(code shr SHIFT_12) and HEX_MASK].toInt())
        buffer.writeByte(hexChars[(code shr SHIFT_8) and HEX_MASK].toInt())
        buffer.writeByte(hexChars[(code shr SHIFT_4) and HEX_MASK].toInt())
        buffer.writeByte(hexChars[code and HEX_MASK].toInt())
    }

    private fun writeLong(value: Long) {
        if (value == 0L) {
            buffer.writeByte(C.ZERO_BYTE.toInt())
            return
        }
        var temp = value
        val isNegative = temp < 0
        if (isNegative) {
            buffer.writeByte(C.DASH_BYTE.toInt())
            if (temp == Long.MIN_VALUE) {
                buffer.writeUtf8(STR_MIN_LONG_ABS)
                return
            }
            temp = -temp
        }
        val scratchBuf = scratch ?: acquireScratch()
        var pos = scratchBuf.size
        while (temp > 0L) {
            val digit = (temp % TEN_LONG).toInt()
            scratchBuf[--pos] = (C.ZERO_BYTE.toInt() + digit).toByte()
            temp /= TEN_LONG
        }
        buffer.write(scratchBuf, pos, scratchBuf.size - pos)
    }
}

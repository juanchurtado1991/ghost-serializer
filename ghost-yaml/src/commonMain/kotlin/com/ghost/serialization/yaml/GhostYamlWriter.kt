package com.ghost.serialization.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.releaseScratchBuffer
import com.ghost.serialization.yaml.GhostYamlConstants as C
import okio.BufferedSink
import okio.ByteString

/**
 * A highly optimized, low-allocation YAML writer for Kotlin Multiplatform.
 */
class GhostYamlWriter(
    internal val sink: BufferedSink
) {
    @PublishedApi
    internal val buffer = sink.buffer

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

        private const val SPACE_INT = 0x20
        private const val DASH_INT = 0x2D
        private const val NEWLINE_INT = 0x0A
        private const val DOUBLE_QUOTE_INT = 0x22
        private const val BACKSLASH_INT = 0x5C
        private const val COLON_INT = 0x3A
        private const val ZERO_INT = 0x30
        private const val TILDE_INT = 0x7E

        private const val CHAR_LF_INT = 10
        private const val CHAR_CR_INT = 13
        private const val CHAR_TAB_INT = 9
        private const val CHAR_BS_INT = 8
        private const val CHAR_FF_INT = 12
        private const val CHAR_SPACE_INT = 32

        private const val CHAR_N_INT = 0x6E
        private const val CHAR_R_INT = 0x72
        private const val CHAR_T_INT = 0x74
        private const val CHAR_B_INT = 0x62
        private const val CHAR_F_INT = 0x66
        private const val CHAR_U_INT = 0x75
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

    @InternalGhostApi
    fun flush() {
        sink.emit()
    }

    private fun writeIndentation(level: Int) {
        val spacesCount = level * SPACES_PER_LEVEL
        var count = 0
        while (count < spacesCount) {
            buffer.writeByte(SPACE_INT)
            count++
        }
    }

    private fun prepareValue(isStructural: Boolean) {
        val currentDepth = depth
        if (currentDepth > 0 && contexts[currentDepth] == TYPE_ARRAY) {
            if (justWroteDash) {
                buffer.writeByte(DASH_INT)
                buffer.writeByte(SPACE_INT)
            } else {
                buffer.writeByte(NEWLINE_INT)
                writeIndentation(currentDepth - 1)
                buffer.writeByte(DASH_INT)
                buffer.writeByte(SPACE_INT)
            }
            itemCounts[currentDepth]++
            justWroteDash = isStructural
        } else {
            if (isStructural) {
                pendingSpace = false
            } else {
                if (pendingSpace) {
                    buffer.writeByte(SPACE_INT)
                    pendingSpace = false
                }
            }
        }
    }

    fun beginObject(): GhostYamlWriter {
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

    fun endObject(): GhostYamlWriter {
        depth--
        justWroteDash = false
        return this
    }

    fun beginArray(): GhostYamlWriter {
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

    fun endArray(): GhostYamlWriter {
        depth--
        justWroteDash = false
        return this
    }

    fun name(key: String): GhostYamlWriter {
        val currentDepth = depth
        if (currentDepth <= 0) {
            throw GhostYamlException("Cannot write name outside of object scope")
        }
        if (justWroteDash) {
            justWroteDash = false
        } else {
            val count = itemCounts[currentDepth]
            if (count > 0) {
                buffer.writeByte(NEWLINE_INT)
                writeIndentation(currentDepth - 1)
            } else if (currentDepth > 1) {
                buffer.writeByte(NEWLINE_INT)
                writeIndentation(currentDepth - 1)
            }
        }
        buffer.writeUtf8(key)
        buffer.writeByte(COLON_INT)
        itemCounts[currentDepth]++
        pendingSpace = true
        return this
    }

    fun name(key: ByteString): GhostYamlWriter {
        val currentDepth = depth
        if (currentDepth <= 0) {
            throw GhostYamlException("Cannot write name outside of object scope")
        }
        if (justWroteDash) {
            justWroteDash = false
        } else {
            val count = itemCounts[currentDepth]
            if (count > 0) {
                buffer.writeByte(NEWLINE_INT)
                writeIndentation(currentDepth - 1)
            } else if (currentDepth > 1) {
                buffer.writeByte(NEWLINE_INT)
                writeIndentation(currentDepth - 1)
            }
        }
        buffer.write(key)
        itemCounts[currentDepth]++
        pendingSpace = false
        return this
    }

    fun value(text: String): GhostYamlWriter {
        prepareValue(isStructural = false)
        writeStringValueRaw(text)
        return this
    }

    fun value(number: Int): GhostYamlWriter {
        prepareValue(isStructural = false)
        writeLong(number.toLong())
        return this
    }

    fun value(number: Long): GhostYamlWriter {
        prepareValue(isStructural = false)
        writeLong(number)
        return this
    }

    fun value(number: Double): GhostYamlWriter {
        prepareValue(isStructural = false)
        buffer.writeUtf8(number.toString())
        return this
    }

    fun value(number: Float): GhostYamlWriter {
        prepareValue(isStructural = false)
        buffer.writeUtf8(number.toString())
        return this
    }

    fun value(value: Boolean): GhostYamlWriter {
        prepareValue(isStructural = false)
        if (value) {
            buffer.writeUtf8(C.STR_TRUE)
        } else {
            buffer.writeUtf8(C.STR_FALSE)
        }
        return this
    }

    fun nullValue(): GhostYamlWriter {
        prepareValue(isStructural = false)
        buffer.writeUtf8(STR_NULL)
        return this
    }

    @InternalGhostApi
    fun writeStringValueRaw(value: String) {
        val length = value.length
        if (length == 0) {
            buffer.writeByte(DOUBLE_QUOTE_INT)
            buffer.writeByte(DOUBLE_QUOTE_INT)
            return
        }

        if (length <= PLAIN_ASCII_LIMIT) {
            var allPlain = true
            var index = 0
            while (index < length) {
                val code = value[index].code
                if (code !in SPACE_INT..TILDE_INT ||
                    code == DOUBLE_QUOTE_INT ||
                    code == BACKSLASH_INT
                ) {
                    allPlain = false
                    break
                }
                index++
            }
            if (allPlain) {
                buffer.writeByte(DOUBLE_QUOTE_INT)
                buffer.writeUtf8(value)
                buffer.writeByte(DOUBLE_QUOTE_INT)
                return
            }
        }

        buffer.writeByte(DOUBLE_QUOTE_INT)
        writeEscaped(value)
        buffer.writeByte(DOUBLE_QUOTE_INT)
    }

    private fun writeEscaped(text: String) {
        val length = text.length
        var index = 0

        while (index < length) {
            val charCode = text[index].code
            if (charCode == DOUBLE_QUOTE_INT) {
                buffer.writeByte(BACKSLASH_INT)
                buffer.writeByte(DOUBLE_QUOTE_INT)
            } else if (charCode == BACKSLASH_INT) {
                buffer.writeByte(BACKSLASH_INT)
                buffer.writeByte(BACKSLASH_INT)
            } else {
                when (charCode) {
                    CHAR_LF_INT -> {
                        buffer.writeByte(BACKSLASH_INT)
                        buffer.writeByte(CHAR_N_INT)
                    }
                    CHAR_CR_INT -> {
                        buffer.writeByte(BACKSLASH_INT)
                        buffer.writeByte(CHAR_R_INT)
                    }
                    CHAR_TAB_INT -> {
                        buffer.writeByte(BACKSLASH_INT)
                        buffer.writeByte(CHAR_T_INT)
                    }
                    CHAR_BS_INT -> {
                        buffer.writeByte(BACKSLASH_INT)
                        buffer.writeByte(CHAR_B_INT)
                    }
                    CHAR_FF_INT -> {
                        buffer.writeByte(BACKSLASH_INT)
                        buffer.writeByte(CHAR_F_INT)
                    }
                    else -> {
                        if (charCode < CHAR_SPACE_INT) {
                            buffer.writeByte(BACKSLASH_INT)
                            buffer.writeByte(CHAR_U_INT)
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
            buffer.writeByte(ZERO_INT)
            return
        }
        var temp = value
        val isNegative = temp < 0
        if (isNegative) {
            buffer.writeByte(DASH_INT)
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
            scratchBuf[--pos] = (ZERO_INT + digit).toByte()
            temp /= TEN_LONG
        }
        buffer.write(scratchBuf, pos, scratchBuf.size - pos)
    }
}

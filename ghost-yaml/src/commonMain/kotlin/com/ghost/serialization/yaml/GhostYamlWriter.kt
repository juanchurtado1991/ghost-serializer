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
        val spacesCount = level * C.SPACES_PER_LEVEL
        var count = 0
        while (count < spacesCount) {
            buffer.writeByte(C.SPACE_INT)
            count++
        }
    }

    private fun prepareValue(isStructural: Boolean) {
        val currentDepth = depth
        if (currentDepth > 0 && contexts[currentDepth] == C.TYPE_ARRAY) {
            if (justWroteDash) {
                buffer.writeByte(C.DASH_INT)
                buffer.writeByte(C.SPACE_INT)
            } else {
                buffer.writeByte(C.NEWLINE_INT)
                writeIndentation(currentDepth - 1)
                buffer.writeByte(C.DASH_INT)
                buffer.writeByte(C.SPACE_INT)
            }
            itemCounts[currentDepth]++
            justWroteDash = isStructural
        } else {
            if (isStructural) {
                pendingSpace = false
            } else {
                if (pendingSpace) {
                    buffer.writeByte(C.SPACE_INT)
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
        contexts[nextDepth] = C.TYPE_OBJECT
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
        contexts[nextDepth] = C.TYPE_ARRAY
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
                buffer.writeByte(C.NEWLINE_INT)
                writeIndentation(currentDepth - 1)
            } else if (currentDepth > 1) {
                buffer.writeByte(C.NEWLINE_INT)
                writeIndentation(currentDepth - 1)
            }
        }
        buffer.writeUtf8(key)
        buffer.writeByte(C.COLON_INT)
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
                buffer.writeByte(C.NEWLINE_INT)
                writeIndentation(currentDepth - 1)
            } else if (currentDepth > 1) {
                buffer.writeByte(C.NEWLINE_INT)
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
        buffer.writeUtf8(C.STR_NULL)
        return this
    }

    @InternalGhostApi
    fun writeStringValueRaw(value: String) {
        val length = value.length
        if (length == 0) {
            buffer.writeByte(C.DOUBLE_QUOTE_INT)
            buffer.writeByte(C.DOUBLE_QUOTE_INT)
            return
        }

        if (length <= C.PLAIN_ASCII_LIMIT) {
            var allPlain = true
            var index = 0
            while (index < length) {
                val code = value[index].code
                if (code !in C.SPACE_INT..C.TILDE_INT ||
                    code == C.DOUBLE_QUOTE_INT ||
                    code == C.BACKSLASH_INT
                ) {
                    allPlain = false
                    break
                }
                index++
            }
            if (allPlain) {
                buffer.writeByte(C.DOUBLE_QUOTE_INT)
                buffer.writeUtf8(value)
                buffer.writeByte(C.DOUBLE_QUOTE_INT)
                return
            }
        }

        buffer.writeByte(C.DOUBLE_QUOTE_INT)
        writeEscaped(value)
        buffer.writeByte(C.DOUBLE_QUOTE_INT)
    }

    private fun writeEscaped(text: String) {
        val length = text.length
        var index = 0

        while (index < length) {
            val charCode = text[index].code
            if (charCode == C.DOUBLE_QUOTE_INT) {
                buffer.writeByte(C.BACKSLASH_INT)
                buffer.writeByte(C.DOUBLE_QUOTE_INT)
            } else if (charCode == C.BACKSLASH_INT) {
                buffer.writeByte(C.BACKSLASH_INT)
                buffer.writeByte(C.BACKSLASH_INT)
            } else {
                when (charCode) {
                    C.CHAR_LF_INT -> {
                        buffer.writeByte(C.BACKSLASH_INT)
                        buffer.writeByte(C.CHAR_N_INT)
                    }
                    C.CHAR_CR_INT -> {
                        buffer.writeByte(C.BACKSLASH_INT)
                        buffer.writeByte(C.CHAR_R_INT)
                    }
                    C.CHAR_TAB_INT -> {
                        buffer.writeByte(C.BACKSLASH_INT)
                        buffer.writeByte(C.CHAR_T_INT)
                    }
                    C.CHAR_BS_INT -> {
                        buffer.writeByte(C.BACKSLASH_INT)
                        buffer.writeByte(C.CHAR_B_INT)
                    }
                    C.CHAR_FF_INT -> {
                        buffer.writeByte(C.BACKSLASH_INT)
                        buffer.writeByte(C.CHAR_F_INT)
                    }
                    else -> {
                        if (charCode < C.CHAR_SPACE_INT) {
                            buffer.writeByte(C.BACKSLASH_INT)
                            buffer.writeByte(C.CHAR_U_INT)
                            writeUnicodeHex(charCode)
                        } else if (charCode < C.ASCII_LIMIT) {
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
        val hexChars = C.HEX_CHARS_ARR
        buffer.writeByte(hexChars[(code shr C.SHIFT_12) and C.HEX_MASK].toInt())
        buffer.writeByte(hexChars[(code shr C.SHIFT_8) and C.HEX_MASK].toInt())
        buffer.writeByte(hexChars[(code shr C.SHIFT_4) and C.HEX_MASK].toInt())
        buffer.writeByte(hexChars[code and C.HEX_MASK].toInt())
    }

    private fun writeLong(value: Long) {
        if (value == 0L) {
            buffer.writeByte(C.ZERO_INT)
            return
        }
        var temp = value
        val isNegative = temp < 0
        if (isNegative) {
            buffer.writeByte(C.DASH_INT)
            if (temp == Long.MIN_VALUE) {
                buffer.writeUtf8(C.STR_MIN_LONG_ABS)
                return
            }
            temp = -temp
        }
        val scratchBuf = scratch ?: acquireScratch()
        var pos = scratchBuf.size
        while (temp > 0L) {
            val digit = (temp % C.TEN_LONG).toInt()
            scratchBuf[--pos] = (C.ZERO_INT + digit).toByte()
            temp /= C.TEN_LONG
        }
        buffer.write(scratchBuf, pos, scratchBuf.size - pos)
    }
}

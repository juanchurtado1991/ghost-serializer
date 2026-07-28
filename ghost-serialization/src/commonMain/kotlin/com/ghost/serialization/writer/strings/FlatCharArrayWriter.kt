package com.ghost.serialization.writer.strings

import com.ghost.serialization.writer.common.*
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.GhostJsonConstants.BUFFER_SCALE_FACTOR
import com.ghost.serialization.parser.common.GhostJsonConstants.ERR_CAPACITY_OVERFLOW_PREFIX
import com.ghost.serialization.parser.common.GhostJsonConstants.INITIAL_WRITE_BUFFER_SIZE
import com.ghost.serialization.parser.common.GhostJsonConstants.STRING_QUOTE_PAIR_BYTES
import com.ghost.serialization.parser.common.GhostJsonConstants.CHAR_QUOTE
import com.ghost.serialization.parser.common.GhostJsonConstants.CHAR_T
import com.ghost.serialization.parser.common.GhostJsonConstants.CHAR_R
import com.ghost.serialization.parser.common.GhostJsonConstants.CHAR_U
import com.ghost.serialization.parser.common.GhostJsonConstants.CHAR_E
import com.ghost.serialization.parser.common.GhostJsonConstants.CHAR_F
import com.ghost.serialization.parser.common.GhostJsonConstants.CHAR_A
import com.ghost.serialization.parser.common.GhostJsonConstants.CHAR_L
import com.ghost.serialization.parser.common.GhostJsonConstants.CHAR_S
import com.ghost.serialization.parser.common.GhostJsonConstants.CHAR_N
import com.ghost.serialization.parser.common.GhostJsonConstants.CHAR_DOT
import com.ghost.serialization.parser.common.GhostJsonConstants.CHAR_ZERO
import com.ghost.serialization.parser.common.GhostJsonConstants.CAPACITY_GROWTH_SHIFT
import com.ghost.serialization.parser.common.GhostJsonConstants as C
import com.ghost.serialization.parser.common.GhostHeuristics.maxWarmCharWriteBufferCapacity
import okio.ByteString

/**
 * A growing flat-array character buffer used as the in-memory output target for
 * [GhostJsonStringWriter]. Concrete final class with no interface or superclass
 * to allow direct JIT/AOT inlining.
 */
@InternalGhostApi
class FlatCharArrayWriter(private val initialCapacity: Int = INITIAL_WRITE_BUFFER_SIZE) {

    var array: CharArray = CharArray(initialCapacity)
        private set

    var size: Int = 0
        private set

    private fun ensureCapacity(extraChars: Int) {
        val requiredCapacity = size + extraChars
        if (requiredCapacity < 0) {
            throw IllegalStateException(ERR_CAPACITY_OVERFLOW_PREFIX + "size=$size, extraChars=$extraChars")
        }
        if (requiredCapacity > array.size) {
            var newCapacity = array.size
            if (newCapacity == 0) {
                newCapacity = INITIAL_WRITE_BUFFER_SIZE
            }
            while (newCapacity < requiredCapacity) {
                val nextCapacity = newCapacity + (newCapacity shr CAPACITY_GROWTH_SHIFT)
                if (nextCapacity < newCapacity) {
                    newCapacity = Int.MAX_VALUE
                    break
                }
                newCapacity = nextCapacity
            }
            if (newCapacity < requiredCapacity) {
                newCapacity = requiredCapacity
            }
            array = array.copyOf(newCapacity)
        }
    }

    /** Appends a single character. */
    fun writeChar(charAsInt: Int) {
        val currentSize = size
        val backingArray = array
        if (currentSize < backingArray.size) {
            backingArray[currentSize] = charAsInt.toChar()
            size = currentSize + 1
        } else {
            growAndWrite(charAsInt)
        }
    }

    private fun growAndWrite(charAsInt: Int) {
        ensureCapacity(1)
        array[size++] = charAsInt.toChar()
    }

    /** Appends exactly two characters. */
    fun write2Chars(firstChar: Int, secondChar: Int) {
        val currentSize = size
        val backingArray = array
        if (currentSize + 1 < backingArray.size) {
            backingArray[currentSize] = firstChar.toChar()
            backingArray[currentSize + 1] = secondChar.toChar()
            size = currentSize + 2
        } else {
            ensureCapacity(2)
            val updatedArray = array
            updatedArray[currentSize] = firstChar.toChar()
            updatedArray[currentSize + 1] = secondChar.toChar()
            size = currentSize + 2
        }
    }

    /**
     * Writes `"…"` for a string the caller already verified needs no JSON escapes.
     * On the char channel that includes BMP/non-ASCII content (copied as UTF-16 code units).
     */
    fun writeQuotedAscii(text: String, length: Int) {
        ensureCapacity(length + STRING_QUOTE_PAIR_BYTES)
        val backingArray = array
        val writeIndex = size
        backingArray[writeIndex] = CHAR_QUOTE
        text.copyRangeToCharArray(backingArray, writeIndex + 1, 0, length)
        backingArray[writeIndex + 1 + length] = CHAR_QUOTE
        size = writeIndex + length + STRING_QUOTE_PAIR_BYTES
    }

    fun write(chars: CharArray) {
        ensureCapacity(chars.size)
        chars.copyInto(array, size)
        size += chars.size
    }

    fun write(chars: CharArray, offset: Int, length: Int) {
        ensureCapacity(length)
        chars.copyInto(
            array,
            size,
            offset,
            offset + length
        )
        size += length
    }

    fun writeString(text: String) {
        writeString(text, 0, text.length)
    }

    fun writeString(text: String, beginIndex: Int, endIndex: Int) {
        val length = endIndex - beginIndex
        ensureCapacity(length)
        text.copyRangeToCharArray(array, size, beginIndex, endIndex)
        size += length
    }

    /** Decodes a UTF-8 byte range directly into the char buffer without an intermediate [String]. */
    fun appendUtf8(bytes: ByteArray, offset: Int, length: Int) {
        if (length <= 0) {
            return
        }
        ensureCapacity(length)
        val backingArray = array
        var writeIndex = size
        var readIndex = offset
        val end = offset + length
        while (readIndex < end) {
            val leadByte = bytes[readIndex].toInt() and C.BYTE_MASK
            when {
                leadByte <= C.UTF8_1BYTE_MAX -> {
                    backingArray[writeIndex++] = leadByte.toChar()
                    readIndex++
                }
                (leadByte shr C.UTF8_2BYTE_LEAD_SHIFT) == C.UTF8_2BYTE_LEAD_TAG -> {
                    val contByte1 = bytes[readIndex + 1].toInt() and C.BYTE_MASK
                    val codePoint = ((leadByte and C.UTF8_2BYTE_PAYLOAD_MASK) shl C.UTF8_SHIFT_6) or
                        (contByte1 and C.UTF8_CONT_MASK)
                    backingArray[writeIndex++] = codePoint.toChar()
                    readIndex += C.UTF8_2BYTE_SIZE
                }
                (leadByte shr C.UTF8_3BYTE_LEAD_SHIFT) == C.UTF8_3BYTE_LEAD_TAG -> {
                    val contByte1 = bytes[readIndex + 1].toInt() and C.BYTE_MASK
                    val contByte2 = bytes[readIndex + 2].toInt() and C.BYTE_MASK
                    val codePoint = ((leadByte and C.UTF8_3BYTE_PAYLOAD_MASK) shl C.UTF8_SHIFT_12) or
                        ((contByte1 and C.UTF8_CONT_MASK) shl C.UTF8_SHIFT_6) or
                        (contByte2 and C.UTF8_CONT_MASK)
                    backingArray[writeIndex++] = codePoint.toChar()
                    readIndex += C.UTF8_3BYTE_SIZE
                }
                else -> {
                    val contByte1 = bytes[readIndex + 1].toInt() and C.BYTE_MASK
                    val contByte2 = bytes[readIndex + 2].toInt() and C.BYTE_MASK
                    val contByte3 = bytes[readIndex + 3].toInt() and C.BYTE_MASK
                    val codePoint = ((leadByte and C.UTF8_4BYTE_PAYLOAD_MASK) shl C.UTF8_SHIFT_18) or
                        ((contByte1 and C.UTF8_CONT_MASK) shl C.UTF8_SHIFT_12) or
                        ((contByte2 and C.UTF8_CONT_MASK) shl C.UTF8_SHIFT_6) or
                        (contByte3 and C.UTF8_CONT_MASK)
                    if (writeIndex + 1 >= backingArray.size) {
                        size = writeIndex
                        ensureCapacity(C.UTF8_2BYTE_SIZE)
                    }
                    val updatedArray = array
                    val planeOffset = codePoint - C.UNICODE_BASE
                    val highSurrogate = (planeOffset shr C.SHIFT_10) + C.HIGH_SURROGATE_START
                    val lowSurrogate = (planeOffset and C.SURROGATE_PAIR_MASK) + C.LOW_SURROGATE_START
                    updatedArray[writeIndex++] = highSurrogate.toChar()
                    updatedArray[writeIndex++] = lowSurrogate.toChar()
                    readIndex += C.UTF8_4BYTE_SIZE
                }
            }
        }
        size = writeIndex
    }

    /** Writes a ByteString interpreting its bytes directly as ASCII chars. */
    fun writeAscii(byteString: ByteString) {
        val str = byteString.utf8()
        val length = str.length
        ensureCapacity(length)
        str.copyRangeToCharArray(array, size, 0, length)
        size += length
    }

    /** Writes the literal "true". */
    fun writeTrue() {
        ensureCapacity(4)
        val backingArray = array
        var writeIndex = size
        backingArray[writeIndex++] = CHAR_T
        backingArray[writeIndex++] = CHAR_R
        backingArray[writeIndex++] = CHAR_U
        backingArray[writeIndex++] = CHAR_E
        size = writeIndex
    }

    /** Writes the literal "false". */
    fun writeFalse() {
        ensureCapacity(5)
        val backingArray = array
        var writeIndex = size
        backingArray[writeIndex++] = CHAR_F
        backingArray[writeIndex++] = CHAR_A
        backingArray[writeIndex++] = CHAR_L
        backingArray[writeIndex++] = CHAR_S
        backingArray[writeIndex++] = CHAR_E
        size = writeIndex
    }

    /** Writes the literal "null". */
    fun writeNull() {
        ensureCapacity(4)
        val backingArray = array
        var writeIndex = size
        backingArray[writeIndex++] = CHAR_N
        backingArray[writeIndex++] = CHAR_U
        backingArray[writeIndex++] = CHAR_L
        backingArray[writeIndex++] = CHAR_L
        size = writeIndex
    }

    /** Writes the literal ".0". */
    fun writeDotZero() {
        ensureCapacity(2)
        val backingArray = array
        var writeIndex = size
        backingArray[writeIndex++] = CHAR_DOT
        backingArray[writeIndex++] = CHAR_ZERO
        size = writeIndex
    }

    fun reset() {
        size = 0
        contractCapacity()
    }

    private fun contractCapacity() {
        if (array.size > maxWarmCharWriteBufferCapacity) {
            array = CharArray(initialCapacity)
        }
    }

    fun toCharArray(): CharArray = array.copyOf(size)

    override fun toString(): String {
        return array.concatToString(0, size)
    }
}

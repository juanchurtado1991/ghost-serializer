package com.ghost.serialization.writer

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.GhostJsonConstants.BUFFER_SCALE_FACTOR
import com.ghost.serialization.parser.GhostJsonConstants.ERR_CAPACITY_OVERFLOW_PREFIX
import com.ghost.serialization.parser.GhostJsonConstants.INITIAL_WRITE_BUFFER_SIZE
import com.ghost.serialization.parser.GhostJsonConstants.STRING_QUOTE_PAIR_BYTES
import com.ghost.serialization.parser.GhostJsonConstants.CHAR_QUOTE
import com.ghost.serialization.parser.GhostJsonConstants.CHAR_T
import com.ghost.serialization.parser.GhostJsonConstants.CHAR_R
import com.ghost.serialization.parser.GhostJsonConstants.CHAR_U
import com.ghost.serialization.parser.GhostJsonConstants.CHAR_E
import com.ghost.serialization.parser.GhostJsonConstants.CHAR_F
import com.ghost.serialization.parser.GhostJsonConstants.CHAR_A
import com.ghost.serialization.parser.GhostJsonConstants.CHAR_L
import com.ghost.serialization.parser.GhostJsonConstants.CHAR_S
import com.ghost.serialization.parser.GhostJsonConstants.CHAR_N
import com.ghost.serialization.parser.GhostJsonConstants.CHAR_DOT
import com.ghost.serialization.parser.GhostJsonConstants.CHAR_ZERO
import com.ghost.serialization.parser.GhostJsonConstants.CAPACITY_GROWTH_SHIFT
import com.ghost.serialization.parser.GhostHeuristics.maxWarmCharWriteBufferCapacity
import okio.ByteString

/**
 * A growing flat-array character buffer used as the in-memory output target for
 * [GhostJsonStringWriter]. Concrete final class with no interface or superclass
 * to allow direct JIT/AOT inlining.
 */
@InternalGhostApi
class FlatCharArrayWriter(private val initialCapacity: Int = INITIAL_WRITE_BUFFER_SIZE / 8) {

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

    /** Writes a plain-ASCII string directly with minimal checks. */
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

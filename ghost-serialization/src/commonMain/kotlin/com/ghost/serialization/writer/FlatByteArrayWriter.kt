package com.ghost.serialization.writer

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.GhostJsonConstants as C
import com.ghost.serialization.parser.GhostJsonConstants.BUFFER_SCALE_FACTOR
import com.ghost.serialization.parser.GhostJsonConstants.HIGH_SURROGATE_END
import com.ghost.serialization.parser.GhostJsonConstants.HIGH_SURROGATE_START
import com.ghost.serialization.parser.GhostJsonConstants.INITIAL_WRITE_BUFFER_SIZE
import com.ghost.serialization.parser.GhostHeuristics
import com.ghost.serialization.parser.GhostJsonConstants.LOW_SURROGATE_END
import com.ghost.serialization.parser.GhostJsonConstants.LOW_SURROGATE_START
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_10
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_12
import com.ghost.serialization.parser.GhostJsonConstants.UNICODE_BASE
import com.ghost.serialization.parser.GhostJsonConstants.UTF8_1BYTE_LIMIT
import com.ghost.serialization.parser.GhostJsonConstants.UTF8_2BYTE_LIMIT
import com.ghost.serialization.parser.GhostJsonConstants.UTF8_2BYTE_PREFIX
import com.ghost.serialization.parser.GhostJsonConstants.UTF8_3BYTE_PREFIX
import com.ghost.serialization.parser.GhostJsonConstants.UTF8_4BYTE_PREFIX
import com.ghost.serialization.parser.GhostJsonConstants.UTF8_CONT_MASK
import com.ghost.serialization.parser.GhostJsonConstants.UTF8_CONT_PREFIX
import com.ghost.serialization.parser.GhostJsonConstants.UTF8_MAX_BMP_BYTES
import com.ghost.serialization.parser.GhostJsonConstants.UTF8_REPLACEMENT_CHAR
import com.ghost.serialization.parser.GhostJsonConstants.UTF8_SHIFT_18
import com.ghost.serialization.parser.GhostJsonConstants.UTF8_SHIFT_6
import com.ghost.serialization.parser.GhostJsonConstants.CAPACITY_GROWTH_SHIFT
import okio.ByteString

/**
 * A growing flat-array byte buffer used as the in-memory output target for
 * [GhostJsonFlatWriter]. It is intentionally a `final` concrete class with no
 * interface or superclass so every byte-write made by [GhostJsonFlatWriter]
 * resolves as a monomorphic call the JIT (HotSpot/ART) and Kotlin/Native AOT
 * compiler can fully inline — no v-table lookup, no segment management, just
 * direct array stores.
 *
 * Compared to the streaming path that writes through [okio.Buffer]/segments,
 * this class trades the ability to incrementally drain bytes for ~2-3x
 * faster small-encode throughput. The full encoded payload is exposed at
 * the end of the encode via [toByteArray] / [toStringUtf8] (or accessed
 * directly via [array] + [size] for zero-copy fast paths).
 */
@InternalGhostApi
class FlatByteArrayWriter(private val initialCapacity: Int = INITIAL_WRITE_BUFFER_SIZE) {

    /**
     * Backing store. The slice `array[0 until size]` is the live encoded
     * payload; everything past `size` is uninitialized / stale capacity.
     */
    var array: ByteArray = ByteArray(initialCapacity)
        private set

    /** Number of bytes currently written into [array]. */
    var size: Int = 0
        private set

    /**
     * Grows [array] until at least `extraBytes` more bytes can fit past
     * the current [size]. Capacity grows geometrically by [BUFFER_SCALE_FACTOR]
     * to amortize copy cost over many writes.
     */
    private fun ensureCapacity(extraBytes: Int) {
        val requiredCapacity = size + extraBytes
        if (requiredCapacity < 0) {
            throw IllegalStateException(C.ERR_CAPACITY_OVERFLOW_PREFIX + "size=$size, extraBytes=$extraBytes")
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

    /** Appends a single byte (low 8 bits of [byteAsInt]). Optimized for inlining. */
    fun writeByte(byteAsInt: Int) {
        val currentSize = size
        val backingArray = array
        if (currentSize < backingArray.size) {
            backingArray[currentSize] = byteAsInt.toByte()
            size = currentSize + 1
        } else {
            growAndWrite(byteAsInt)
        }
    }

    private fun growAndWrite(byteAsInt: Int) {
        ensureCapacity(1)
        array[size++] = byteAsInt.toByte()
    }

    /**
     * Appends exactly two bytes in a single bounds-check.
     * Use instead of two consecutive [writeByte] calls whenever both bytes
     * are known at the call site (e.g. opening + closing quotes, escape pairs).
     */
    fun write2Bytes(firstByte: Int, secondByte: Int) {
        val currentSize = size
        val backingArray = array
        if (currentSize + 1 < backingArray.size) {
            backingArray[currentSize] = firstByte.toByte()
            backingArray[currentSize + 1] = secondByte.toByte()
            size = currentSize + 2
        } else {
            ensureCapacity(2)
            val updatedArray = array
            updatedArray[currentSize] = firstByte.toByte()
            updatedArray[currentSize + 1] = secondByte.toByte()
            size = currentSize + 2
        }
    }

    /**
     * Writes `"text"` directly into [array] for a caller-verified plain-ASCII
     * string (all chars in [0x20, 0x7E] with no `"` or `\`).
     *
     * Avoids the scratch-buffer accumulation used by the generic escape path,
     * saving one intermediate copy + thread-local acquire for the common case.
     */
    fun writeQuotedAscii(text: String, length: Int) {
        ensureCapacity(length + C.STRING_QUOTE_PAIR_BYTES)
        val backingArray = array
        var writeIndex = size
        backingArray[writeIndex++] = C.QUOTE_INT.toByte()
        var charIndex = 0
        // Unrolled x4 for instruction-level parallelism
        while (charIndex + 3 < length) {
            backingArray[writeIndex] = text[charIndex].code.toByte()
            backingArray[writeIndex + 1] = text[charIndex + 1].code.toByte()
            backingArray[writeIndex + 2] = text[charIndex + 2].code.toByte()
            backingArray[writeIndex + 3] = text[charIndex + 3].code.toByte()
            writeIndex += 4
            charIndex += 4
        }
        while (charIndex < length) {
            backingArray[writeIndex++] = text[charIndex].code.toByte()
            charIndex++
        }
        backingArray[writeIndex++] = C.QUOTE_INT.toByte()
        size = writeIndex
    }

    /** Appends every byte from [bytes] to the live payload. */
    fun write(bytes: ByteArray) {
        ensureCapacity(bytes.size)
        bytes.copyInto(array, size)
        size += bytes.size
    }

    /** Appends `bytes[offset until offset + length]` to the live payload. */
    fun write(bytes: ByteArray, offset: Int, length: Int) {
        ensureCapacity(length)
        bytes.copyInto(
            array,
            size,
            offset,
            offset + length
        )
        size += length
    }

    /** Appends every byte of the immutable [byteString] to the live payload. */
    fun write(byteString: ByteString) {
        val length = byteString.size
        ensureCapacity(length)
        byteString.copyInto(
            0,
            array,
            size,
            length
        )
        size += length
    }

    /** UTF-8 encodes the entire string [text] directly into the payload. */
    fun writeUtf8(text: String) {
        writeUtf8(text, 0, text.length)
    }

    /**
     * UTF-8 encodes `text[beginIndex until endIndex)` directly into [array].
     *
     * Hand-rolled to avoid the intermediate `String.toByteArray()` copy that
     * `okio.Buffer.writeUtf8` would otherwise allocate. The ASCII fast-path
     * (BMP code points below [UTF8_1BYTE_LIMIT]) collapses to a single
     * array store per character. Surrogate pairs are decoded into a 4-byte
     * sequence; lone surrogates emit [UTF8_REPLACEMENT_CHAR].
     */
    fun writeUtf8(text: String, beginIndex: Int, endIndex: Int) {
        val count = endIndex - beginIndex
        ensureCapacity(count * UTF8_MAX_BMP_BYTES)
        var sourceIndex = beginIndex
        var writeIndex = size
        val backingArray = array

        // ASCII Fast-Path with unrolling
        while (sourceIndex + 3 < endIndex) {
            val charCode0 = text[sourceIndex].code
            val charCode1 = text[sourceIndex + 1].code
            val charCode2 = text[sourceIndex + 2].code
            val charCode3 = text[sourceIndex + 3].code

            if ((charCode0 or charCode1 or charCode2 or charCode3) < UTF8_1BYTE_LIMIT) {
                backingArray[writeIndex] = charCode0.toByte()
                backingArray[writeIndex + 1] = charCode1.toByte()
                backingArray[writeIndex + 2] = charCode2.toByte()
                backingArray[writeIndex + 3] = charCode3.toByte()
                sourceIndex += 4
                writeIndex += 4
            } else {
                break
            }
        }

        while (sourceIndex < endIndex) {
            val codePoint = text[sourceIndex].code
            when {
                codePoint < UTF8_1BYTE_LIMIT -> {
                    backingArray[writeIndex++] = codePoint.toByte()
                }

                codePoint < UTF8_2BYTE_LIMIT -> {
                    backingArray[writeIndex++] =
                        (UTF8_2BYTE_PREFIX or (codePoint shr UTF8_SHIFT_6)).toByte()
                    backingArray[writeIndex++] =
                        (UTF8_CONT_PREFIX or (codePoint and UTF8_CONT_MASK)).toByte()
                }

                codePoint !in HIGH_SURROGATE_START..LOW_SURROGATE_END -> {
                    backingArray[writeIndex++] =
                        (UTF8_3BYTE_PREFIX or (codePoint shr SHIFT_12)).toByte()
                    backingArray[writeIndex++] =
                        (UTF8_CONT_PREFIX or ((codePoint shr UTF8_SHIFT_6) and UTF8_CONT_MASK)).toByte()
                    backingArray[writeIndex++] =
                        (UTF8_CONT_PREFIX or (codePoint and UTF8_CONT_MASK)).toByte()
                }

                codePoint <= HIGH_SURROGATE_END && sourceIndex + 1 < endIndex -> {
                    val lowSurrogate = text[sourceIndex + 1].code
                    if (lowSurrogate in LOW_SURROGATE_START..LOW_SURROGATE_END) {
                        val fullCodePoint = UNICODE_BASE +
                            (((codePoint - HIGH_SURROGATE_START) shl SHIFT_10) or
                                (lowSurrogate - LOW_SURROGATE_START))
                        backingArray[writeIndex++] =
                            (UTF8_4BYTE_PREFIX or (fullCodePoint shr UTF8_SHIFT_18)).toByte()
                        backingArray[writeIndex++] =
                            (UTF8_CONT_PREFIX or ((fullCodePoint shr SHIFT_12) and UTF8_CONT_MASK)).toByte()
                        backingArray[writeIndex++] =
                            (UTF8_CONT_PREFIX or ((fullCodePoint shr UTF8_SHIFT_6) and UTF8_CONT_MASK)).toByte()
                        backingArray[writeIndex++] =
                            (UTF8_CONT_PREFIX or (fullCodePoint and UTF8_CONT_MASK)).toByte()
                        sourceIndex++
                    } else {
                        UTF8_REPLACEMENT_CHAR.copyInto(backingArray, writeIndex)
                        writeIndex += UTF8_REPLACEMENT_CHAR.size
                    }
                }

                else -> {
                    UTF8_REPLACEMENT_CHAR.copyInto(backingArray, writeIndex)
                    writeIndex += UTF8_REPLACEMENT_CHAR.size
                }
            }
            sourceIndex++
        }
        size = writeIndex
    }

    /** Writes the literal "true" directly. */
    fun writeTrue() {
        ensureCapacity(4)
        val backingArray = array
        var writeIndex = size
        backingArray[writeIndex++] = C.T_BYTE_INT.toByte()
        backingArray[writeIndex++] = C.R_BYTE_INT.toByte()
        backingArray[writeIndex++] = C.U_BYTE_INT.toByte()
        backingArray[writeIndex++] = C.E_BYTE_INT.toByte()
        size = writeIndex
    }

    /** Writes the literal "false" directly. */
    fun writeFalse() {
        ensureCapacity(5)
        val backingArray = array
        var writeIndex = size
        backingArray[writeIndex++] = C.F_BYTE_INT.toByte()
        backingArray[writeIndex++] = C.A_BYTE_INT.toByte()
        backingArray[writeIndex++] = C.L_BYTE_INT.toByte()
        backingArray[writeIndex++] = C.S_BYTE_INT.toByte()
        backingArray[writeIndex++] = C.E_BYTE_INT.toByte()
        size = writeIndex
    }

    /** Writes the literal "null" directly. */
    fun writeNull() {
        ensureCapacity(4)
        val backingArray = array
        var writeIndex = size
        backingArray[writeIndex++] = C.N_BYTE_INT.toByte()
        backingArray[writeIndex++] = C.U_BYTE_INT.toByte()
        backingArray[writeIndex++] = C.L_BYTE_INT.toByte()
        backingArray[writeIndex++] = C.L_BYTE_INT.toByte()
        size = writeIndex
    }

    /** Writes the literal ".0" directly. */
    fun writeDotZero() {
        ensureCapacity(2)
        val backingArray = array
        var writeIndex = size
        backingArray[writeIndex++] = C.DOT_INT.toByte()
        backingArray[writeIndex++] = C.ZERO_INT.toByte()
        size = writeIndex
    }

    /**
     * Marks the buffer as empty without releasing capacity, so the next
     * encode reuses the already-grown [array].
     */
    fun reset() {
        size = 0
        contractCapacity()
    }

    private fun contractCapacity() {
        if (array.size > GhostHeuristics.maxWarmWriteBufferCapacity) {
            array = ByteArray(initialCapacity)
        }
    }

    /** Returns a fresh [ByteArray] containing exactly the encoded payload. */
    fun toByteArray(): ByteArray = array.copyOf(size)

    /** Decodes the encoded payload back into a [String] (UTF-8). */
    fun toStringUtf8(): String = array.decodeToString(0, size)
}

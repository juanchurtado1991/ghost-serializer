package com.ghost.serialization.writer

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.GhostJsonConstants as C
import com.ghost.serialization.parser.GhostJsonConstants.BUFFER_SCALE_FACTOR
import com.ghost.serialization.parser.GhostJsonConstants.HIGH_SURROGATE_END
import com.ghost.serialization.parser.GhostJsonConstants.HIGH_SURROGATE_START
import com.ghost.serialization.parser.GhostJsonConstants.INITIAL_WRITE_BUFFER_SIZE
import com.ghost.serialization.parser.GhostJsonConstants.MAX_WARM_BUFFER_SIZE
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
        if (requiredCapacity > array.size) {
            var newCapacity = array.size
            while (newCapacity < requiredCapacity) {
                newCapacity *= BUFFER_SCALE_FACTOR
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
    fun write2Bytes(b1: Int, b2: Int) {
        val s = size
        val arr = array
        if (s + 1 < arr.size) {
            arr[s] = b1.toByte()
            arr[s + 1] = b2.toByte()
            size = s + 2
        } else {
            ensureCapacity(2)
            val arr2 = array
            arr2[s] = b1.toByte()
            arr2[s + 1] = b2.toByte()
            size = s + 2
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
        val arr = array
        var w = size
        arr[w++] = C.QUOTE_INT.toByte()
        var i = 0
        // Unrolled x4 for instruction-level parallelism
        while (i + 3 < length) {
            arr[w] = text[i].code.toByte()
            arr[w + 1] = text[i + 1].code.toByte()
            arr[w + 2] = text[i + 2].code.toByte()
            arr[w + 3] = text[i + 3].code.toByte()
            w += 4
            i += 4
        }
        while (i < length) {
            arr[w++] = text[i].code.toByte()
            i++
        }
        arr[w++] = C.QUOTE_INT.toByte()
        size = w
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
            val c0 = text[sourceIndex].code
            val c1 = text[sourceIndex + 1].code
            val c2 = text[sourceIndex + 2].code
            val c3 = text[sourceIndex + 3].code

            if ((c0 or c1 or c2 or c3) < UTF8_1BYTE_LIMIT) {
                backingArray[writeIndex] = c0.toByte()
                backingArray[writeIndex + 1] = c1.toByte()
                backingArray[writeIndex + 2] = c2.toByte()
                backingArray[writeIndex + 3] = c3.toByte()
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
        var s = size
        backingArray[s++] = C.T_BYTE_INT.toByte()
        backingArray[s++] = C.R_BYTE_INT.toByte()
        backingArray[s++] = C.U_BYTE_INT.toByte()
        backingArray[s++] = C.E_BYTE_INT.toByte()
        size = s
    }

    /** Writes the literal "false" directly. */
    fun writeFalse() {
        ensureCapacity(5)
        val backingArray = array
        var s = size
        backingArray[s++] = C.F_BYTE_INT.toByte()
        backingArray[s++] = C.A_BYTE_INT.toByte()
        backingArray[s++] = C.L_BYTE_INT.toByte()
        backingArray[s++] = C.S_BYTE_INT.toByte()
        backingArray[s++] = C.E_BYTE_INT.toByte()
        size = s
    }

    /** Writes the literal "null" directly. */
    fun writeNull() {
        ensureCapacity(4)
        val backingArray = array
        var s = size
        backingArray[s++] = C.N_BYTE_INT.toByte()
        backingArray[s++] = C.U_BYTE_INT.toByte()
        backingArray[s++] = C.L_BYTE_INT.toByte()
        backingArray[s++] = C.L_BYTE_INT.toByte()
        size = s
    }

    /** Writes the literal ".0" directly. */
    fun writeDotZero() {
        ensureCapacity(2)
        val backingArray = array
        var s = size
        backingArray[s++] = C.DOT_INT.toByte()
        backingArray[s++] = C.ZERO_INT.toByte()
        size = s
    }

    /**
     * Marks the buffer as empty without releasing capacity, so the next
     * encode reuses the already-grown [array].
     */
    fun reset() {
        size = 0
        if (array.size > MAX_WARM_BUFFER_SIZE) {
            array = ByteArray(initialCapacity)
        }
    }

    /** Returns a fresh [ByteArray] containing exactly the encoded payload. */
    fun toByteArray(): ByteArray = array.copyOf(size)

    /** Decodes the encoded payload back into a [String] (UTF-8). */
    fun toStringUtf8(): String = array.decodeToString(0, size)
}

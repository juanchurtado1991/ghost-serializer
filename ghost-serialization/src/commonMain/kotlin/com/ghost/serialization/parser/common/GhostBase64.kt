@file:Suppress("AssignedValueIsNeverRead")

package com.ghost.serialization.parser.common

import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK
import com.ghost.serialization.proto.GhostProtoConstants as PC

/**
 * Decodes a standard or URL-safe Base64 string (with or without `=` padding) into raw bytes.
 *
 * Operates on an already-materialized Kotlin [String] (e.g. from `reader.nextString()`), so
 * it works from any reader flavor ([GhostJsonReader] or [GhostJsonStringReader]) — unlike a
 * scratch-buffer-pooled decoder tied to a specific byte buffer implementation.
 *
 * @throws IllegalArgumentException if [value] contains a non-alphabet, non-whitespace character.
 */
fun decodeBase64String(value: String): ByteArray {
    val lut = PC.BASE64_LUT
    val length = value.length
    val output = ByteArray(length)
    var outputPosition = 0
    val chunk = IntArray(PC.B64_PAD_MULTIPLIER)
    var chunkIndex = 0

    for (index in 0 until length) {
        val c = value[index].code
        if (c <= TOK.SPACE_INT) continue
        chunk[chunkIndex++] = c
        if (chunkIndex == PC.B64_PAD_MULTIPLIER) {
            val value0 = lookupBase64Code(lut = lut, code = chunk[0])
            val value1 = lookupBase64Code(lut = lut, code = chunk[1])
            val value2 = lookupBase64Code(lut = lut, code = chunk[2])
            val value3 = lookupBase64Code(lut = lut, code = chunk[3])

            val hasInvalidChar = value0 < 0 || value1 < 0 ||
                value2 == PC.B64_INVALID_CODE || value3 == PC.B64_INVALID_CODE
            if (hasInvalidChar) throw IllegalArgumentException(PC.ERR_INVALID_BASE64)

            output[outputPosition++] = combineFirstByte(value0 = value0, value1 = value1)
            if (value2 != PC.B64_PADDING_CODE) {
                output[outputPosition++] = combineSecondByte(value1 = value1, value2 = value2)
                if (value3 != PC.B64_PADDING_CODE) {
                    output[outputPosition++] = combineThirdByte(value2 = value2, value3 = value3)
                }
            }
            chunkIndex = 0
        }
    }

    if (chunkIndex > 0) {
        while (chunkIndex < PC.B64_PAD_MULTIPLIER) {
            chunk[chunkIndex++] = TOK.EQUALS_INT
        }
        val value0 = lookupBase64Code(lut = lut, code = chunk[0])
        val value1 = lookupBase64Code(lut = lut, code = chunk[1])
        val value2 = lookupBase64Code(lut = lut, code = chunk[2])
        if (value0 >= 0 && value1 >= 0) {
            output[outputPosition++] = combineFirstByte(value0 = value0, value1 = value1)
            if (value2 != PC.B64_PADDING_CODE && value2 >= 0) {
                output[outputPosition++] = combineSecondByte(value1 = value1, value2 = value2)
            }
        }
    }

    return output.copyOf(outputPosition)
}

/**
 * Encodes [source] into a standard Base64 string with `=` padding (RFC 4648 §4).
 */
fun encodeBase64String(source: ByteArray): String {
    if (source.isEmpty()) return ""
    val chars = PC.BASE64_ALPHABET_BYTES
    val outputLength = ((source.size + PC.B64_OFFSET_2) / PC.B64_PAD_DIVISOR) * PC.B64_PAD_MULTIPLIER
    val output = ByteArray(outputLength)
    var index = 0
    var outputIndex = 0
    val length = source.size
    val loopLimit = length - PC.B64_OFFSET_2
    while (index < loopLimit) {
        val byte0 = source[index].toInt() and PC.B64_BYTE_MASK
        val byte1 = source[index + PC.B64_OFFSET_1].toInt() and PC.B64_BYTE_MASK
        val byte2 = source[index + PC.B64_OFFSET_2].toInt() and PC.B64_BYTE_MASK
        output[outputIndex++] = firstBase64Char(chars = chars, byte0 = byte0)
        output[outputIndex++] =
            chars[((byte0 and PC.B64_MASK_2BITS) shl PC.B64_SHIFT_4) or (byte1 shr PC.B64_SHIFT_4)]
        output[outputIndex++] =
            chars[((byte1 and PC.B64_MASK_4BITS) shl PC.B64_SHIFT_2) or (byte2 shr PC.B64_SHIFT_6)]
        output[outputIndex++] = chars[byte2 and PC.B64_MASK_6BITS]
        index += PC.B64_PAD_DIVISOR
    }
    if (index < length) {
        val byte0 = source[index].toInt() and PC.B64_BYTE_MASK
        output[outputIndex++] = firstBase64Char(chars = chars, byte0 = byte0)
        if (index == length - PC.B64_OFFSET_1) {
            output[outputIndex++] = chars[(byte0 and PC.B64_MASK_2BITS) shl PC.B64_SHIFT_4]
            output[outputIndex++] = TOK.EQUALS_BYTE
            output[outputIndex++] = TOK.EQUALS_BYTE
        } else {
            val byte1 = source[index + PC.B64_OFFSET_1].toInt() and PC.B64_BYTE_MASK
            output[outputIndex++] =
                chars[((byte0 and PC.B64_MASK_2BITS) shl PC.B64_SHIFT_4) or (byte1 shr PC.B64_SHIFT_4)]
            output[outputIndex++] = chars[(byte1 and PC.B64_MASK_4BITS) shl PC.B64_SHIFT_2]
            output[outputIndex++] = TOK.EQUALS_BYTE
        }
    }
    return output.decodeToString()
}

/**
 * Chars outside the LUT's range (any non-Latin-1 code unit) are never valid base64 alphabet
 * members — bounds-check rather than indexing [lut] directly, since a raw index would throw
 * [IndexOutOfBoundsException] instead of the documented [IllegalArgumentException].
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun lookupBase64Code(lut: IntArray, code: Int): Int =
    if (code < lut.size) lut[code] else PC.B64_INVALID_CODE

@Suppress("NOTHING_TO_INLINE")
internal inline fun combineFirstByte(value0: Int, value1: Int): Byte =
    ((value0 shl PC.B64_SHIFT_2) or (value1 ushr PC.B64_SHIFT_4)).toByte()

@Suppress("NOTHING_TO_INLINE")
internal inline fun combineSecondByte(value1: Int, value2: Int): Byte =
    ((value1 shl PC.B64_SHIFT_4) or (value2 ushr PC.B64_SHIFT_2)).toByte()

@Suppress("NOTHING_TO_INLINE")
internal inline fun combineThirdByte(value2: Int, value3: Int): Byte =
    ((value2 shl PC.B64_SHIFT_6) or value3).toByte()

@Suppress("NOTHING_TO_INLINE")
private inline fun firstBase64Char(
    chars: ByteArray,
    byte0: Int
): Byte = chars[byte0 shr PC.B64_SHIFT_2]

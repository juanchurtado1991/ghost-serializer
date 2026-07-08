package com.ghost.serialization.parser

import com.ghost.serialization.parser.GhostJsonConstants as C

/**
 * Decodes a standard or URL-safe Base64 string (with or without `=` padding) into raw bytes.
 *
 * Operates on an already-materialized Kotlin [String] (e.g. from `reader.nextString()`), so
 * it works from any reader flavor (streaming [GhostJsonReader], flat [GhostJsonFlatReader],
 * [GhostJsonStringReader]) — unlike a scratch-buffer-pooled decoder tied to a specific byte
 * buffer implementation.
 *
 * @throws IllegalArgumentException if [value] contains a non-alphabet, non-whitespace character.
 */
fun decodeBase64String(value: String): ByteArray {
    val lut = C.BASE64_LUT
    val length = value.length
    val output = ByteArray(length)
    var outputPosition = 0
    val chunk = IntArray(4)
    var chunkIndex = 0

    for (index in 0 until length) {
        val c = value[index].code
        if (c <= C.SPACE_INT) continue
        chunk[chunkIndex++] = c
        if (chunkIndex == 4) {
            // Chars outside the LUT's range (any non-Latin-1 code unit) are never valid
            // base64 alphabet members — bounds-check rather than indexing lut[] directly,
            // since a raw index would throw ArrayIndexOutOfBoundsException instead of the
            // documented IllegalArgumentException.
            val value0 = if (chunk[0] < lut.size) lut[chunk[0]] else -1
            val value1 = if (chunk[1] < lut.size) lut[chunk[1]] else -1
            val value2 = if (chunk[2] < lut.size) lut[chunk[2]] else -1
            val value3 = if (chunk[3] < lut.size) lut[chunk[3]] else -1
            if (value0 < 0 || value1 < 0 || value2 == -1 || value3 == -1) {
                throw IllegalArgumentException("Invalid base64 character")
            }
            output[outputPosition++] = ((value0 shl C.B64_SHIFT_2) or (value1 ushr C.B64_SHIFT_4)).toByte()
            if (value2 != -2) {
                output[outputPosition++] = ((value1 shl C.B64_SHIFT_4) or (value2 ushr C.B64_SHIFT_2)).toByte()
                if (value3 != -2) {
                    output[outputPosition++] = ((value2 shl C.B64_SHIFT_6) or value3).toByte()
                }
            }
            chunkIndex = 0
        }
    }

    if (chunkIndex > 0) {
        while (chunkIndex < 4) {
            chunk[chunkIndex++] = '='.code
        }
        val value0 = if (chunk[0] < lut.size) lut[chunk[0]] else -1
        val value1 = if (chunk[1] < lut.size) lut[chunk[1]] else -1
        val value2 = if (chunk[2] < lut.size) lut[chunk[2]] else -1
        val value3 = if (chunk[3] < lut.size) lut[chunk[3]] else -1
        if (value0 >= 0 && value1 >= 0) {
            output[outputPosition++] = ((value0 shl C.B64_SHIFT_2) or (value1 ushr C.B64_SHIFT_4)).toByte()
            if (value2 != -2 && value2 >= 0) {
                output[outputPosition++] = ((value1 shl C.B64_SHIFT_4) or (value2 ushr C.B64_SHIFT_2)).toByte()
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
    val chars = C.BASE64_ALPHABET_BYTES
    val outputLength = ((source.size + C.B64_OFFSET_2) / C.B64_PAD_DIVISOR) * C.B64_PAD_MULTIPLIER
    val output = ByteArray(outputLength)
    var index = 0
    var outputIndex = 0
    val length = source.size
    val loopLimit = length - C.B64_OFFSET_2
    while (index < loopLimit) {
        val byte0 = source[index].toInt() and C.B64_BYTE_MASK
        val byte1 = source[index + C.B64_OFFSET_1].toInt() and C.B64_BYTE_MASK
        val byte2 = source[index + C.B64_OFFSET_2].toInt() and C.B64_BYTE_MASK
        output[outputIndex++] = chars[byte0 shr C.B64_SHIFT_2]
        output[outputIndex++] = chars[((byte0 and C.B64_MASK_2BITS) shl C.B64_SHIFT_4) or (byte1 shr C.B64_SHIFT_4)]
        output[outputIndex++] = chars[((byte1 and C.B64_MASK_4BITS) shl C.B64_SHIFT_2) or (byte2 shr C.B64_SHIFT_6)]
        output[outputIndex++] = chars[byte2 and C.B64_MASK_6BITS]
        index += C.B64_PAD_DIVISOR
    }
    if (index < length) {
        val byte0 = source[index].toInt() and C.B64_BYTE_MASK
        output[outputIndex++] = chars[byte0 shr C.B64_SHIFT_2]
        if (index == length - C.B64_OFFSET_1) {
            output[outputIndex++] = chars[(byte0 and C.B64_MASK_2BITS) shl C.B64_SHIFT_4]
            output[outputIndex++] = C.EQUALS_BYTE
            output[outputIndex++] = C.EQUALS_BYTE
        } else {
            val byte1 = source[index + C.B64_OFFSET_1].toInt() and C.B64_BYTE_MASK
            output[outputIndex++] = chars[((byte0 and C.B64_MASK_2BITS) shl C.B64_SHIFT_4) or (byte1 shr C.B64_SHIFT_4)]
            output[outputIndex++] = chars[(byte1 and C.B64_MASK_4BITS) shl C.B64_SHIFT_2]
            output[outputIndex++] = C.EQUALS_BYTE
        }
    }
    return output.decodeToString()
}

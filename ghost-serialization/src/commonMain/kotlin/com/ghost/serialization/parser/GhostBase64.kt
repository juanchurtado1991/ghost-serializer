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
    val len = value.length
    val out = ByteArray(len)
    var outPos = 0
    val chunk = IntArray(4)
    var chunkIdx = 0

    for (i in 0 until len) {
        val c = value[i].code
        if (c <= C.SPACE_INT) continue
        chunk[chunkIdx++] = c
        if (chunkIdx == 4) {
            val v0 = lut[chunk[0]]
            val v1 = lut[chunk[1]]
            val v2 = lut[chunk[2]]
            val v3 = lut[chunk[3]]
            if (v0 < 0 || v1 < 0 || v2 == -1 || v3 == -1) {
                throw IllegalArgumentException("Invalid base64 character")
            }
            out[outPos++] = ((v0 shl C.B64_SHIFT_2) or (v1 ushr C.B64_SHIFT_4)).toByte()
            if (v2 != -2) {
                out[outPos++] = ((v1 shl C.B64_SHIFT_4) or (v2 ushr C.B64_SHIFT_2)).toByte()
                if (v3 != -2) {
                    out[outPos++] = ((v2 shl C.B64_SHIFT_6) or v3).toByte()
                }
            }
            chunkIdx = 0
        }
    }

    if (chunkIdx > 0) {
        while (chunkIdx < 4) {
            chunk[chunkIdx++] = '='.code
        }
        val v0 = lut[chunk[0]]
        val v1 = lut[chunk[1]]
        val v2 = lut[chunk[2]]
        val v3 = lut[chunk[3]]
        if (v0 >= 0 && v1 >= 0) {
            out[outPos++] = ((v0 shl C.B64_SHIFT_2) or (v1 ushr C.B64_SHIFT_4)).toByte()
            if (v2 != -2 && v2 >= 0) {
                out[outPos++] = ((v1 shl C.B64_SHIFT_4) or (v2 ushr C.B64_SHIFT_2)).toByte()
            }
        }
    }

    return out.copyOf(outPos)
}

/**
 * Encodes [src] into a standard Base64 string with `=` padding (RFC 4648 §4).
 */
fun encodeBase64String(src: ByteArray): String {
    if (src.isEmpty()) return ""
    val chars = C.BASE64_ALPHABET_BYTES
    val outLen = ((src.size + C.B64_OFFSET_2) / C.B64_PAD_DIVISOR) * C.B64_PAD_MULTIPLIER
    val out = ByteArray(outLen)
    var i = 0
    var o = 0
    val len = src.size
    val limit = len - C.B64_OFFSET_2
    while (i < limit) {
        val b0 = src[i].toInt() and C.B64_BYTE_MASK
        val b1 = src[i + C.B64_OFFSET_1].toInt() and C.B64_BYTE_MASK
        val b2 = src[i + C.B64_OFFSET_2].toInt() and C.B64_BYTE_MASK
        out[o++] = chars[b0 shr C.B64_SHIFT_2]
        out[o++] = chars[((b0 and C.B64_MASK_2BITS) shl C.B64_SHIFT_4) or (b1 shr C.B64_SHIFT_4)]
        out[o++] = chars[((b1 and C.B64_MASK_4BITS) shl C.B64_SHIFT_2) or (b2 shr C.B64_SHIFT_6)]
        out[o++] = chars[b2 and C.B64_MASK_6BITS]
        i += C.B64_PAD_DIVISOR
    }
    if (i < len) {
        val b0 = src[i].toInt() and C.B64_BYTE_MASK
        out[o++] = chars[b0 shr C.B64_SHIFT_2]
        if (i == len - C.B64_OFFSET_1) {
            out[o++] = chars[(b0 and C.B64_MASK_2BITS) shl C.B64_SHIFT_4]
            out[o++] = C.EQUALS_BYTE
            out[o++] = C.EQUALS_BYTE
        } else {
            val b1 = src[i + C.B64_OFFSET_1].toInt() and C.B64_BYTE_MASK
            out[o++] = chars[((b0 and C.B64_MASK_2BITS) shl C.B64_SHIFT_4) or (b1 shr C.B64_SHIFT_4)]
            out[o++] = chars[(b1 and C.B64_MASK_4BITS) shl C.B64_SHIFT_2]
            out[o++] = C.EQUALS_BYTE
        }
    }
    return out.decodeToString()
}

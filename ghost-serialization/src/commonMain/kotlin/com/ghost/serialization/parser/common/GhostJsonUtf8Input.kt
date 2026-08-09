@file:OptIn(InternalGhostApi::class)
@file:Suppress("NOTHING_TO_INLINE")

package com.ghost.serialization.parser.common

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.GhostJsonException
import okio.Buffer
import okio.BufferedSource
import com.ghost.serialization.parser.common.GhostJsonConstants as C

/**
 * RFC 8259 §8.1: JSON text may be UTF-8, UTF-16, or UTF-32 (with or without BOM).
 * Ghost's parsers consume UTF-8 only; this layer normalizes at the byte entrypoint.
 *
 * Hot path (typical UTF-8, no BOM): a few byte comparisons and the same [ByteArray]
 * reference — no allocation and no copy.
 */
internal inline fun <T> withPreparedUtf8Json(
    bytes: ByteArray,
    limit: Int,
    block: (data: ByteArray, offset: Int, length: Int) -> T
): T {
    if (limit <= 0) return block(bytes, 0, 0)

    val b0 = bytes[0].toInt() and C.BYTE_MASK
    // Fast path: a JSON text in UTF-8 begins with an ASCII structural/value byte
    // (`{` `[` `"` whitespace, digit, `t`/`f`/`n`/`-`) that is neither a BOM lead
    // byte nor a NUL, immediately followed by a non-NUL byte. UTF-16/32 always
    // interleave NULs for these ASCII code points, so this check alone rules them
    // out with two comparisons and no allocation.
    if (b0 != UTF8_BOM_0 && b0 != UTF16_BE_BOM_0 && b0 != UTF16_LE_BOM_0 && b0 != NUL_BYTE &&
        (limit < UTF16_UNIT_SIZE || (bytes[1].toInt() and C.BYTE_MASK) != NUL_BYTE)
    ) {
        return block(bytes, 0, limit)
    }

    val detected = detectJsonByteEncoding(bytes, 0, limit)
    val payloadOffset = detected.bomSize
    val payloadLength = limit - detected.bomSize
    return when (detected.kind) {
        JsonEncodingKind.UTF8 -> block(bytes, payloadOffset, payloadLength)
        JsonEncodingKind.UTF16_LE -> {
            val utf8 = utf16ToUtf8(bytes, payloadOffset, payloadLength, littleEndian = true)
            block(utf8, 0, utf8.size)
        }

        JsonEncodingKind.UTF16_BE -> {
            val utf8 = utf16ToUtf8(bytes, payloadOffset, payloadLength, littleEndian = false)
            block(utf8, 0, utf8.size)
        }

        JsonEncodingKind.UTF32_LE -> {
            val utf8 = utf32ToUtf8(bytes, payloadOffset, payloadLength, littleEndian = true)
            block(utf8, 0, utf8.size)
        }

        JsonEncodingKind.UTF32_BE -> {
            val utf8 = utf32ToUtf8(bytes, payloadOffset, payloadLength, littleEndian = false)
            block(utf8, 0, utf8.size)
        }
    }
}

/**
 * Ensures a streaming source yields UTF-8 JSON. UTF-8 (optional BOM skip) stays
 * streaming; UTF-16/32 loads the payload once and rewrites to UTF-8.
 */
internal fun prepareUtf8JsonSource(source: BufferedSource): BufferedSource {
    if (!source.request(BOM_PROBE_MIN.toLong())) return source

    val buf = source.buffer
    val available = buf.size.toInt().coerceAtMost(BOM_PROBE_SIZE)
    val probe = ByteArray(available)
    for (i in 0 until available) {
        probe[i] = buf[i.toLong()]
    }

    val detected = detectJsonByteEncoding(probe, 0, available)
    if (detected.kind == JsonEncodingKind.UTF8) {
        if (detected.bomSize > 0) source.skip(detected.bomSize.toLong())
        return source
    }

    val raw = source.readByteArray()
    val offset = detected.bomSize
    val length = raw.size - detected.bomSize
    return Buffer().write(
        when (detected.kind) {
            JsonEncodingKind.UTF16_LE -> utf16ToUtf8(raw, offset, length, littleEndian = true)
            JsonEncodingKind.UTF16_BE -> utf16ToUtf8(raw, offset, length, littleEndian = false)
            JsonEncodingKind.UTF32_LE -> utf32ToUtf8(raw, offset, length, littleEndian = true)
            JsonEncodingKind.UTF32_BE -> utf32ToUtf8(raw, offset, length, littleEndian = false)
            JsonEncodingKind.UTF8 -> raw // unreachable
        }
    )
}

internal fun detectJsonByteEncoding(
    bytes: ByteArray,
    offset: Int,
    length: Int
): DetectedJsonEncoding {
    if (length <= 0) return DetectedJsonEncoding(JsonEncodingKind.UTF8, NO_BOM)

    val b0 = bytes[offset].toInt() and C.BYTE_MASK
    val b1 = if (length > 1) bytes[offset + 1].toInt() and C.BYTE_MASK else ABSENT_BYTE
    val b2 = if (length > 2) bytes[offset + 2].toInt() and C.BYTE_MASK else ABSENT_BYTE
    val b3 = if (length > 3) bytes[offset + 3].toInt() and C.BYTE_MASK else ABSENT_BYTE

    // BOM detection (prefer longer matches first).
    if (length >= UTF32_UNIT_SIZE &&
        b0 == NUL_BYTE && b1 == NUL_BYTE && b2 == UTF16_BE_BOM_0 && b3 == UTF16_LE_BOM_0
    ) {
        return DetectedJsonEncoding(JsonEncodingKind.UTF32_BE, UTF32_UNIT_SIZE)
    }
    if (length >= UTF32_UNIT_SIZE &&
        b0 == UTF16_LE_BOM_0 && b1 == UTF16_BE_BOM_0 && b2 == NUL_BYTE && b3 == NUL_BYTE
    ) {
        return DetectedJsonEncoding(JsonEncodingKind.UTF32_LE, UTF32_UNIT_SIZE)
    }
    if (length >= UTF16_UNIT_SIZE && b0 == UTF16_BE_BOM_0 && b1 == UTF16_LE_BOM_0) {
        return DetectedJsonEncoding(JsonEncodingKind.UTF16_BE, UTF16_UNIT_SIZE)
    }
    if (length >= UTF16_UNIT_SIZE && b0 == UTF16_LE_BOM_0 && b1 == UTF16_BE_BOM_0) {
        return DetectedJsonEncoding(JsonEncodingKind.UTF16_LE, UTF16_UNIT_SIZE)
    }
    if (length >= UTF8_BOM_SIZE && b0 == UTF8_BOM_0 && b1 == UTF8_BOM_1 && b2 == UTF8_BOM_2) {
        return DetectedJsonEncoding(JsonEncodingKind.UTF8, UTF8_BOM_SIZE)
    }

    // BOM-less NUL-byte patterns (RFC 4627 / common practice).
    if (length >= UTF32_UNIT_SIZE) {
        when {
            b0 == NUL_BYTE && b1 == NUL_BYTE && b2 == NUL_BYTE && b3 != NUL_BYTE ->
                return DetectedJsonEncoding(JsonEncodingKind.UTF32_BE, NO_BOM)

            b0 != NUL_BYTE && b1 == NUL_BYTE && b2 == NUL_BYTE && b3 == NUL_BYTE ->
                return DetectedJsonEncoding(JsonEncodingKind.UTF32_LE, NO_BOM)

            b0 == NUL_BYTE && b1 != NUL_BYTE && b2 == NUL_BYTE && b3 != NUL_BYTE ->
                return DetectedJsonEncoding(JsonEncodingKind.UTF16_BE, NO_BOM)

            b0 != NUL_BYTE && b1 == NUL_BYTE && b2 != NUL_BYTE && b3 == NUL_BYTE ->
                return DetectedJsonEncoding(JsonEncodingKind.UTF16_LE, NO_BOM)
        }
    } else if (length >= UTF16_UNIT_SIZE) {
        when {
            b0 == NUL_BYTE && b1 != NUL_BYTE ->
                return DetectedJsonEncoding(JsonEncodingKind.UTF16_BE, NO_BOM)

            b0 != NUL_BYTE && b1 == NUL_BYTE ->
                return DetectedJsonEncoding(JsonEncodingKind.UTF16_LE, NO_BOM)
        }
    }

    return DetectedJsonEncoding(JsonEncodingKind.UTF8, NO_BOM)
}

internal fun utf16ToUtf8(
    bytes: ByteArray,
    offset: Int,
    length: Int,
    littleEndian: Boolean
): ByteArray {
    if (length <= 0) return C.EMPTY_BYTES
    if (length % UTF16_UNIT_SIZE != 0) {
        throw GhostJsonException(C.ERR_INVALID_JSON_ENCODING)
    }
    val out = ByteArray(utf8MaxSizeFromUtf16(length))
    var oi = 0
    var i = offset
    val end = offset + length
    while (i < end) {
        val unit = readUtf16Unit(bytes, i, littleEndian)
        i += UTF16_UNIT_SIZE
        val cp = when {
            unit in C.HIGH_SURROGATE_START..C.HIGH_SURROGATE_END -> {
                if (i >= end) throw GhostJsonException(C.ERR_INVALID_JSON_ENCODING)
                val low = readUtf16Unit(bytes, i, littleEndian)
                i += UTF16_UNIT_SIZE
                if (low !in C.LOW_SURROGATE_START..C.LOW_SURROGATE_END) {
                    throw GhostJsonException(C.ERR_INVALID_JSON_ENCODING)
                }
                SUPPLEMENTARY_PLANE_BASE +
                        ((unit - C.HIGH_SURROGATE_START) shl SURROGATE_TO_CP_SHIFT) +
                        (low - C.LOW_SURROGATE_START)
            }

            unit in C.LOW_SURROGATE_START..C.LOW_SURROGATE_END ->
                throw GhostJsonException(C.ERR_INVALID_JSON_ENCODING)

            else -> unit
        }
        oi = writeUtf8CodePoint(out, oi, cp)
    }
    return if (oi == out.size) out else out.copyOf(oi)
}

internal fun utf32ToUtf8(
    bytes: ByteArray,
    offset: Int,
    length: Int,
    littleEndian: Boolean
): ByteArray {
    if (length <= 0) return C.EMPTY_BYTES
    if (length % UTF32_UNIT_SIZE != 0) {
        throw GhostJsonException(C.ERR_INVALID_JSON_ENCODING)
    }
    val out = ByteArray((length / UTF32_UNIT_SIZE) * C.UTF8_4BYTE_SIZE)
    var oi = 0
    var i = offset
    val end = offset + length
    while (i < end) {
        val cp = readUtf32CodePoint(bytes, i, littleEndian)
        i += UTF32_UNIT_SIZE
        if (cp !in 0..UNICODE_MAX_CODE_POINT ||
            cp in C.HIGH_SURROGATE_START..C.LOW_SURROGATE_END
        ) {
            throw GhostJsonException(C.ERR_INVALID_JSON_ENCODING)
        }
        oi = writeUtf8CodePoint(out, oi, cp)
    }
    return if (oi == out.size) out else out.copyOf(oi)
}

private inline fun readUtf16Unit(bytes: ByteArray, index: Int, littleEndian: Boolean): Int {
    val b0 = bytes[index].toInt() and C.BYTE_MASK
    val b1 = bytes[index + 1].toInt() and C.BYTE_MASK
    return if (littleEndian) b0 or (b1 shl C.SHIFT_8) else (b0 shl C.SHIFT_8) or b1
}

private inline fun readUtf32CodePoint(bytes: ByteArray, index: Int, littleEndian: Boolean): Int {
    val b0 = bytes[index].toInt() and C.BYTE_MASK
    val b1 = bytes[index + 1].toInt() and C.BYTE_MASK
    val b2 = bytes[index + 2].toInt() and C.BYTE_MASK
    val b3 = bytes[index + 3].toInt() and C.BYTE_MASK
    return if (littleEndian) {
        b0 or (b1 shl C.SHIFT_8) or (b2 shl C.SHIFT_16) or (b3 shl C.SHIFT_24)
    } else {
        (b0 shl C.SHIFT_24) or (b1 shl C.SHIFT_16) or (b2 shl C.SHIFT_8) or b3
    }
}

private fun writeUtf8CodePoint(out: ByteArray, offset: Int, cp: Int): Int {
    var o = offset
    when {
        cp < C.UTF8_1BYTE_LIMIT -> {
            out[o++] = cp.toByte()
        }

        cp < C.UTF8_2BYTE_LIMIT -> {
            out[o++] = (C.UTF8_2BYTE_PREFIX or (cp shr C.UTF8_SHIFT_6)).toByte()
            out[o++] = (C.UTF8_CONT_PREFIX or (cp and C.UTF8_CONT_MASK)).toByte()
        }

        cp < SUPPLEMENTARY_PLANE_BASE -> {
            out[o++] = (C.UTF8_3BYTE_PREFIX or (cp shr C.UTF8_SHIFT_12)).toByte()
            out[o++] =
                (C.UTF8_CONT_PREFIX or ((cp shr C.UTF8_SHIFT_6) and C.UTF8_CONT_MASK)).toByte()
            out[o++] = (C.UTF8_CONT_PREFIX or (cp and C.UTF8_CONT_MASK)).toByte()
        }

        else -> {
            out[o++] = (C.UTF8_4BYTE_PREFIX or (cp shr C.UTF8_SHIFT_18)).toByte()
            out[o++] =
                (C.UTF8_CONT_PREFIX or ((cp shr C.UTF8_SHIFT_12) and C.UTF8_CONT_MASK)).toByte()
            out[o++] =
                (C.UTF8_CONT_PREFIX or ((cp shr C.UTF8_SHIFT_6) and C.UTF8_CONT_MASK)).toByte()
            out[o++] = (C.UTF8_CONT_PREFIX or (cp and C.UTF8_CONT_MASK)).toByte()
        }
    }
    return o
}

private fun utf8MaxSizeFromUtf16(utf16ByteLength: Int): Int {
    // Worst case: every UTF-16 unit becomes a 3-byte UTF-8 sequence (BMP non-ASCII);
    // surrogate pairs (2 units) become 4 UTF-8 bytes, which stays within this bound.
    return (utf16ByteLength / UTF16_UNIT_SIZE) * C.UTF8_3BYTE_SIZE
}

/** Sentinel for a byte position that is past [length] during BOM probing. */
private const val ABSENT_BYTE = -1

/** Numeric value of the NUL byte (0x00). */
private const val NUL_BYTE = 0

/** BOM size to report when no byte-order mark is present. */
private const val NO_BOM = 0

/** Bytes per UTF-16 code unit. */
private const val UTF16_UNIT_SIZE = 2

/** Bytes per UTF-32 code unit. */
private const val UTF32_UNIT_SIZE = 4

/** Minimum bytes to request before probing for a BOM. */
private const val BOM_PROBE_MIN = 1

/** Bytes inspected when sniffing the leading BOM / NUL pattern. */
private const val BOM_PROBE_SIZE = 4

private const val UTF8_BOM_0 = 0xEF
private const val UTF8_BOM_1 = 0xBB
private const val UTF8_BOM_2 = 0xBF
private const val UTF8_BOM_SIZE = 3

private const val UTF16_BE_BOM_0 = 0xFE
private const val UTF16_LE_BOM_0 = 0xFF

/** First code point outside the BMP (start of the supplementary planes). */
private const val SUPPLEMENTARY_PLANE_BASE = 0x10000

/** Bit shift applied to the high surrogate when rebuilding a supplementary code point. */
private const val SURROGATE_TO_CP_SHIFT = 10

/** Largest valid Unicode code point (U+10FFFF). */
private const val UNICODE_MAX_CODE_POINT = 0x10FFFF


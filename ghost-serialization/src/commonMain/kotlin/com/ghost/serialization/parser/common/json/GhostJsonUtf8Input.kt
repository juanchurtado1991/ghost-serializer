@file:OptIn(InternalGhostApi::class)
@file:Suppress("NOTHING_TO_INLINE")

package com.ghost.serialization.parser.common.json

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.GhostJsonException
import okio.Buffer
import okio.BufferedSource
import com.ghost.serialization.parser.common.constants.GhostJsonErrorMessages as EM
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants as SCN
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants as WR

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

    val firstByte = bytes[0].toInt() and TOK.BYTE_MASK
    // Fast path: UTF-8 JSON starts with an ASCII structural/value byte, not a BOM lead or
    // NUL, followed by a non-NUL byte. UTF-16/32 always interleave NULs for ASCII code
    // points, so this rules them out with a handful of comparisons, no allocation.
    val isBomlessUtf8 = firstByte != TOK.UTF8_BOM_0 &&
        firstByte != TOK.UTF16_BE_BOM_0 &&
        firstByte != TOK.UTF16_LE_BOM_0 &&
        firstByte != TOK.NUL_BYTE &&
        (limit < TOK.UTF16_UNIT_SIZE || (bytes[1].toInt() and TOK.BYTE_MASK) != TOK.NUL_BYTE)
    if (isBomlessUtf8) {
        return block(bytes, 0, limit)
    }

    val detected = detectJsonByteEncoding(bytes = bytes, offset = 0, length = limit)
    val payloadOffset = detected.bomSize
    val payloadLength = limit - detected.bomSize
    if (detected.kind == JsonEncodingKind.UTF8) {
        return block(bytes, payloadOffset, payloadLength)
    }
    val utf8 = decodeNonUtf8ToUtf8(bytes = bytes, offset = payloadOffset, length = payloadLength, kind = detected.kind)
    return block(utf8, 0, utf8.size)
}

/**
 * Ensures a streaming source yields UTF-8 JSON. UTF-8 (optional BOM skip) stays
 * streaming; UTF-16/32 loads the payload once and rewrites to UTF-8.
 */
internal fun prepareUtf8JsonSource(source: BufferedSource): BufferedSource {
    if (!source.request(TOK.BOM_PROBE_MIN.toLong())) return source

    val sourceBuffer = source.buffer
    val available = sourceBuffer.size.toInt().coerceAtMost(TOK.BOM_PROBE_SIZE)
    val probe = ByteArray(available)
    for (probeIndex in 0 until available) {
        probe[probeIndex] = sourceBuffer[probeIndex.toLong()]
    }

    val detected = detectJsonByteEncoding(bytes = probe, offset = 0, length = available)
    if (detected.kind == JsonEncodingKind.UTF8) {
        if (detected.bomSize > 0) source.skip(detected.bomSize.toLong())
        return source
    }

    val raw = source.readByteArray()
    val utf8 = decodeNonUtf8ToUtf8(
        bytes = raw,
        offset = detected.bomSize,
        length = raw.size - detected.bomSize,
        kind = detected.kind
    )
    return Buffer().write(utf8)
}

internal fun detectJsonByteEncoding(
    bytes: ByteArray,
    offset: Int,
    length: Int
): DetectedJsonEncoding {
    if (length <= 0) return DetectedJsonEncoding(kind = JsonEncodingKind.UTF8, bomSize = TOK.NO_BOM)

    val byte0 = bytes[offset].toInt() and TOK.BYTE_MASK
    val byte1 = if (length > 1) bytes[offset + 1].toInt() and TOK.BYTE_MASK else TOK.ABSENT_BYTE
    val byte2 = if (length > 2) bytes[offset + 2].toInt() and TOK.BYTE_MASK else TOK.ABSENT_BYTE
    val byte3 = if (length > 3) bytes[offset + 3].toInt() and TOK.BYTE_MASK else TOK.ABSENT_BYTE

    // BOM detection (prefer longer matches first).
    val isUtf32BigEndianBom = length >= TOK.UTF32_UNIT_SIZE &&
        byte0 == TOK.NUL_BYTE &&
        byte1 == TOK.NUL_BYTE &&
        byte2 == TOK.UTF16_BE_BOM_0 &&
        byte3 == TOK.UTF16_LE_BOM_0
    if (isUtf32BigEndianBom) {
        return DetectedJsonEncoding(kind = JsonEncodingKind.UTF32_BE, bomSize = TOK.UTF32_UNIT_SIZE)
    }
    val isUtf32LittleEndianBom = length >= TOK.UTF32_UNIT_SIZE &&
        byte0 == TOK.UTF16_LE_BOM_0 &&
        byte1 == TOK.UTF16_BE_BOM_0 &&
        byte2 == TOK.NUL_BYTE &&
        byte3 == TOK.NUL_BYTE
    if (isUtf32LittleEndianBom) {
        return DetectedJsonEncoding(kind = JsonEncodingKind.UTF32_LE, bomSize = TOK.UTF32_UNIT_SIZE)
    }
    val isUtf16BigEndianBom = length >= TOK.UTF16_UNIT_SIZE &&
        byte0 == TOK.UTF16_BE_BOM_0 &&
        byte1 == TOK.UTF16_LE_BOM_0
    if (isUtf16BigEndianBom) {
        return DetectedJsonEncoding(kind = JsonEncodingKind.UTF16_BE, bomSize = TOK.UTF16_UNIT_SIZE)
    }
    val isUtf16LittleEndianBom = length >= TOK.UTF16_UNIT_SIZE &&
        byte0 == TOK.UTF16_LE_BOM_0 &&
        byte1 == TOK.UTF16_BE_BOM_0
    if (isUtf16LittleEndianBom) {
        return DetectedJsonEncoding(kind = JsonEncodingKind.UTF16_LE, bomSize = TOK.UTF16_UNIT_SIZE)
    }
    val isUtf8Bom = length >= TOK.UTF8_BOM_SIZE &&
        byte0 == TOK.UTF8_BOM_0 &&
        byte1 == TOK.UTF8_BOM_1 &&
        byte2 == TOK.UTF8_BOM_2
    if (isUtf8Bom) {
        return DetectedJsonEncoding(kind = JsonEncodingKind.UTF8, bomSize = TOK.UTF8_BOM_SIZE)
    }

    // BOM-less NUL-byte patterns (RFC 4627 / common practice).
    if (length >= TOK.UTF32_UNIT_SIZE) {
        when {
            byte0 == TOK.NUL_BYTE && byte1 == TOK.NUL_BYTE && byte2 == TOK.NUL_BYTE && byte3 != TOK.NUL_BYTE ->
                return DetectedJsonEncoding(kind = JsonEncodingKind.UTF32_BE, bomSize = TOK.NO_BOM)

            byte0 != TOK.NUL_BYTE && byte1 == TOK.NUL_BYTE && byte2 == TOK.NUL_BYTE && byte3 == TOK.NUL_BYTE ->
                return DetectedJsonEncoding(kind = JsonEncodingKind.UTF32_LE, bomSize = TOK.NO_BOM)

            byte0 == TOK.NUL_BYTE && byte1 != TOK.NUL_BYTE && byte2 == TOK.NUL_BYTE && byte3 != TOK.NUL_BYTE ->
                return DetectedJsonEncoding(kind = JsonEncodingKind.UTF16_BE, bomSize = TOK.NO_BOM)

            byte0 != TOK.NUL_BYTE && byte1 == TOK.NUL_BYTE && byte2 != TOK.NUL_BYTE && byte3 == TOK.NUL_BYTE ->
                return DetectedJsonEncoding(kind = JsonEncodingKind.UTF16_LE, bomSize = TOK.NO_BOM)
        }
    } else if (length >= TOK.UTF16_UNIT_SIZE) {
        when {
            byte0 == TOK.NUL_BYTE && byte1 != TOK.NUL_BYTE ->
                return DetectedJsonEncoding(kind = JsonEncodingKind.UTF16_BE, bomSize = TOK.NO_BOM)

            byte0 != TOK.NUL_BYTE && byte1 == TOK.NUL_BYTE ->
                return DetectedJsonEncoding(kind = JsonEncodingKind.UTF16_LE, bomSize = TOK.NO_BOM)
        }
    }

    return DetectedJsonEncoding(kind = JsonEncodingKind.UTF8, bomSize = TOK.NO_BOM)
}

internal fun utf16ToUtf8(
    bytes: ByteArray,
    offset: Int,
    length: Int,
    littleEndian: Boolean
): ByteArray {
    if (length <= 0) return TOK.EMPTY_BYTES
    validateUnitAlignment(length = length, unitSize = TOK.UTF16_UNIT_SIZE)
    val utf8Out = ByteArray(utf8MaxSizeFromUtf16(utf16ByteLength = length))
    var outIndex = 0
    var readIndex = offset
    val end = offset + length
    while (readIndex < end) {
        val codeUnit = readUtf16Unit(bytes = bytes, index = readIndex, littleEndian = littleEndian)
        readIndex += TOK.UTF16_UNIT_SIZE
        val codePoint = when {
            codeUnit in TOK.HIGH_SURROGATE_START..TOK.HIGH_SURROGATE_END -> {
                if (readIndex >= end) throw GhostJsonException(EM.ERR_INVALID_JSON_ENCODING)
                val lowSurrogate = readUtf16Unit(bytes = bytes, index = readIndex, littleEndian = littleEndian)
                readIndex += TOK.UTF16_UNIT_SIZE
                if (lowSurrogate !in TOK.LOW_SURROGATE_START..TOK.LOW_SURROGATE_END) {
                    throw GhostJsonException(EM.ERR_INVALID_JSON_ENCODING)
                }
                TOK.UNICODE_BASE +
                        ((codeUnit - TOK.HIGH_SURROGATE_START) shl TOK.SHIFT_10) +
                        (lowSurrogate - TOK.LOW_SURROGATE_START)
            }

            codeUnit in TOK.LOW_SURROGATE_START..TOK.LOW_SURROGATE_END ->
                throw GhostJsonException(EM.ERR_INVALID_JSON_ENCODING)

            else -> codeUnit
        }
        outIndex = writeUtf8CodePoint(out = utf8Out, offset = outIndex, codePoint = codePoint)
    }
    return trimToActualSize(buffer = utf8Out, actualSize = outIndex)
}

internal fun utf32ToUtf8(
    bytes: ByteArray,
    offset: Int,
    length: Int,
    littleEndian: Boolean
): ByteArray {
    if (length <= 0) return TOK.EMPTY_BYTES
    validateUnitAlignment(length = length, unitSize = TOK.UTF32_UNIT_SIZE)
    val utf8Out = ByteArray((length / TOK.UTF32_UNIT_SIZE) * WR.UTF8_4BYTE_SIZE)
    var outIndex = 0
    var readIndex = offset
    val end = offset + length
    while (readIndex < end) {
        val codePoint = readUtf32CodePoint(bytes = bytes, index = readIndex, littleEndian = littleEndian)
        readIndex += TOK.UTF32_UNIT_SIZE
        val isInvalidCodePoint = codePoint !in 0..TOK.UNICODE_MAX_CODE_POINT ||
            codePoint in TOK.HIGH_SURROGATE_START..TOK.LOW_SURROGATE_END
        if (isInvalidCodePoint) {
            throw GhostJsonException(EM.ERR_INVALID_JSON_ENCODING)
        }
        outIndex = writeUtf8CodePoint(out = utf8Out, offset = outIndex, codePoint = codePoint)
    }
    return trimToActualSize(buffer = utf8Out, actualSize = outIndex)
}

/** Dispatches a non-UTF-8 payload to [utf16ToUtf8] / [utf32ToUtf8]; [kind] must not be [JsonEncodingKind.UTF8]. */
private fun decodeNonUtf8ToUtf8(bytes: ByteArray, offset: Int, length: Int, kind: JsonEncodingKind): ByteArray =
    when (kind) {
        JsonEncodingKind.UTF16_LE -> utf16ToUtf8(bytes = bytes, offset = offset, length = length, littleEndian = true)
        JsonEncodingKind.UTF16_BE -> utf16ToUtf8(bytes = bytes, offset = offset, length = length, littleEndian = false)
        JsonEncodingKind.UTF32_LE -> utf32ToUtf8(bytes = bytes, offset = offset, length = length, littleEndian = true)
        JsonEncodingKind.UTF32_BE -> utf32ToUtf8(bytes = bytes, offset = offset, length = length, littleEndian = false)
        JsonEncodingKind.UTF8 -> bytes // unreachable: callers handle UTF8 separately
    }

private inline fun readUtf16Unit(bytes: ByteArray, index: Int, littleEndian: Boolean): Int {
    val firstByte = bytes[index].toInt() and TOK.BYTE_MASK
    val secondByte = bytes[index + 1].toInt() and TOK.BYTE_MASK
    return if (littleEndian) {
        firstByte or (secondByte shl SCN.SHIFT_8)
    } else {
        (firstByte shl SCN.SHIFT_8) or secondByte
    }
}

private inline fun readUtf32CodePoint(bytes: ByteArray, index: Int, littleEndian: Boolean): Int {
    val byte0 = bytes[index].toInt() and TOK.BYTE_MASK
    val byte1 = bytes[index + 1].toInt() and TOK.BYTE_MASK
    val byte2 = bytes[index + 2].toInt() and TOK.BYTE_MASK
    val byte3 = bytes[index + 3].toInt() and TOK.BYTE_MASK
    return if (littleEndian) {
        byte0 or (byte1 shl SCN.SHIFT_8) or (byte2 shl SCN.SHIFT_16) or (byte3 shl SCN.SHIFT_24)
    } else {
        (byte0 shl SCN.SHIFT_24) or (byte1 shl SCN.SHIFT_16) or (byte2 shl SCN.SHIFT_8) or byte3
    }
}

/** Throws if [length] isn't a whole number of [unitSize]-byte code units. */
private inline fun validateUnitAlignment(length: Int, unitSize: Int) {
    if (length % unitSize != 0) throw GhostJsonException(EM.ERR_INVALID_JSON_ENCODING)
}

private fun writeUtf8CodePoint(out: ByteArray, offset: Int, codePoint: Int): Int {
    var writeOffset = offset
    when {
        codePoint < WR.UTF8_1BYTE_LIMIT -> {
            out[writeOffset++] = codePoint.toByte()
        }

        codePoint < WR.UTF8_2BYTE_LIMIT -> {
            out[writeOffset++] = (WR.UTF8_2BYTE_PREFIX or (codePoint shr WR.UTF8_SHIFT_6)).toByte()
            out[writeOffset++] = (WR.UTF8_CONT_PREFIX or (codePoint and WR.UTF8_CONT_MASK)).toByte()
        }

        codePoint < TOK.UNICODE_BASE -> {
            out[writeOffset++] = (WR.UTF8_3BYTE_PREFIX or (codePoint shr WR.UTF8_SHIFT_12)).toByte()
            out[writeOffset++] =
                (WR.UTF8_CONT_PREFIX or ((codePoint shr WR.UTF8_SHIFT_6) and WR.UTF8_CONT_MASK)).toByte()
            out[writeOffset++] = (WR.UTF8_CONT_PREFIX or (codePoint and WR.UTF8_CONT_MASK)).toByte()
        }

        else -> {
            out[writeOffset++] = (WR.UTF8_4BYTE_PREFIX or (codePoint shr WR.UTF8_SHIFT_18)).toByte()
            out[writeOffset++] =
                (WR.UTF8_CONT_PREFIX or ((codePoint shr WR.UTF8_SHIFT_12) and WR.UTF8_CONT_MASK)).toByte()
            out[writeOffset++] =
                (WR.UTF8_CONT_PREFIX or ((codePoint shr WR.UTF8_SHIFT_6) and WR.UTF8_CONT_MASK)).toByte()
            out[writeOffset++] = (WR.UTF8_CONT_PREFIX or (codePoint and WR.UTF8_CONT_MASK)).toByte()
        }
    }
    return writeOffset
}

private fun utf8MaxSizeFromUtf16(utf16ByteLength: Int): Int {
    // Worst case: every UTF-16 unit becomes a 3-byte UTF-8 sequence (BMP non-ASCII);
    // surrogate pairs (2 units) become 4 UTF-8 bytes, which stays within this bound.
    return (utf16ByteLength / TOK.UTF16_UNIT_SIZE) * WR.UTF8_3BYTE_SIZE
}

/** Returns [buffer] unchanged if fully used, otherwise a copy trimmed to [actualSize]. */
private inline fun trimToActualSize(buffer: ByteArray, actualSize: Int): ByteArray =
    if (actualSize == buffer.size) buffer else buffer.copyOf(actualSize)

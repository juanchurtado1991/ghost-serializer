@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser.bytes

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.types.RawJson
import com.ghost.serialization.parser.common.captureJsonValueScan
import com.ghost.serialization.parser.common.GhostJsonConstants as C

/**
 * Captures the next complete JSON value (object, array, string, number, boolean, null)
 * as a [RawJson] view into this reader's buffer without copying UTF-8 bytes.
 */
fun GhostJsonFlatReader.captureRawJson(): RawJson {
    skipWhitespace()
    val start = position
    captureJsonValueBytes()
    nextTokenByte = C.RESET_TOKEN_BYTE
    val length = position - start
    return if (materializeRawJsonCaptures) {
        RawJson.fromUtf8Bytes(rawData.copyOfRange(start, position))
    } else {
        RawJson.fromBufferSlice(rawData, start, length)
    }
}

/**
 * Captures the next complete JSON value (object, array, string, number, boolean, null)
 * as a raw [ByteArray] without decoding any content. The returned bytes are a verbatim
 * copy of the UTF-8 payload that represents the value in the input, including surrounding
 * brackets or quotes.
 *
 * Designed for deferred parsing: capture bytes here, then pass them later to
 * `Ghost.deserialize` which will create a new flat reader
 * over the captured slice — no intermediate String allocation, no UTF-8→UTF-16 round-trip.
 *
 * Contrast with `GhostJsonFlatReader.skipValue`, which
 * uses the stateful depth/comma machine. This function is a pure byte-level scan that
 * does not touch depth, needsCommaMask, or commaConsumedMask.
 */
fun GhostJsonFlatReader.captureRawJsonBytes(): ByteArray = captureRawJson().bytes

private fun GhostJsonFlatReader.captureJsonValueBytes() {
    val data = rawData
    position = captureJsonValueScan(position, limit) { index ->
        data[index].toInt() and C.BYTE_MASK
    }
}

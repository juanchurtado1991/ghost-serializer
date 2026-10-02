@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser.bytes.extensions

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.common.json.captureJsonValueScan
import com.ghost.serialization.types.RawJson
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants as SCN
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

/**
 * [captureRawJson]/[captureRawJsonBytes] capture the next complete JSON value (object, array,
 * string, number, boolean, null) as a verbatim UTF-8 byte view, without decoding — for deferred
 * parsing: capture now, pass the bytes to `Ghost.deserialize` later to build a new reader over
 * the slice, with no intermediate String allocation or UTF-8→UTF-16 round-trip. Contrast with
 * [GhostJsonFlatReader.skipValue], which walks the depth/comma state machine — this is a pure
 * byte-level scan untouched by depth/needsCommaMask/commaConsumedMask.
 */
fun GhostJsonFlatReader.captureRawJson(): RawJson {
    skipWhitespace()
    val start = position
    captureJsonValueBytes()
    nextTokenByte = SCN.RESET_TOKEN_BYTE
    return if (materializeRawJsonCaptures) {
        RawJson.fromUtf8Bytes(
            bytes = rawData.copyOfRange(
                fromIndex = start,
                toIndex = position
            )
        )
    } else {
        RawJson.fromBufferSlice(
            buffer = rawData,
            offset = start,
            length = position - start
        )
    }
}

fun GhostJsonFlatReader.captureRawJsonBytes(): ByteArray = captureRawJson().bytes

private fun GhostJsonFlatReader.captureJsonValueBytes() {
    val data = rawData
    position = captureJsonValueScan(
        startPosition = position,
        limit = limit
    ) { index ->
        data[index].toInt() and TOK.BYTE_MASK
    }
}

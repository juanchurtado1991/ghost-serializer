package com.ghost.serialization.parser.strings

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.json.captureJsonValueScan
import com.ghost.serialization.types.RawJson
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants as SCN

/**
 * Captures the next complete JSON value as owned [RawJson] (UTF-16 source requires encoding).
 */
fun GhostJsonStringReader.captureRawJson(): RawJson =
    RawJson.fromUtf8Bytes(bytes = captureRawJsonBytes())

/**
 * Captures the next complete JSON value as a raw [ByteArray] without decoding the value tree.
 *
 * Since [GhostJsonStringReader] operates on a UTF-16 [String], the captured char range is
 * converted to UTF-8 via [GhostJsonStringReader.sliceUtf8Bytes]: a range copy of the cached
 * UTF-8 view when present, otherwise an encode of that range only. Prefer
 * `parser.bytes.captureRawJsonBytes` when starting from a [ByteArray] source.
 */
@OptIn(InternalGhostApi::class)
fun GhostJsonStringReader.captureRawJsonBytes(): ByteArray {
    skipWhitespace()
    val start = position
    captureStringReaderValueBytes()
    nextTokenByte = SCN.RESET_TOKEN_BYTE
    return sliceUtf8Bytes(charStart = start, charEnd = position)
}

private fun GhostJsonStringReader.captureStringReaderValueBytes() {
    val chars = rawChars
    position = captureJsonValueScan(
        startPosition = position,
        limit = limit,
    ) { index -> chars[index].code }
}

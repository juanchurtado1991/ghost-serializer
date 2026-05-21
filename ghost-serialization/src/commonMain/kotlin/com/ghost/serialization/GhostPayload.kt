@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.GhostHeuristics
import com.ghost.serialization.parser.GhostJsonConstants
import okio.Buffer
import okio.BufferedSource

internal const val PAYLOAD_READ_CHUNK = 8192

fun throwPayloadTooLarge(maxBytes: Int): Nothing {
    throw GhostJsonException(
        "${GhostJsonConstants.ERR_MAX_PAYLOAD_SIZE} ($maxBytes bytes)",
        0,
        0
    )
}

fun checkPayloadSize(size: Long) {
    val max = GhostLimits.effectiveMaxPayloadBytes()
    if (size > max) {
        throwPayloadTooLarge(max)
    }
}

fun checkPayloadSize(size: Int) = checkPayloadSize(size.toLong())

/**
 * Reads all bytes from [source] up to [maxBytes], failing fast if the payload is larger.
 */
fun BufferedSource.readPayloadBytes(
    maxBytes: Int = GhostLimits.effectiveMaxPayloadBytes()
): ByteArray {
    val sink = Buffer()
    var total = 0L
    while (true) {
        val remaining = maxBytes - total
        if (remaining <= 0) {
            throwPayloadTooLarge(maxBytes)
        }
        val toRead = minOf(PAYLOAD_READ_CHUNK.toLong(), remaining)
        val read = read(sink, toRead)
        if (read == -1L) {
            break
        }
        total += read
        if (total > maxBytes) {
            throwPayloadTooLarge(maxBytes)
        }
    }
    return sink.readByteArray()
}

/**
 * Returns a larger scratch buffer for streaming adapters, or throws if the cap would be exceeded.
 */
@InternalGhostApi
fun growPayloadBuffer(
    current: ByteArray,
    offset: Int,
    maxBytes: Int = GhostLimits.effectiveMaxPayloadBytes()
): ByteArray {
    if (offset > maxBytes) {
        throwPayloadTooLarge(maxBytes)
    }
    val newSize = current.size * 2
    if (newSize > maxBytes) {
        throwPayloadTooLarge(maxBytes)
    }
    return acquireScratchBuffer(newSize)
}

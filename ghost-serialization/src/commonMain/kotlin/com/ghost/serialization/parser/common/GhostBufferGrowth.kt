@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser.common

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.releaseScratchBuffer
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants as WR

/**
 * Grows a temporary scratch [ByteArray] by [WR.BUFFER_SCALE_FACTOR],
 * copying the first [outPos] bytes from [outBuffer] and releasing the old buffer to the pool.
 */
internal fun growBuffer(
    outBuffer: ByteArray,
    outPos: Int
): ByteArray = acquireScratchBuffer(
    minSize = outBuffer.size * WR.BUFFER_SCALE_FACTOR
).apply {
    outBuffer.copyInto(
        destination = this,
        destinationOffset = 0,
        startIndex = 0,
        endIndex = outPos
    )
    releaseScratchBuffer(buffer = outBuffer)
}

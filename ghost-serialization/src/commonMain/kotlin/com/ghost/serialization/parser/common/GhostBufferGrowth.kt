@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser.common

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.releaseScratchBuffer
import com.ghost.serialization.parser.common.GhostJsonConstants as C

/**
 * Grows a temporary scratch [ByteArray] by [GhostJsonConstants.BUFFER_SCALE_FACTOR],
 * copying the first [outPos] bytes from [outBuffer] and releasing the old buffer to the pool.
 */
internal fun growBuffer(outBuffer: ByteArray, outPos: Int): ByteArray {
    val newBuffer = acquireScratchBuffer(outBuffer.size * C.BUFFER_SCALE_FACTOR)
    outBuffer.copyInto(newBuffer, 0, 0, outPos)
    releaseScratchBuffer(outBuffer)
    return newBuffer
}

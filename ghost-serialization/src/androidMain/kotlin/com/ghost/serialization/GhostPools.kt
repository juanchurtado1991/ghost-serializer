package com.ghost.serialization

import com.ghost.serialization.parser.GhostJsonConstants

private val scratchPool = ThreadLocal<ByteArray>()

@InternalGhostApi
actual fun acquireScratchBuffer(): ByteArray {
    val existing = scratchPool.get()
    if (existing != null) {
        scratchPool.set(null)
        return existing
    }
    return ByteArray(GhostJsonConstants.SCRATCH_BUFFER_SIZE)
}

@InternalGhostApi
actual fun releaseScratchBuffer(buffer: ByteArray) {
    if (buffer.size == GhostJsonConstants.SCRATCH_BUFFER_SIZE) {
        scratchPool.set(buffer)
    }
}

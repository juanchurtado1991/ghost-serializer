package com.ghost.serialization

import com.ghost.serialization.parser.GhostJsonConstants

private var pool: ByteArray? = null

@InternalGhostApi
actual fun acquireScratchBuffer(): ByteArray {
    val existing = pool
    if (existing != null) {
        pool = null
        return existing
    }
    return ByteArray(GhostJsonConstants.SCRATCH_BUFFER_SIZE)
}

@InternalGhostApi
actual fun releaseScratchBuffer(buffer: ByteArray) {
    pool = buffer
}

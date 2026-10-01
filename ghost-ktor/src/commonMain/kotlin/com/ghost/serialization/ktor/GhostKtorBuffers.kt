package com.ghost.serialization.ktor

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.RESPONSE_SCRATCH_INITIAL_SIZE
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.growScratchBuffer
import com.ghost.serialization.releaseScratchBuffer
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable

/**
 * Shared scratch-buffer grow/read loop for Ktor content converters.
 */
@OptIn(InternalGhostApi::class)
internal object GhostKtorBuffers {
    /**
     * Reads [content] into a pooled scratch buffer that doubles when full.
     * Invokes [block] with the buffer and filled length, then releases the scratch.
     */
    suspend inline fun <T> readToScratch(
        content: ByteReadChannel,
        block: (buffer: ByteArray, length: Int) -> T
    ): T {
        var scratch = acquireScratchBuffer(minSize = RESPONSE_SCRATCH_INITIAL_SIZE)
        try {
            var offset = 0
            while (true) {
                if (offset == scratch.size) {
                    scratch = growScratchBuffer(scratch = scratch, usedBytes = offset)
                }

                val read = content.readAvailable(scratch, offset, scratch.size - offset)
                if (read == -1) break
                offset += read
            }
            return block(scratch, offset)
        } finally {
            releaseScratchBuffer(buffer = scratch)
        }
    }
}

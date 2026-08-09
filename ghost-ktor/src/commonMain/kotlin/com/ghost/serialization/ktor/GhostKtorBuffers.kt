package com.ghost.serialization.ktor

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.releaseScratchBuffer
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable

/**
 * Shared scratch-buffer grow/read loop for Ktor content converters.
 */
@OptIn(InternalGhostApi::class)
internal object GhostKtorBuffers {
    const val INITIAL_SIZE = 524288

    /**
     * Reads [content] into a pooled scratch buffer that doubles when full.
     * Invokes [block] with the buffer and filled length, then releases the scratch.
     */
    suspend inline fun <T> readToScratch(
        content: ByteReadChannel,
        block: (buffer: ByteArray, length: Int) -> T
    ): T {
        var scratch = acquireScratchBuffer(INITIAL_SIZE)
        try {
            var offset = 0
            while (true) {
                if (offset == scratch.size) {
                    val grown = acquireScratchBuffer(scratch.size * 2)
                    scratch.copyInto(grown, 0, 0, offset)
                    releaseScratchBuffer(scratch)
                    scratch = grown
                }

                val read = content.readAvailable(scratch, offset, scratch.size - offset)
                if (read == -1) break
                offset += read
            }
            return block(scratch, offset)
        } finally {
            releaseScratchBuffer(scratch)
        }
    }
}

package com.ghost.serialization.retrofit

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.releaseScratchBuffer
import java.io.InputStream

/**
 * Shared scratch-buffer grow/read loop for Retrofit response-body converters.
 */
@OptIn(InternalGhostApi::class)
internal object GhostRetrofitBuffers {
    const val INITIAL_SIZE = 524288

    /** Reads [stream] into a pooled scratch buffer that doubles when full, then invokes [block]. */
    inline fun <T> readToScratch(
        stream: InputStream,
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

                val read = stream.read(scratch, offset, scratch.size - offset)
                if (read == -1) break
                offset += read
            }
            return block(scratch, offset)
        } finally {
            releaseScratchBuffer(scratch)
        }
    }
}

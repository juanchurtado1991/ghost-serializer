package com.ghost.serialization.writer

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.GhostJsonConstants.INITIAL_WRITE_BUFFER_SIZE

/**
 * Pooled, reusable encode-target for in-memory encodes
 * (`Ghost.encodeToString` / `Ghost.encodeToBytes`).
 *
 * Both fields stay warm between calls so the underlying [ByteArray] survives
 * across encodes — only its content is reset. The pair is held in a
 * [ThreadLocal] (or `@ThreadLocal` on Kotlin/Native) per platform actual,
 * so concurrent encodes on different threads do not contend on it.
 */
@InternalGhostApi
class WriterSinkPair {
    /**
     * Backing flat byte buffer. The encoded payload lives in
     * `byteWriter.array[0 until byteWriter.size]` after each encode.
     */
    val byteWriter: FlatByteArrayWriter = FlatByteArrayWriter(INITIAL_WRITE_BUFFER_SIZE)

    /** Writer wired against [byteWriter]; calls into [byteWriter] are monomorphic. */
    val writer: GhostJsonFlatWriter = GhostJsonFlatWriter(byteWriter)
}

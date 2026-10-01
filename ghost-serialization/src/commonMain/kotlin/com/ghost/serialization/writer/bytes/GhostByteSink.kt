package com.ghost.serialization.writer.bytes

import okio.BufferedSink

/**
 * Byte-level sink shared by [GhostJsonWriter]'s and `GhostYamlWriter`'s two backing
 * stores each: an Okio [BufferedSink] (streaming) and a [FlatByteArrayWriter]
 * (in-memory). Each implementation picks its own fastest path per operation; the
 * writers themselves only call through this interface. Adds JSON-only literal/quoting
 * intrinsics to [GhostGenericByteSink] — `GhostYamlWriter` depends on that smaller interface
 * instead, since it only ever uses the generic subset.
 */
interface GhostByteSink : GhostGenericByteSink {
    fun writeDotZero()
    fun writeFalse()
    fun writeNull()
    fun writeQuotedAscii(text: String, length: Int)
    fun writeQuotedBmpCodeUnit(codePoint: Int)
    fun writeTrue()
}

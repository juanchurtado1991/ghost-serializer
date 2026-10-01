@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.writer.strings.FlatCharArrayWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter

/** Reusable char-array backed [GhostJsonStringWriter] pair, pooled per platform (thread-local or single-instance). */
@PublishedApi
internal class WriterStringPair {
    val charWriter = FlatCharArrayWriter()
    val writer = GhostJsonStringWriter(buffer = charWriter)
}

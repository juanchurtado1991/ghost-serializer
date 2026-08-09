@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.writer.strings.FlatCharArrayWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter

@PublishedApi
internal class WriterStringPair {
    val charWriter = FlatCharArrayWriter()
    val writer = GhostJsonStringWriter(charWriter)
}

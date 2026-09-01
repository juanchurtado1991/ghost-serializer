@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.bytes.FlatByteArrayWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter

private var cachedReader: GhostYamlFlatReader? = null
private var cachedWriter: GhostYamlWriter? = null
private var cachedWriterBuffer: FlatByteArrayWriter? = null

@InternalGhostApi
actual fun <T> ghostYamlInternalUseFlatReader(
    bytes: ByteArray,
    block: (GhostYamlFlatReader) -> T
): T {
    var reader = cachedReader
    if (reader == null) {
        reader = GhostYamlFlatReader(bytes)
        cachedReader = reader
    } else {
        reader.reset(bytes)
    }
    return block(reader)
}

@InternalGhostApi
actual fun <T> ghostYamlInternalUseFlatWriter(
    block: (GhostYamlWriter, FlatByteArrayWriter) -> T
): T {
    var writer = cachedWriter
    var buffer = cachedWriterBuffer
    if (writer == null || buffer == null) {
        buffer = FlatByteArrayWriter()
        writer = GhostYamlWriter(buffer)
        cachedWriter = writer
        cachedWriterBuffer = buffer
    } else {
        writer.reset()
        buffer.reset()
    }
    return block(writer, buffer)
}

package com.ghost.serialization.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.bytes.FlatByteArrayWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter

private val flatReaderPool = ThreadLocal<GhostYamlFlatReader>()
private val flatWriterPool = ThreadLocal<GhostYamlWriter>()
private val flatWriterBufferPool = ThreadLocal<FlatByteArrayWriter>()

@InternalGhostApi
actual fun <T> ghostYamlInternalUseFlatReader(
    bytes: ByteArray,
    block: (GhostYamlFlatReader) -> T
): T {
    var reader = flatReaderPool.get()
    if (reader == null) {
        reader = GhostYamlFlatReader(bytes)
        flatReaderPool.set(reader)
    } else {
        reader.reset(bytes)
    }
    return block(reader)
}

@InternalGhostApi
actual fun <T> ghostYamlInternalUseFlatWriter(
    block: (GhostYamlWriter, FlatByteArrayWriter) -> T
): T {
    var writer = flatWriterPool.get()
    var buffer = flatWriterBufferPool.get()
    if (writer == null || buffer == null) {
        buffer = FlatByteArrayWriter()
        writer = GhostYamlWriter(buffer)
        flatWriterPool.set(writer)
        flatWriterBufferPool.set(buffer)
    } else {
        writer.reset()
        buffer.reset()
    }
    return block(writer, buffer)
}

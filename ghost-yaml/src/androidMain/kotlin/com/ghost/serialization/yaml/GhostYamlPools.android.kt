package com.ghost.serialization.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.yaml.parser.GhostYamlFlatReader
import com.ghost.serialization.yaml.writer.GhostYamlFlatWriter
import com.ghost.serialization.writer.FlatByteArrayWriter

private val flatReaderPool = ThreadLocal<GhostYamlFlatReader>()
private val flatWriterPool = ThreadLocal<GhostYamlFlatWriter>()

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
    block: (GhostYamlFlatWriter) -> T
): T {
    var writer = flatWriterPool.get()
    if (writer == null) {
        writer = GhostYamlFlatWriter(FlatByteArrayWriter())
        flatWriterPool.set(writer)
    } else {
        writer.reset()
    }
    return block(writer)
}

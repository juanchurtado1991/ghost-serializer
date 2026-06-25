package com.ghost.serialization.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.yaml.parser.GhostYamlFlatReader
import com.ghost.serialization.yaml.writer.GhostYamlFlatWriter
import com.ghost.serialization.writer.FlatByteArrayWriter
import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
private var cachedReader: GhostYamlFlatReader? = null

@ThreadLocal
private var cachedWriter: GhostYamlFlatWriter? = null

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
    block: (GhostYamlFlatWriter) -> T
): T {
    var writer = cachedWriter
    if (writer == null) {
        writer = GhostYamlFlatWriter(FlatByteArrayWriter())
        cachedWriter = writer
    } else {
        writer.reset()
    }
    return block(writer)
}

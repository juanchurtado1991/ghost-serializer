package com.ghost.serialization.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.bytes.FlatByteArrayWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter

@InternalGhostApi
expect fun <T> ghostYamlInternalUseFlatReader(
    bytes: ByteArray,
    block: (GhostYamlFlatReader) -> T
): T

/** [block] receives the writer and its backing buffer (for extracting the encoded bytes). */
@InternalGhostApi
expect fun <T> ghostYamlInternalUseFlatWriter(
    block: (GhostYamlWriter, FlatByteArrayWriter) -> T
): T

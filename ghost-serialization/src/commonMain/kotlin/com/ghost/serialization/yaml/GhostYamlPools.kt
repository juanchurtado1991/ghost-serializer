package com.ghost.serialization.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.yaml.GhostYamlFlatWriter

@InternalGhostApi
expect fun <T> ghostYamlInternalUseFlatReader(
    bytes: ByteArray,
    block: (GhostYamlFlatReader) -> T
): T

@InternalGhostApi
expect fun <T> ghostYamlInternalUseFlatWriter(
    block: (GhostYamlFlatWriter) -> T
): T

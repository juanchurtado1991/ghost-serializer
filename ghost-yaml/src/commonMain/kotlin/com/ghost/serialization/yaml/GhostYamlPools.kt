package com.ghost.serialization.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.yaml.parser.GhostYamlFlatReader
import com.ghost.serialization.yaml.writer.GhostYamlFlatWriter

@InternalGhostApi
expect fun <T> ghostYamlInternalUseFlatReader(
    bytes: ByteArray,
    block: (GhostYamlFlatReader) -> T
): T

@InternalGhostApi
expect fun <T> ghostYamlInternalUseFlatWriter(
    block: (GhostYamlFlatWriter) -> T
): T

package com.ghost.serialization.proto

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.proto.GhostProtoJsonFlatReader

/**
 * Runs [block] with a pooled [GhostProtoJsonFlatReader] on the current thread,
 * mirroring [com.ghost.serialization.ghostInternalUseFlatReader] for JSON and
 * [com.ghost.serialization.yaml.ghostYamlInternalUseFlatReader] for YAML.
 */
@InternalGhostApi
expect fun <T> ghostProtoInternalUseFlatReader(
    bytes: ByteArray,
    offset: Int = 0,
    length: Int = bytes.size - offset,
    block: (GhostProtoJsonFlatReader) -> T
): T

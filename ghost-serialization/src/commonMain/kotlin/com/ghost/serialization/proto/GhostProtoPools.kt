package com.ghost.serialization.proto

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.proto.GhostProtoJsonFlatReader

/**
 * Runs [block] with a pooled [GhostProtoJsonFlatReader] on the current thread.
 *
 * Uses the same thread-local pooling pattern as `ghostInternalUseFlatReader`
 * for JSON and `ghostYamlInternalUseFlatReader` for YAML.
 */
@InternalGhostApi
expect fun <T> ghostProtoInternalUseFlatReader(
    bytes: ByteArray,
    offset: Int = 0,
    length: Int = bytes.size - offset,
    block: (GhostProtoJsonFlatReader) -> T
): T

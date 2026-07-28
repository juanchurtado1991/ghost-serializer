package com.ghost.serialization.proto

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.proto.GhostProtoJsonFlatReader
import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
private var cachedProtoReader: GhostProtoJsonFlatReader? = null

@InternalGhostApi
actual fun <T> ghostProtoInternalUseFlatReader(
    bytes: ByteArray,
    offset: Int,
    length: Int,
    block: (GhostProtoJsonFlatReader) -> T
): T {
    var reader = cachedProtoReader
    if (reader == null) {
        reader = GhostProtoJsonFlatReader(bytes)
        cachedProtoReader = reader
    } else {
        reader.resetSlice(bytes, offset, length)
    }
    return block(reader)
}

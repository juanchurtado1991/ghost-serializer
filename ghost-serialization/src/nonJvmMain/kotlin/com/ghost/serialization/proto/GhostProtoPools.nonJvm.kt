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
    val reader = cachedProtoReader
        ?: GhostProtoJsonFlatReader(rawData = bytes)
            .also { cachedProtoReader = it }

    reader.resetSlice(
        buffer = bytes,
        offset = offset,
        length = length
    )

    return block(reader).also { reader.requireEndOfInput() }
}

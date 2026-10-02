package com.ghost.serialization.proto

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.proto.GhostProtoJsonFlatReader

private val flatProtoReaderPool = ThreadLocal<GhostProtoJsonFlatReader>()

@InternalGhostApi
actual fun <T> ghostProtoInternalUseFlatReader(
    bytes: ByteArray,
    offset: Int,
    length: Int,
    block: (GhostProtoJsonFlatReader) -> T
): T {
    val reader = flatProtoReaderPool.get()
        ?: GhostProtoJsonFlatReader(rawData = bytes)
            .also { flatProtoReaderPool.set(it) }

    reader.resetSlice(
        buffer = bytes,
        offset = offset,
        length = length
    )

    return block(reader)
}

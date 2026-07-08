@file:OptIn(InternalGhostApi::class)

package com.ghost.protobuf

import com.ghost.serialization.parser.GhostProtoJsonFlatReader
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.releaseScratchBuffer
import okio.BufferedSource

object GhostProtobuf {

    inline fun <reified T : Any> deserialize(bytes: ByteArray): T {
        val reader = GhostProtoJsonFlatReader(bytes)
        val serializer = com.ghost.serialization.Ghost.resolveSerializer<T>()
        return serializer.deserialize(reader)
    }

    inline fun <reified T : Any> deserialize(json: String): T {
        return deserialize(json.encodeToByteArray())
    }

    inline fun <reified T : Any> deserialize(reader: GhostProtoJsonFlatReader): T {
        val serializer = com.ghost.serialization.Ghost.resolveSerializer<T>()
        return serializer.deserialize(reader)
    }

    inline fun <reified T : Any> deserialize(source: BufferedSource): T {
        source.request(Long.MAX_VALUE)
        val limit = source.buffer.size.toInt()
        val bytes = acquireScratchBuffer(limit)
        try {
            var offset = 0
            while (offset < limit) {
                val count = source.read(bytes, offset, limit - offset)
                if (count == -1) break
                offset += count
            }
            // Zero-allocation slice range initialization on reader
            val reader = GhostProtoJsonFlatReader(bytes)
            reader.limit = offset
            reader.position = 0
            val serializer = com.ghost.serialization.Ghost.resolveSerializer<T>()
            return serializer.deserialize(reader)
        } finally {
            releaseScratchBuffer(bytes)
        }
    }
}

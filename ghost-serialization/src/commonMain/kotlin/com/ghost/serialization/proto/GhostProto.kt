@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.proto.GhostProtoJsonFlatReader
import com.ghost.serialization.releaseScratchBuffer
import okio.BufferedSource
import kotlin.reflect.KClass

/**
 * Proto3-flavored entry point mirroring [Ghost]'s serialize/deserialize surface, routed through
 * the proto JSON mapping reader ([GhostProtoJsonFlatReader]) instead of the generic JSON one —
 * kept as its own facade so proto3-specific behavior (e.g. `google.protobuf.Any` handling) has
 * a home distinct from [Ghost]'s general-purpose API.
 */
object GhostProto {

    inline fun <reified T : Any> deserialize(bytes: ByteArray): T {
        return ghostProtoInternalUseFlatReader(bytes = bytes) { reader ->
            val serializer = Ghost.resolveSerializer<T>()
            serializer.deserialize(reader = reader)
        }
    }

    /**
     * Non-inline variant for contexts where the target type is only known as a [KClass] at
     * runtime (HTTP framework integrations — Retrofit `Type`, Ktor `TypeInfo`, Spring `Class<*>`).
     * Resolves through [registry] (defaults to the global [Ghost] singleton) rather than calling
     * [Ghost] directly, so tests can substitute a fake [GhostRegistry].
     *
     * @throws IllegalArgumentException if no [GhostSerializer] is registered for [clazz].
     */
    fun <T : Any> deserialize(
        bytes: ByteArray,
        clazz: KClass<T>,
        registry: GhostRegistry = Ghost
    ): T {
        val serializer = registry.getSerializer(clazz = clazz)
            ?: Ghost.throwError(message = Ghost.serializerNotFoundMessage(type = clazz.simpleName))
        return ghostProtoInternalUseFlatReader(bytes = bytes) { reader ->
            serializer.deserialize(reader = reader)
        }
    }

    inline fun <reified T : Any> deserialize(json: String): T {
        return deserialize(bytes = json.encodeToByteArray())
    }

    inline fun <reified T : Any> deserialize(reader: GhostProtoJsonFlatReader): T {
        val serializer = Ghost.resolveSerializer<T>()
        return serializer.deserialize(reader = reader)
    }

    inline fun <reified T : Any> deserialize(source: BufferedSource): T {
        source.request(byteCount = Long.MAX_VALUE)
        val limit = source.buffer.size.toInt()
        val bytes = acquireScratchBuffer(minSize = limit)
        try {
            var offset = 0
            while (offset < limit) {
                val count = source.read(sink = bytes, offset = offset, byteCount = limit - offset)
                if (count == -1) break
                offset += count
            }
            return ghostProtoInternalUseFlatReader(bytes = bytes, length = offset) { reader ->
                val serializer = Ghost.resolveSerializer<T>()
                serializer.deserialize(reader = reader)
            }
        } finally {
            releaseScratchBuffer(buffer = bytes)
        }
    }

    /**
     * Encodes [value] using its registered [GhostSerializer]. proto3 JSON mapping is already
     * applied by the KSP-generated serializer, so this delegates to [Ghost.encodeToBytes] —
     * kept here for a consistent `GhostProto.*` surface on both directions.
     */
    inline fun <reified T : Any> encodeToBytes(value: T): ByteArray =
        Ghost.encodeToBytes(value = value)

    /** Non-inline variant using a pre-resolved [serializer]; see [encodeToBytes]. */
    fun <T : Any> encodeToBytes(serializer: GhostSerializer<T>, value: T): ByteArray =
        Ghost.encodeToBytes(serializer = serializer, value = value)

    /** String variant of [encodeToBytes]; see its documentation. */
    inline fun <reified T : Any> encodeToString(value: T): String =
        Ghost.encodeToString(value = value)
}

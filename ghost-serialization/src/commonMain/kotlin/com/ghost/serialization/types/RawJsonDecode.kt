@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.types

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.ghostInternalUseFlatReader
import kotlin.reflect.KClass

/**
 * Typed deserialization for [RawJson] using zero-copy slice parsing on the flat byte reader.
 */
object RawJsonDecode {

    /** Parses this opaque JSON into [T] without copying the slice when it aliases a parent buffer. */
    inline fun <reified T : Any> decode(raw: RawJson): T =
        decode(raw = raw, clazz = T::class)

    /** Parses this opaque JSON with an explicit [serializer]. */
    fun <T : Any> decode(raw: RawJson, serializer: GhostSerializer<T>): T {
        if (raw.storageOffset == 0 && raw.storageLength == raw.storage.size) {
            return Ghost.deserialize(serializer = serializer, bytes = raw.storage)
        }
        return ghostInternalUseFlatReader(bytes = raw.storage) { reader ->
            reader.resetSlice(buffer = raw.storage, offset = raw.storageOffset, length = raw.storageLength)
            serializer.deserialize(reader = reader)
        }
    }

    /** Resolves [clazz] through [registry] (defaults to the global [Ghost] singleton) rather than
     * calling [Ghost] directly, so tests can substitute a fake [GhostRegistry]. */
    fun <T : Any> decode(
        raw: RawJson,
        clazz: KClass<T>,
        registry: GhostRegistry = Ghost
    ): T {
        val serializer = registry.getSerializer(clazz = clazz)
            ?: error(message = Ghost.serializerNotFoundMessage(type = clazz.simpleName))
        return decode(raw = raw, serializer = serializer)
    }
}

/** Parses this [RawJson] into [T] (zero-copy slice when captured from a response buffer). */
inline fun <reified T : Any> RawJson.decodeAs(): T = RawJsonDecode.decode(raw = this)

/** Parses this [RawJson] with an explicit [serializer]. */
fun <T : Any> RawJson.decodeAs(serializer: GhostSerializer<T>): T =
    RawJsonDecode.decode(raw = this, serializer = serializer)

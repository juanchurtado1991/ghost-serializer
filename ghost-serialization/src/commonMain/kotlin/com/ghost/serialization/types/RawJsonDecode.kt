@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.types

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.ghostInternalUseFlatReader
import kotlin.reflect.KClass

/**
 * Typed deserialization for [RawJson] using zero-copy slice parsing on the flat byte reader.
 */
object RawJsonDecode {

    /**
     * Parses this opaque JSON into [T] without copying the slice when it aliases a parent buffer.
     */
    inline fun <reified T : Any> decode(raw: RawJson): T =
        decode(raw, T::class)

    /**
     * Parses this opaque JSON with an explicit [serializer].
     */
    fun <T : Any> decode(raw: RawJson, serializer: GhostSerializer<T>): T {
        if (raw.storageOffset == 0 && raw.storageLength == raw.storage.size) {
            return Ghost.deserialize(serializer, raw.storage)
        }
        return ghostInternalUseFlatReader(raw.storage, raw.endExclusive) { reader ->
            reader.resetSlice(raw.storage, raw.storageOffset, raw.storageLength)
            serializer.deserialize(reader)
        }
    }

    fun <T : Any> decode(raw: RawJson, clazz: KClass<T>): T {
        val serializer = Ghost.getSerializer(clazz)
            ?: error("No GhostSerializer registered for ${clazz.simpleName}")
        @Suppress("UNCHECKED_CAST")
        return decode(raw, serializer as GhostSerializer<T>)
    }
}

/** Parses this [RawJson] into [T] (zero-copy slice when captured from a response buffer). */
inline fun <reified T : Any> RawJson.decodeAs(): T = RawJsonDecode.decode(this)

/** Parses this [RawJson] with an explicit [serializer]. */
fun <T : Any> RawJson.decodeAs(serializer: GhostSerializer<T>): T = RawJsonDecode.decode(this, serializer)

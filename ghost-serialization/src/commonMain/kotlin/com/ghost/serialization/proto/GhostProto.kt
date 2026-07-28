@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto

import com.ghost.serialization.parser.common.*
import com.ghost.serialization.parser.bytes.*
import com.ghost.serialization.parser.strings.*
import com.ghost.serialization.parser.streaming.*
import com.ghost.serialization.parser.proto.*
import com.ghost.serialization.Ghost
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.releaseScratchBuffer
import com.ghost.serialization.parser.proto.GhostProtoJsonFlatReader
import okio.BufferedSource
import kotlin.reflect.KClass

object GhostProto {

    inline fun <reified T : Any> deserialize(bytes: ByteArray): T {
        return ghostProtoInternalUseFlatReader(bytes) { reader ->
            val serializer = Ghost.resolveSerializer<T>()
            serializer.deserialize(reader)
        }
    }

    /**
     * Non-inline variant for contexts where the target type is only known as a [KClass] at
     * runtime (HTTP framework integrations — Retrofit `Type`, Ktor `TypeInfo`, Spring `Class<*>`).
     *
     * @throws IllegalArgumentException if no [GhostSerializer] is registered for [clazz].
     */
    fun <T : Any> deserialize(bytes: ByteArray, clazz: KClass<T>): T {
        val serializer = Ghost.getSerializer(clazz)
            ?: Ghost.throwError("${Ghost.NOT_FOUND} ${clazz.simpleName}. ${Ghost.MISSING_ANN}")
        return ghostProtoInternalUseFlatReader(bytes) { reader ->
            serializer.deserialize(reader)
        }
    }

    /**
     * Encodes [value] using its registered [GhostSerializer]. proto3 JSON mapping (int64
     * quoting, Base64 `bytes`, default-value omission) is already applied by the KSP-generated
     * serializer for `@GhostProtoSerialization` classes, so this simply delegates to
     * [Ghost.encodeToBytes] — kept here so callers have one consistent `GhostProto.*`
     * surface for both directions instead of mixing it with `Ghost.*`.
     */
    inline fun <reified T : Any> encodeToBytes(value: T): ByteArray = Ghost.encodeToBytes(value)

    /** Non-inline variant using a pre-resolved [serializer]; see [encodeToBytes]. */
    fun <T : Any> encodeToBytes(serializer: GhostSerializer<T>, value: T): ByteArray =
        Ghost.encodeToBytes(serializer, value)

    /** String variant of [encodeToBytes]; see its documentation. */
    inline fun <reified T : Any> encodeToString(value: T): String = Ghost.encodeToString(value)

    inline fun <reified T : Any> deserialize(json: String): T {
        return deserialize(json.encodeToByteArray())
    }

    inline fun <reified T : Any> deserialize(reader: GhostProtoJsonFlatReader): T {
        val serializer = Ghost.resolveSerializer<T>()
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
            return ghostProtoInternalUseFlatReader(bytes, length = offset) { reader ->
                val serializer = Ghost.resolveSerializer<T>()
                serializer.deserialize(reader)
            }
        } finally {
            releaseScratchBuffer(bytes)
        }
    }
}

package com.ghost.serialization.ktor

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.releaseScratchBuffer
import com.ghost.serialization.parser.GhostProtoJsonFlatReader
import io.ktor.http.ContentType
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.ContentConverter
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset
import kotlin.reflect.KClass

/**
 * Ktor [ContentConverter] for proto3 JSON mapping (`@GhostProtoSerialization`).
 *
 * Differs from [GhostContentConverter] only on the read path: request/response bodies are
 * parsed through [GhostProtoJsonFlatReader], which additionally accepts quoted-or-bare
 * int64/uint64, lenient int32 (rejects fractional values), and quoted `"NaN"`/`"Infinity"`
 * literals per proto3 JSON rules. `serializeNullable` reuses [Ghost.encodeToBytes] since wire
 * correctness on write is already generated into the `@GhostProtoSerialization` serializer's
 * own `serialize()` method.
 *
 * ```kotlin
 * install(ContentNegotiation) { ghostProto() }
 * ```
 */
@OptIn(InternalGhostApi::class)
class GhostProtoContentConverter(
    private val configurer: ((GhostProtoJsonFlatReader) -> Unit)? = null
) : ContentConverter {

    @Suppress("UNCHECKED_CAST")
    override suspend fun serializeNullable(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?
    ): OutgoingContent? {
        if (value == null) return null
        val clazz = typeInfo.type

        val serializer = typeInfo.kotlinType?.let { Ghost.getSerializer(it) }
            ?: Ghost.getSerializer(clazz as KClass<Any>)
            ?: return null

        val bytes = Ghost.encodeToBytes(serializer, value)

        return ByteArrayContent(bytes, contentType)
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun deserialize(
        charset: Charset,
        typeInfo: TypeInfo,
        content: ByteReadChannel
    ): Any? {
        val serializer = typeInfo.kotlinType?.let { Ghost.getSerializer(it) }
            ?: Ghost.getSerializer(typeInfo.type as KClass<Any>)
            ?: return null

        var scratch = acquireScratchBuffer(BUFFER_SIZE)

        try {
            var offset = 0
            while (true) {
                if (offset == scratch.size) {
                    val grown = acquireScratchBuffer(scratch.size * 2)
                    scratch.copyInto(grown, 0, 0, offset)
                    releaseScratchBuffer(scratch)
                    scratch = grown
                }

                val read = content.readAvailable(scratch, offset, scratch.size - offset)
                if (read == -1) break
                offset += read
            }

            val reader = GhostProtoJsonFlatReader(scratch)
            reader.limit = offset
            configurer?.invoke(reader)

            return serializer.deserialize(reader)
        } finally {
            releaseScratchBuffer(scratch)
        }
    }

    companion object {
        private const val BUFFER_SIZE = 524288
    }
}

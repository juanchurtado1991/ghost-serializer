package com.ghost.serialization.ktor

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.proto.GhostProtoJsonFlatReader
import com.ghost.serialization.proto.ghostProtoInternalUseFlatReader
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
 * Read path parses via [GhostProtoJsonFlatReader] for proto3 JSON leniency (quoted-or-bare
 * int64, lenient int32, quoted `"NaN"`/`"Infinity"`). Encoding reuses [Ghost.encodeToBytes]
 * since proto3 wire correctness is generated into the serializer's own `serialize()`.
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
    override suspend fun serialize(
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

        return GhostKtorBuffers.readToScratch(content) { scratch, offset ->
            ghostProtoInternalUseFlatReader(scratch, length = offset) { reader ->
                configurer?.invoke(reader)
                serializer.deserialize(reader)
            }
        }
    }
}

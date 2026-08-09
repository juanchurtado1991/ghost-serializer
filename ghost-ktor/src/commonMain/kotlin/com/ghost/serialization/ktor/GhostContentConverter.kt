package com.ghost.serialization.ktor

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.ghostInternalUseFlatReader
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import io.ktor.http.ContentType
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.ContentConverter
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset
import kotlin.reflect.KClass


@OptIn(InternalGhostApi::class)
class GhostContentConverter(
    private val configurer: ((GhostJsonFlatReader) -> Unit)? = null
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
            ghostInternalUseFlatReader(scratch, offset) { reader ->
                configurer?.invoke(reader)
                serializer.deserialize(reader)
            }
        }
    }
}

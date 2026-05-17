package com.ghost.serialization.ktor

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.ghostInternalEncodeWithWriter
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.ByteArrayContent
import io.ktor.serialization.ContentConverter
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import kotlin.reflect.KClass

@OptIn(InternalGhostApi::class)
class GhostContentConverter : ContentConverter {

    override suspend fun serializeNullable(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?
    ): OutgoingContent? {
        if (value == null) return null
        val clazz = typeInfo.type

        @Suppress("UNCHECKED_CAST")
        val serializer = Ghost.getSerializer(clazz as KClass<Any>)
            ?: return null

        val bytes = ghostInternalEncodeWithWriter { writer ->
            serializer.serialize(writer, value)
        }
        return ByteArrayContent(bytes, contentType)
    }

    override suspend fun deserialize(
        charset: Charset,
        typeInfo: TypeInfo,
        content: ByteReadChannel
    ): Any {
        val bytes = content.readRemaining().readBytes()
        return Ghost.decodeFromBytes(bytes, typeInfo.type)
    }
}

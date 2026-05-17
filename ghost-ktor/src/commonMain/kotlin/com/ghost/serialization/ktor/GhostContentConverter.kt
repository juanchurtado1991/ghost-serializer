package com.ghost.serialization.ktor

import com.ghost.serialization.Ghost
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.content.ByteArrayContent
import io.ktor.serialization.ContentConverter
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset
import okio.Buffer
import okio.BufferedSource
import kotlin.reflect.KClass

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

        val buffer = Buffer()
        serializer.serialize(buffer, value)
        val bytes = buffer.readByteArray()
        return ByteArrayContent(bytes, contentType)
    }

    override suspend fun deserialize(
        charset: Charset,
        typeInfo: TypeInfo,
        content: ByteReadChannel
    ): Any? {
        val source: BufferedSource = content.toBufferedSource()
        return Ghost.decodeFromSource(source, typeInfo.type)
    }
}

/** Platform bridge: converts a [ByteReadChannel]
 * to an [okio.BufferedSource] without copying bytes. */
internal expect suspend fun ByteReadChannel.toBufferedSource(): BufferedSource

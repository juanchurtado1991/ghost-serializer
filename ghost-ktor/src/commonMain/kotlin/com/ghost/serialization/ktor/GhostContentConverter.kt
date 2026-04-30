@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.ktor

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.GhostJsonReader
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.serialization.ContentConverter
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset
import okio.Buffer

class GhostContentConverter : ContentConverter {
    
    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?
    ): io.ktor.http.content.OutgoingContent? {
        if (value == null) return null
        val serializer = Ghost.getSerializer(typeInfo.kotlinType ?: return null) ?: return null

        val buffer = Buffer()
        serializer.serialize(buffer, value)
        return TextContent(
            text = buffer.readUtf8(),
            contentType = contentType
        )
    }

    override suspend fun deserialize(
        charset: Charset,
        typeInfo: TypeInfo,
        content: ByteReadChannel
    ): Any? {
        val serializer = Ghost.getSerializer(typeInfo.kotlinType ?: return null) ?: return null
        val reader = createReader(content)
        return serializer.deserialize(reader)
    }
}

internal expect suspend fun createReader(channel: ByteReadChannel): GhostJsonReader

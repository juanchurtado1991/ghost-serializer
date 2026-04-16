package com.ghost.serialization.ktor

import com.ghost.serialization.Ghost
import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import okio.Buffer
import okio.BufferedSource
import okio.use
import com.ghost.serialization.core.parser.GhostJsonReader
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray

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
        return io.ktor.http.content.TextContent(
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
        
        // Ktor 3.0 uses ByteReadChannel. Read all content into a buffer for Ghost.
        val bytes = content.readRemaining().readByteArray()
        return serializer.deserialize(GhostJsonReader(bytes))
    }
}

package com.ghost.serialization.ktor

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.ghostInternalUseFlatReader
import com.ghost.serialization.releaseScratchBuffer
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.GhostJsonFlatReader
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
    override suspend fun serializeNullable(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?
    ): OutgoingContent? {
        if (value == null) return null
        val clazz = typeInfo.type


        val serializer = Ghost
            .getSerializer(clazz as KClass<Any>)
            ?: return null

        val bytes = Ghost.encodeToBytes(serializer, value)

        return ByteArrayContent(bytes, contentType)
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun deserialize(
        charset: Charset,
        typeInfo: TypeInfo,
        content: ByteReadChannel
    ): Any {
        var scratch =
            acquireScratchBuffer(BUFFER_SIZE)

        try {
            var offset = 0
            while (true) {
                if (offset == scratch.size) {
                    val grown =
                        acquireScratchBuffer(scratch.size * 2)

                    scratch.copyInto(
                        grown,
                        0,
                        0,
                        offset
                    )

                    releaseScratchBuffer(scratch)
                    scratch = grown
                }

                val read = content.readAvailable(
                    scratch,
                    offset,
                    scratch.size - offset
                )

                if (read == -1) break
                offset += read
            }

            return ghostInternalUseFlatReader(scratch, offset) { reader ->
                configurer?.invoke(reader)
                val serializer = Ghost.getSerializer(typeInfo.type as KClass<Any>)
                    ?: Ghost.throwError("${Ghost.NOT_FOUND} ${typeInfo.type.simpleName}. ${Ghost.MISSING_ANN}")

                serializer.deserialize(reader)
            }
        } finally {
            releaseScratchBuffer(scratch)
        }
    }

    companion object {
        private const val BUFFER_SIZE = 524288
    }
}

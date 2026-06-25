package com.ghost.serialization.yaml.ktor

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.releaseScratchBuffer
import com.ghost.serialization.yaml.parser.GhostYamlFlatReader
import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import com.ghost.serialization.yaml.ghostYamlInternalUseFlatReader
import com.ghost.serialization.yaml.ghostYamlInternalUseFlatWriter
import com.ghost.serialization.encodeToYamlBytes
import io.ktor.http.ContentType
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.ContentConverter
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset
import kotlin.reflect.KClass

@OptIn(InternalGhostApi::class)
class GhostYamlContentConverter(
    private val configurer: ((GhostYamlFlatReader) -> Unit)? = null
) : ContentConverter {

    @Suppress("UNCHECKED_CAST")
    override suspend fun serializeNullable(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?
    ): OutgoingContent? {
        if (value == null) return null
        val clazz = value::class

        val serializer = Ghost.getSerializer(clazz as KClass<Any>)
            ?: return null

        if (serializer !is GhostYamlSerializer<*>) {
            return null
        }

        val yamlSerializer = serializer as GhostYamlSerializer<Any>
        val bytes = ghostYamlInternalUseFlatWriter { writer ->
            yamlSerializer.serialize(writer, value)
            writer.buffer.toByteArray()
        }

        return ByteArrayContent(bytes, contentType)
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun deserialize(
        charset: Charset,
        typeInfo: TypeInfo,
        content: ByteReadChannel
    ): Any {
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

            val bytesToParse = if (offset == scratch.size) {
                scratch
            } else {
                scratch.copyOf(offset)
            }

            return ghostYamlInternalUseFlatReader(bytesToParse) { reader ->
                configurer?.invoke(reader)
                val serializer = Ghost.getSerializer(typeInfo.type as KClass<Any>)
                    ?: Ghost.throwError("Serializer not found for ${typeInfo.type.simpleName}")

                if (serializer !is GhostYamlSerializer<*>) {
                    Ghost.throwError("Serializer for ${typeInfo.type.simpleName} does not implement GhostYamlSerializer")
                }

                val yamlSerializer = serializer as GhostYamlSerializer<Any>
                yamlSerializer.deserialize(reader)
            }
        } finally {
            releaseScratchBuffer(scratch)
        }
    }

    companion object {
        private const val BUFFER_SIZE = 524288
    }
}

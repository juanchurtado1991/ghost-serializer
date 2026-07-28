package com.ghost.serialization.ktor

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.releaseScratchBuffer
import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import com.ghost.serialization.yaml.ghostYamlInternalUseFlatReader
import com.ghost.serialization.yaml.ghostYamlInternalUseFlatWriter
import io.ktor.http.ContentType
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.ContentConverter
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.readAvailable
import kotlin.reflect.KClass

/**
 * Ktor [ContentConverter] for YAML-backed types ([GhostYamlSerializer]).
 *
 * ```kotlin
 * install(ContentNegotiation) { ghostYaml() }
 * ```
 */
@OptIn(InternalGhostApi::class)
class GhostYamlContentConverter(
    private val configurer: ((GhostYamlFlatReader) -> Unit)? = null
) : ContentConverter {

    @Suppress("UNCHECKED_CAST")
    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?
    ): OutgoingContent? {
        if (value == null) return null

        val serializer = typeInfo.kotlinType?.let { Ghost.getSerializer(it) }
            ?: Ghost.getSerializer(value::class as KClass<Any>)
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
    ): Any? {
        val serializer = typeInfo.kotlinType?.let { Ghost.getSerializer(it) }
            ?: Ghost.getSerializer(typeInfo.type as KClass<Any>)
            ?: return null

        if (serializer !is GhostYamlSerializer<*>) {
            return null
        }

        val yamlSerializer = serializer as GhostYamlSerializer<Any>
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

            val bytesToParse = if (offset == scratch.size) scratch else scratch.copyOf(offset)

            return ghostYamlInternalUseFlatReader(bytesToParse) { reader ->
                configurer?.invoke(reader)
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

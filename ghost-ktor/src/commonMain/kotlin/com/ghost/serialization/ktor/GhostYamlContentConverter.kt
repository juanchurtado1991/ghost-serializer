package com.ghost.serialization.ktor

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
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
        val bytes = ghostYamlInternalUseFlatWriter { writer, buffer ->
            yamlSerializer.serialize(writer, value)
            buffer.toByteArray()
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
        return GhostKtorBuffers.readToScratch(content) { scratch, offset ->
            val bytesToParse = if (offset == scratch.size) scratch else scratch.copyOf(offset)
            ghostYamlInternalUseFlatReader(bytesToParse) { reader ->
                configurer?.invoke(reader)
                yamlSerializer.deserialize(reader)
            }
        }
    }
}

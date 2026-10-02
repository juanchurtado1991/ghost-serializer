package com.ghost.serialization.ktor

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import com.ghost.serialization.yaml.ghostYamlInternalUseFlatReader
import com.ghost.serialization.ghostYamlEncodeToBytes
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
 * Resolves serializers through [registry] (defaults to the global [Ghost] singleton) rather than
 * calling [Ghost] directly, so tests can substitute a fake [GhostRegistry] — only for the
 * plain-[KClass] fallback path, since generic-type resolution (`List<T>`/`Set<T>`/`Map<K, V>`
 * via [TypeInfo.kotlinType]) always goes through [Ghost] itself, which is the only place that
 * capability exists.
 *
 * ```kotlin
 * install(ContentNegotiation) { ghostYaml() }
 * ```
 */
@OptIn(InternalGhostApi::class)
class GhostYamlContentConverter(
    private val registry: GhostRegistry = Ghost,
    private val configurer: ((GhostYamlFlatReader) -> Unit)? = null
) : ContentConverter {

    @Suppress("UNCHECKED_CAST")
    override suspend fun deserialize(
        charset: Charset,
        typeInfo: TypeInfo,
        content: ByteReadChannel
    ): Any? {
        val serializer = resolveKtorSerializer(
            typeInfo = typeInfo,
            fallbackClass = typeInfo.type,
            registry = registry
        ) ?: return null

        if (serializer !is GhostYamlSerializer<*>) {
            return null
        }

        val yamlSerializer = serializer as GhostYamlSerializer<Any>
        return GhostKtorBuffers.readToScratch(content) { scratch, offset ->
            val bytesToParse = if (offset == scratch.size) scratch else scratch.copyOf(offset)
            ghostYamlInternalUseFlatReader(bytes = bytesToParse) { reader ->
                configurer?.invoke(reader)
                yamlSerializer.deserialize(reader)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?
    ): OutgoingContent? {
        if (value == null) return null

        val serializer = resolveKtorSerializer(
            typeInfo = typeInfo,
            fallbackClass = value::class,
            registry = registry
        ) ?: return null

        if (serializer !is GhostYamlSerializer<*>) {
            return null
        }

        val yamlSerializer = serializer as GhostYamlSerializer<Any>
        val bytes = ghostYamlEncodeToBytes(
            serializer = yamlSerializer,
            value = value
        )
        return ByteArrayContent(bytes, contentType)
    }
}

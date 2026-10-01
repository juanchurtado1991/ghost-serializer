package com.ghost.serialization.ktor

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.ghostInternalUseFlatReader
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.ContentConverter
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset
import kotlin.reflect.KClass

/**
 * Resolves serializers through [registry] (defaults to the global [Ghost] singleton) rather than
 * calling [Ghost] directly, so tests can substitute a fake [GhostRegistry] — only for the
 * plain-[KClass] fallback path, since generic-type resolution (`List<T>`/`Set<T>`/`Map<K, V>`
 * via [TypeInfo.kotlinType]) always goes through [Ghost] itself, which is the only place that
 * capability exists.
 */
@OptIn(InternalGhostApi::class)
class GhostContentConverter(
    private val registry: GhostRegistry = Ghost,
    private val configurer: ((GhostJsonFlatReader) -> Unit)? = null
) : ContentConverter {

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

        return GhostKtorBuffers.readToScratch(content) { scratch, offset ->
            ghostInternalUseFlatReader(bytes = scratch, limit = offset) { reader ->
                configurer?.invoke(reader)
                serializer.deserialize(reader)
            }
        }
    }

    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?
    ): OutgoingContent? = encodeKtorContent(
        value = value,
        contentType = contentType,
        typeInfo = typeInfo,
        registry = registry
    )
}

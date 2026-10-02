package com.ghost.serialization.ktor

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.parser.proto.GhostProtoJsonFlatReader
import com.ghost.serialization.proto.ghostProtoInternalUseFlatReader
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.ContentConverter
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset
import kotlin.reflect.KClass


/**
 * Ktor [ContentConverter] for proto3 JSON mapping (`@GhostProtoSerialization`).
 *
 * Read path parses via [GhostProtoJsonFlatReader] for proto3 JSON leniency (quoted-or-bare
 * int64, lenient int32, quoted `"NaN"`/`"Infinity"`). Encoding reuses [Ghost.encodeToBytes]
 * since proto3 wire correctness is generated into the serializer's own `serialize()`.
 *
 * Resolves serializers through [registry] (defaults to the global [Ghost] singleton) rather than
 * calling [Ghost] directly, so tests can substitute a fake [GhostRegistry] — only for the
 * plain-[KClass] fallback path, since generic-type resolution (`List<T>`/`Set<T>`/`Map<K, V>`
 * via [TypeInfo.kotlinType]) always goes through [Ghost] itself, which is the only place that
 * capability exists.
 *
 * ```kotlin
 * install(ContentNegotiation) { ghostProto() }
 * ```
 */
@OptIn(InternalGhostApi::class)
class GhostProtoContentConverter(
    private val registry: GhostRegistry = Ghost,
    private val configurer: ((GhostProtoJsonFlatReader) -> Unit)? = null
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
            ghostProtoInternalUseFlatReader(bytes = scratch, length = offset) { reader ->
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

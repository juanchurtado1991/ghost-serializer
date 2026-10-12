package com.ghost.serialization.ktor

import com.ghost.serialization.Ghost
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import io.ktor.http.ContentType
import io.ktor.http.withCharset
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.charsets.encodeToByteArray
import kotlin.reflect.KClass

/** JSON/proto3 encode path: serializer lookup, `Ghost.encodeToBytes`, wrap as [ByteArrayContent]. */
internal fun encodeKtorContent(
    value: Any?,
    contentType: ContentType,
    typeInfo: TypeInfo,
    registry: GhostRegistry,
    charset: Charset = Charsets.UTF_8
): OutgoingContent? {
    if (value == null) return null
    val serializer = resolveKtorSerializer(
        typeInfo = typeInfo,
        fallbackClass = typeInfo.type,
        registry = registry
    ) ?: return null
    return ghostKtorBytesContent(
        utf8Bytes = Ghost.encodeToBytes(serializer = serializer, value = value),
        contentType = contentType,
        charset = charset
    )
}

/**
 * Wraps UTF-8 [utf8Bytes] as content. For a negotiated non-UTF-8 [charset] the text is re-encoded with it and the
 * content type says so, as kotlinx.serialization's converter does.
 */
internal fun ghostKtorBytesContent(
    utf8Bytes: ByteArray,
    contentType: ContentType,
    charset: Charset
): OutgoingContent {
    if (charset == Charsets.UTF_8) return ByteArrayContent(utf8Bytes, contentType)
    val encoded = charset.newEncoder().encodeToByteArray(utf8Bytes.decodeToString())
    return ByteArrayContent(encoded, contentType.withCharset(charset))
}

/**
 * Shared steps of Ghost's three Ktor content converters (JSON/proto/YAML), which only differ in
 * which reader/writer they run the resolved serializer through.
 *
 * Generic types (`List<T>`/`Set<T>`/`Map<K, V>`) resolve through [Ghost] itself via
 * [TypeInfo.kotlinType] — only [Ghost] has that capability; the plain-class fallback goes
 * through [registry].
 */
@Suppress("UNCHECKED_CAST")
internal fun resolveKtorSerializer(
    typeInfo: TypeInfo,
    fallbackClass: KClass<*>,
    registry: GhostRegistry
): GhostSerializer<Any>? = typeInfo.kotlinType?.let { Ghost.getSerializer(it) }
    ?: registry.getSerializer(fallbackClass as KClass<Any>)

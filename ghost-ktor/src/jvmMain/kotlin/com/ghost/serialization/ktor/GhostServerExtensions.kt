package com.ghost.serialization.ktor

import com.ghost.serialization.proto.GhostProto
import com.ghost.serialization.Ghost
import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import com.ghost.serialization.yaml.ghostYamlInternalUseFlatWriter
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

@PublishedApi
internal const val ERROR_PREFIX = "Ghost serializer not found for class "
@PublishedApi
internal const val ERROR_SUFFIX = ". Make sure it is annotated with @GhostSerialization."

/**
 * Serializes the [value] directly using Ghost Serializer and responds,
 * bypassing the ContentNegotiation pipeline entirely to maximize throughput.
 */
suspend inline fun <reified T : Any> ApplicationCall.respondGhost(
    value: T,
    status: HttpStatusCode = HttpStatusCode.OK
) {
    val serializer = Ghost.getSerializer(T::class)
        ?: throw IllegalArgumentException("$ERROR_PREFIX${T::class.simpleName}$ERROR_SUFFIX")

    val bytes = Ghost.encodeToBytes(serializer, value)
    respond(ByteArrayContent(bytes, ContentType.Application.Json, status))
}

/**
 * Proto3-JSON variant of [respondGhost] — named for discoverability alongside [respondGhost]
 * and `bodyGhostProto`. Encoding is identical either way: proto3 wire correctness (int64
 * quoting, Base64 bytes, default-value omission) is generated into the
 * `@GhostProtoSerialization` serializer's own `serialize()` method, not a separate writer.
 */
suspend inline fun <reified T : Any> ApplicationCall.respondGhostProto(
    value: T,
    status: HttpStatusCode = HttpStatusCode.OK
) {
    val bytes = GhostProto.encodeToBytes(value)
    respond(ByteArrayContent(bytes, ContentType.Application.Json, status))
}

/**
 * YAML variant of [respondGhost] — serializes through [GhostYamlSerializer] and responds with
 * `application/yaml`.
 */
suspend inline fun <reified T : Any> ApplicationCall.respondGhostYaml(
    value: T,
    status: HttpStatusCode = HttpStatusCode.OK
) {
    val serializer = Ghost.getSerializer(T::class)
        ?: throw IllegalArgumentException("$ERROR_PREFIX${T::class.simpleName}$ERROR_SUFFIX")
    if (serializer !is GhostYamlSerializer<*>) {
        throw IllegalArgumentException(
            "Serializer for ${T::class.simpleName} does not implement GhostYamlSerializer"
        )
    }
    @Suppress("UNCHECKED_CAST")
    val yamlSerializer = serializer as GhostYamlSerializer<T>
    val bytes = ghostYamlInternalUseFlatWriter { writer ->
        yamlSerializer.serialize(writer, value)
        writer.buffer.toByteArray()
    }
    respond(ByteArrayContent(bytes, ContentType("application", "yaml"), status))
}

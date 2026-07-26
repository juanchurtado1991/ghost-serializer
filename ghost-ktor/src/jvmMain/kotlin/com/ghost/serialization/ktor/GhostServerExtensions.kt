package com.ghost.serialization.ktor

import com.ghost.protobuf.GhostProtobuf
import com.ghost.serialization.Ghost
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlin.reflect.KClass

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
    val bytes = GhostProtobuf.encodeToBytes(value)
    respond(ByteArrayContent(bytes, ContentType.Application.Json, status))
}

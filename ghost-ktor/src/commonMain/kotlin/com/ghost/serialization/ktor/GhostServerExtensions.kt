package com.ghost.serialization.ktor

import com.ghost.serialization.Ghost
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlin.reflect.KClass

private const val ERROR_PREFIX = "Ghost serializer not found for class "
private const val ERROR_SUFFIX = ". Make sure it is annotated with @GhostSerializable."

/**
 * Serializes the [value] directly using Ghost Serializer and responds,
 * bypassing the ContentNegotiation pipeline entirely to maximize throughput.
 */
suspend fun ApplicationCall.respondGhost(
    value: Any,
    status: HttpStatusCode = HttpStatusCode.OK
) {
    @Suppress("UNCHECKED_CAST")
    val serializer = Ghost.getSerializer(value::class as KClass<Any>)
        ?: throw IllegalArgumentException("$ERROR_PREFIX${value::class.simpleName}$ERROR_SUFFIX")
    
    val bytes = Ghost.encodeToBytes(serializer, value)
    respond(ByteArrayContent(bytes, ContentType.Application.Json, status))
}

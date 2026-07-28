package com.ghost.serialization.ktor

import com.ghost.serialization.Ghost
import com.ghost.serialization.decodeFromYaml
import com.ghost.serialization.proto.GhostProto
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse


@PublishedApi
internal const val CLIENT_ERROR_PREFIX = "Ghost serializer not found for class "

@PublishedApi
internal const val CLIENT_ERROR_SUFFIX = ". Make sure it is annotated with @GhostSerialization."

/**
 * Deserializes the response body directly using Ghost, bypassing Ktor Client's
 * ContentNegotiation pipeline.
 */
suspend inline fun <reified T : Any> HttpResponse.bodyGhost(): T {
    val bytes = this.body<ByteArray>()
    val serializer = Ghost.getSerializer(T::class)
        ?: throw IllegalArgumentException("$CLIENT_ERROR_PREFIX${T::class.simpleName}$CLIENT_ERROR_SUFFIX")
    return Ghost.deserialize(serializer, bytes)
}

/**
 * Proto3-JSON variant of [bodyGhost] for `@GhostProtoSerialization` types — parses through
 * [com.ghost.serialization.parser.proto.GhostProtoJsonFlatReader] (quoted-or-bare int64, lenient
 * int32, quoted `NaN`/`Infinity`) instead of the plain flat reader.
 */
suspend inline fun <reified T : Any> HttpResponse.bodyGhostProto(): T {
    val bytes = this.body<ByteArray>()
    return GhostProto.deserialize(bytes, T::class)
}

/**
 * YAML variant of [bodyGhost] for types whose serializer implements
 * [com.ghost.serialization.yaml.contract.GhostYamlSerializer].
 */
suspend inline fun <reified T : Any> HttpResponse.bodyGhostYaml(): T {
    val bytes = this.body<ByteArray>()
    return Ghost.decodeFromYaml(bytes)
}

package com.ghost.serialization.ktor

import com.ghost.serialization.parser.GhostJsonFlatReader
import com.ghost.serialization.parser.GhostProtoJsonFlatReader
import io.ktor.http.ContentType
import io.ktor.serialization.Configuration

/**
 * Extension to register Ghost as the content negotiator in Ktor.
 */
fun Configuration.ghost(
    contentType: ContentType = ContentType.Application.Json,
    configurer: ((GhostJsonFlatReader) -> Unit)? = null
) {
    register(contentType, GhostContentConverter(configurer))
}

/**
 * Extension to register Ghost's proto3-JSON mapping ([GhostProtoContentConverter]) as the
 * content negotiator in Ktor — use for APIs backed by `@GhostProtoSerialization` types.
 */
fun Configuration.ghostProto(
    contentType: ContentType = ContentType.Application.Json,
    configurer: ((GhostProtoJsonFlatReader) -> Unit)? = null
) {
    register(contentType, GhostProtoContentConverter(configurer))
}

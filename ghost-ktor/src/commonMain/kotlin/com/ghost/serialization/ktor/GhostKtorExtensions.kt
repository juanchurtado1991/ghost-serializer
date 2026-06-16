package com.ghost.serialization.ktor

import com.ghost.serialization.parser.GhostJsonFlatReader
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

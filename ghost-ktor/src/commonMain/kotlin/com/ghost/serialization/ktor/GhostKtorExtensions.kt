package com.ghost.serialization.ktor

import io.ktor.http.ContentType
import io.ktor.serialization.Configuration

/**
 * Extension to register Ghost as the content negotiator in Ktor.
 */
fun Configuration.ghost(
    contentType: ContentType = ContentType.Application.Json
) {
    register(contentType, GhostContentConverter())
}

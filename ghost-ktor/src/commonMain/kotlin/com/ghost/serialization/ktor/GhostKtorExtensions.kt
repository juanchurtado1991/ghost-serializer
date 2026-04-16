package com.ghost.serialization.ktor

import io.ktor.serialization.*
import io.ktor.http.*

/**
 * Extension to register Ghost as the content negotiator in Ktor.
 */
fun Configuration.ghost(
    contentType: ContentType = ContentType.Application.Json
) {
    register(contentType, GhostContentConverter())
}

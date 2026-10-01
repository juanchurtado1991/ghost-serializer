package com.ghost.serialization.ktor

import io.ktor.http.ContentType

/** Shared content-type constants for Ghost's Ktor YAML negotiation (client, server, and tests). */
@PublishedApi
internal object GhostKtorMediaTypes {
    const val TYPE_APPLICATION = "application"
    const val SUBTYPE_YAML = "yaml"
    const val JSON_CONTENT_TYPE = "application/json"
    const val YAML_CONTENT_TYPE = "application/yaml"

    val APPLICATION_YAML = ContentType(TYPE_APPLICATION, SUBTYPE_YAML)
}

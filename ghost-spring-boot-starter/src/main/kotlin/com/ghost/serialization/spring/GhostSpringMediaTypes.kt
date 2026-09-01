package com.ghost.serialization.spring

import org.springframework.http.MediaType
import org.springframework.util.MimeType

/** Shared media-type / MIME constants for Ghost Spring MVC and WebFlux converters. */
internal object GhostSpringMediaTypes {
    const val TYPE_APPLICATION = "application"
    const val TYPE_TEXT = "text"

    const val SUBTYPE_JSON_SUFFIX = "*+json"
    const val SUBTYPE_YAML = "yaml"
    const val SUBTYPE_X_YAML = "x-yaml"
    const val SUBTYPE_X_NDJSON = "x-ndjson"
    const val SUBTYPE_NDJSON_TOKEN = "ndjson"

    val APPLICATION_JSON_SUFFIX: MediaType = MediaType(TYPE_APPLICATION, SUBTYPE_JSON_SUFFIX)
    val APPLICATION_YAML: MediaType = MediaType(TYPE_APPLICATION, SUBTYPE_YAML)
    val APPLICATION_X_YAML: MediaType = MediaType(TYPE_APPLICATION, SUBTYPE_X_YAML)
    val TEXT_YAML: MediaType = MediaType(TYPE_TEXT, SUBTYPE_YAML)

    val MIME_APPLICATION_YAML: MimeType = MimeType(TYPE_APPLICATION, SUBTYPE_YAML)
    val MIME_APPLICATION_X_YAML: MimeType = MimeType(TYPE_APPLICATION, SUBTYPE_X_YAML)
    val MIME_TEXT_YAML: MimeType = MimeType(TYPE_TEXT, SUBTYPE_YAML)
    val MIME_APPLICATION_X_NDJSON: MimeType = MimeType(TYPE_APPLICATION, SUBTYPE_X_NDJSON)
}

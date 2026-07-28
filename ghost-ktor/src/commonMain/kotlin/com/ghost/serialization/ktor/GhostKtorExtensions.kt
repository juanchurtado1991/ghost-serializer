package com.ghost.serialization.ktor

import com.ghost.serialization.parser.common.*
import com.ghost.serialization.parser.bytes.*
import com.ghost.serialization.parser.strings.*
import com.ghost.serialization.parser.streaming.*
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.proto.GhostProtoJsonFlatReader
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
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

internal const val CONTENT_TYPE_APPLICATION = "application"
internal const val CONTENT_TYPE_YAML = "yaml"

/**
 * Extension to register Ghost YAML serialization as the content negotiator in Ktor.
 */
fun Configuration.ghostYaml(
    contentType: ContentType = ContentType(CONTENT_TYPE_APPLICATION, CONTENT_TYPE_YAML),
    configurer: ((GhostYamlFlatReader) -> Unit)? = null
) {
    register(contentType, GhostYamlContentConverter(configurer))
}

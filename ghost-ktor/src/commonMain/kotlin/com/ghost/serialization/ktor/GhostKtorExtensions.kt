package com.ghost.serialization.ktor

import com.ghost.serialization.Ghost
import com.ghost.serialization.contract.GhostRegistry
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
    configurer: ((GhostJsonFlatReader) -> Unit)? = null,
    registry: GhostRegistry = Ghost
) {
    register(contentType, GhostContentConverter(configurer = configurer, registry = registry))
}

/**
 * Extension to register Ghost's proto3-JSON mapping ([GhostProtoContentConverter]) as the
 * content negotiator in Ktor — use for APIs backed by `@GhostProtoSerialization` types.
 */
fun Configuration.ghostProto(
    contentType: ContentType = ContentType.Application.Json,
    configurer: ((GhostProtoJsonFlatReader) -> Unit)? = null,
    registry: GhostRegistry = Ghost
) {
    register(contentType, GhostProtoContentConverter(configurer = configurer, registry = registry))
}

/**
 * Extension to register Ghost YAML serialization as the content negotiator in Ktor.
 */
fun Configuration.ghostYaml(
    contentType: ContentType = GhostKtorMediaTypes.APPLICATION_YAML,
    configurer: ((GhostYamlFlatReader) -> Unit)? = null,
    registry: GhostRegistry = Ghost
) {
    register(contentType, GhostYamlContentConverter(configurer = configurer, registry = registry))
}

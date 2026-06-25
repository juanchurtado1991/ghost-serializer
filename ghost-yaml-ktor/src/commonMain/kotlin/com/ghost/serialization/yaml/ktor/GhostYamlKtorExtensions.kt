package com.ghost.serialization.yaml.ktor

import com.ghost.serialization.yaml.parser.GhostYamlFlatReader
import io.ktor.http.ContentType
import io.ktor.serialization.Configuration

internal const val CONTENT_TYPE_APPLICATION = "application"
internal const val CONTENT_TYPE_YAML = "yaml"

/**
 * Extension to register Ghost as the YAML content negotiator in Ktor.
 */
fun Configuration.ghostYaml(
    contentType: ContentType = ContentType(CONTENT_TYPE_APPLICATION, CONTENT_TYPE_YAML),
    configurer: ((GhostYamlFlatReader) -> Unit)? = null
) {
    register(contentType, GhostYamlContentConverter(configurer))
}


package com.ghost.serialization.spring

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Spring Boot configuration for Ghost Serialization.
 *
 * ```yaml
 * ghost:
 *   max-payload-bytes: 33554432
 * ```
 */
@ConfigurationProperties(prefix = "ghost")
class GhostProperties {
    /**
     * Maximum JSON request/response body size in bytes.
     * When null, the platform default from Ghost applies (see [com.ghost.serialization.Ghost.maxPayloadBytes]).
     */
    var maxPayloadBytes: Int? = null
}

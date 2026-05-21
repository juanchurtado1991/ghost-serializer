package com.ghost.serialization.spring

import com.ghost.serialization.Ghost
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass

/**
 * Marker auto-configuration for Ghost Serialization in Spring Boot.
 * Web stack wiring: [GhostWebMvcAutoConfiguration], [GhostWebFluxAutoConfiguration].
 */
@AutoConfiguration
@ConditionalOnClass(Ghost::class)
class GhostAutoConfiguration

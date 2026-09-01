package com.ghost.serialization.annotations

/**
 * Applies resilient parsing to a property on JSON readers only: on a type mismatch or unknown
 * enum value, Ghost assigns `null` (if nullable) and continues instead of throwing.
 *
 * Not honored for YAML ([GhostYamlSerialization]); combining the two on the same class is a
 * KSP error.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class GhostResilient

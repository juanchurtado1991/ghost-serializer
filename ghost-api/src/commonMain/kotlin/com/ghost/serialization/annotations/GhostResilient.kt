package com.ghost.serialization.annotations

/**
 * Applies resilient parsing to a property on **JSON readers only**.
 *
 * If a type mismatch occurs, or an unknown enum value is received, Ghost catches the error,
 * assigns `null` to the field (if nullable), and continues parsing instead of throwing.
 *
 * **JSON-only** — not emitted for YAML ([GhostYamlSerialization]) or proto3 YAML paths.
 * Combining `@GhostResilient` with `@GhostYamlSerialization` on the same class is a KSP error.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class GhostResilient

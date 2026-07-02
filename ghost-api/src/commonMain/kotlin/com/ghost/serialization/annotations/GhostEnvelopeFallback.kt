package com.ghost.serialization.annotations

/**
 * Marks the fallback payload field when an envelope discriminator is unknown.
 *
 * Must be a nullable [com.ghost.serialization.types.RawJson] (or `ByteArray`) property.
 * At most one fallback property is allowed per envelope class.
 *
 * When no fallback is declared, unknown discriminators route to `null`.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class GhostEnvelopeFallback

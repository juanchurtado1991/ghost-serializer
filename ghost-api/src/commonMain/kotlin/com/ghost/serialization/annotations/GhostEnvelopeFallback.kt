package com.ghost.serialization.annotations

/**
 * Marks the fallback payload field when an envelope discriminator is unknown.
 *
 * Must be a nullable `RawJson` (or `ByteArray`) property; at most one per envelope class.
 * Unknown discriminators route to `null` when no fallback is declared.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class GhostEnvelopeFallback

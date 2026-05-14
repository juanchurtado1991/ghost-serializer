package com.ghost.serialization.annotations

/**
 * Indicates that this subclass should be used as the fallback when deserializing
 * a sealed class and the discriminator value in the JSON does not match any known subclass.
 *
 * Only one subclass per sealed class hierarchy can be annotated with this annotation.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class GhostFallback

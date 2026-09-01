package com.ghost.serialization.annotations

/**
 * Marks the fallback subclass used when a sealed class's discriminator doesn't match any
 * known subclass. Only one subclass per hierarchy may carry this annotation.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class GhostFallback

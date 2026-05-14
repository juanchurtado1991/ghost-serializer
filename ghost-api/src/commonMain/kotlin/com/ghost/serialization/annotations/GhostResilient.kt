package com.ghost.serialization.annotations

/**
 * Applies resilient parsing to a property.
 * If a type mismatch occurs, or an unknown enum value is received,
 * Ghost will catch the error, assign `null` to the field (if nullable),
 * and continue parsing instead of throwing an exception.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class GhostResilient

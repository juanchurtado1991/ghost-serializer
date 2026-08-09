package com.ghost.serialization.annotations

/**
 * Enables loose type coercion (automatic conversion of stringified numbers or booleans into
 * their primitive representations) during deserialization for the annotated endpoint, parameter, or controller.
 */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.CLASS
)
@Retention(AnnotationRetention.RUNTIME)
annotation class GhostCoerce

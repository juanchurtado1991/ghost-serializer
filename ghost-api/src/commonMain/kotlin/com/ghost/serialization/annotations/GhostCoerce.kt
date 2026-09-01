package com.ghost.serialization.annotations

/**
 * Enables loose type coercion (stringified numbers/booleans converted to primitives) during
 * deserialization, on the annotated endpoint, parameter, or controller.
 */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.CLASS
)
@Retention(AnnotationRetention.RUNTIME)
annotation class GhostCoerce

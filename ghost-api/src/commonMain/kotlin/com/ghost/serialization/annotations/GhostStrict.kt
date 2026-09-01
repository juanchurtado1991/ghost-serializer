package com.ghost.serialization.annotations

/**
 * Enforces strict JSON syntax checking (missing/duplicate/trailing commas, unmapped fields)
 * during deserialization, on the annotated endpoint, parameter, or controller.
 */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.CLASS
)
@Retention(AnnotationRetention.RUNTIME)
annotation class GhostStrict

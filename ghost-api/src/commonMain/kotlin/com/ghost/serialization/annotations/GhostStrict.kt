package com.ghost.serialization.annotations

/**
 * Enforces strict JSON syntax checking (missing or duplicate commas, trailing commas,
 * and unmapped fields) during deserialization for the annotated endpoint, parameter, or controller.
 */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.CLASS
)
@Retention(AnnotationRetention.RUNTIME)
annotation class GhostStrict

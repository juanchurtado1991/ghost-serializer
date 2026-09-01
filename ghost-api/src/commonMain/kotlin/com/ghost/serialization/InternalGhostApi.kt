package com.ghost.serialization

/**
 * Marks declarations internal to Ghost Serialization: not for public use, may change or be
 * removed without notice. Generated code from the KSP compiler plugin uses this to access
 * internal optimized helpers.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This is an internal Ghost API. Do not use it directly."
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
    AnnotationTarget.CONSTRUCTOR
)
annotation class InternalGhostApi

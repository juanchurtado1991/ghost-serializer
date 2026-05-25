package com.ghost.serialization

/**
 * Marks declarations that are internal to Ghost Serialization.
 *
 * Declarations annotated with this annotation are not intended for public use and might change
 * or be removed in future versions without notice. Generated code from the Ghost KSP compiler plugin
 * uses this annotation to access internal optimized helper functions.
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

package com.ghost.serialization.annotations

/**
 * Indicates that the return value of the annotated function must be used.
 * This is a new feature in Kotlin 2.3.0 to prevent performance leaks and logical errors.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class MustUseReturnValues

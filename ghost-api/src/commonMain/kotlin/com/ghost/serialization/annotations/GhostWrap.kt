package com.ghost.serialization.annotations

/**
 * Re-structures a class property into a nested JSON object during serialization.
 * This is the inverse of [GhostFlatten].
 *
 * @param path The dot-separated path where the property should be wrapped.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.BINARY)
annotation class GhostWrap(val path: String)

package com.ghost.serialization.annotations

/**
 * Maps a nested JSON property directly to a field in the annotated class, avoiding intermediate
 * wrapper classes and their allocations.
 *
 * @param path The dot-separated path to the JSON property.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.BINARY)
annotation class GhostFlatten(val path: String)

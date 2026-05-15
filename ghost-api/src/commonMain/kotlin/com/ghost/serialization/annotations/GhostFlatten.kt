package com.ghost.serialization.annotations

/**
 * Maps a nested JSON property directly to a field in the annotated class.
 *
 * Example:
 * ```kotlin
 * @GhostSerialization
 * data class Device(
 *     @GhostFlatten("attributes.value.level")
 *     val level: Int
 * )
 * ```
 * JSON: `{"attributes": {"value": {"level": 42}}}`
 *
 * This avoids creating intermediate wrapper classes and significantly reduces memory allocation
 * by parsing nested values directly into the target field.
 *
 * @param path The dot-separated path to the JSON property.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.BINARY)
annotation class GhostFlatten(val path: String)

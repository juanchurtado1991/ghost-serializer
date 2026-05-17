package com.ghost.serialization.annotations

/**
 * Indicates that the annotated property should be ignored during serialization and deserialization.
 *
 * Properties marked with this annotation will not be written to the output JSON, and will be skipped
 * when parsing incoming JSON payloads.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class GhostIgnore
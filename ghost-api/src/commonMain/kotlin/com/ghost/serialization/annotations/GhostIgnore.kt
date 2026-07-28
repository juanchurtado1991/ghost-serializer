package com.ghost.serialization.annotations

/**
 * Indicates that the annotated property should be ignored during serialization and deserialization.
 *
 * Ignored properties are omitted on every generated path where the enclosing model is serialized:
 * **JSON**, **proto3 JSON** ([GhostProtoSerialization]), and **YAML** ([GhostYamlSerialization]).
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class GhostIgnore
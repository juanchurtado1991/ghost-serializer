package com.ghost.serialization.annotations

/**
 * Excludes the annotated property from serialization and deserialization on every generated
 * path: JSON, proto3 JSON ([GhostProtoSerialization]), and YAML ([GhostYamlSerialization]).
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class GhostIgnore
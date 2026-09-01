package com.ghost.serialization.annotations

/**
 * Customizes the wire key for the annotated property, overriding the default Kotlin property
 * name on every generated path: **JSON**, **proto3 JSON** ([GhostProtoSerialization]), and
 * **YAML** ([GhostYamlSerialization]).
 *
 * @property name The custom key name on the wire.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class GhostName(val name: String)
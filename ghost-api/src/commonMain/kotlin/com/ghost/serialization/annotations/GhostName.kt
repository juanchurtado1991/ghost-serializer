package com.ghost.serialization.annotations

/**
 * Customizes the wire key used for the annotated property during serialization and deserialization.
 *
 * By default, the serializer uses the property's Kotlin name. When this annotation is applied,
 * the serializer uses [name] instead on every generated path where the property is emitted:
 * **JSON**, **proto3 JSON** ([GhostProtoSerialization]), and **YAML** ([GhostYamlSerialization]).
 *
 * @property name The custom key name on the wire (JSON field name, YAML mapping key, proto JSON name).
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class GhostName(val name: String)
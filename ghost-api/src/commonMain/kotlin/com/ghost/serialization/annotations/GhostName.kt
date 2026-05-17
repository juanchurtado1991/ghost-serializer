package com.ghost.serialization.annotations

/**
 * Customizes the key name used for the annotated property during serialization and deserialization.
 *
 * By default, the serializer uses the property's name in Kotlin. When this annotation is applied,
 * the serializer will use the provided [name] instead.
 *
 * @property name The custom key name to use in the serialized JSON representation.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class GhostName(val name: String)
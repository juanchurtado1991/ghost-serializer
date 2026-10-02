package com.ghost.serialization.contract

import kotlin.reflect.AssociatedObjectKey
import kotlin.reflect.ExperimentalAssociatedObjects
import kotlin.reflect.KClass

/**
 * Links a `@GhostSerialization` class to its generated [serializer] as an associated object, so
 * the runtime resolves it with `findAssociatedObject` instead of `Ghost.addRegistry`. Never
 * written by hand: the Ghost compiler plugin (applied by the `com.ghostserializer.ghost` Gradle
 * plugin) attaches it. Declared separately for Kotlin/Native and Kotlin/Wasm because the
 * associated-objects API is not part of the shared non-JVM stdlib.
 */
@AssociatedObjectKey
@OptIn(ExperimentalAssociatedObjects::class)
@PublishedApi
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
internal annotation class GhostSerializerLink(
    val serializer: KClass<out GhostSerializer<*>>
)

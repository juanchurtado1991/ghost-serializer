package com.ghost.serialization.annotations

/**
 * Marks a property as a "Signature Key" for inferred polymorphism.
 *
 * When a `sealed class` is annotated with `@GhostSerialization(inferred = true)`,
 * Ghost uses the presence of specific keys to decide which concrete subclass to instantiate.
 *
 * By default, all non-nullable fields that are unique to a subclass are treated as signatures.
 * Use this annotation to explicitly mark a field as an identifier, even if it's shared
 * with other classes (as long as the combination of signatures is unique).
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class GhostSignature

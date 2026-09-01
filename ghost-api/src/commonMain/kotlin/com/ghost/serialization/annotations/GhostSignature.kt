package com.ghost.serialization.annotations

/**
 * Marks a property as a signature key for inferred polymorphism: with
 * `@GhostSerialization(inferred = true)` on a sealed class, Ghost uses key presence to pick
 * the concrete subclass.
 *
 * By default, non-nullable fields unique to a subclass are signatures. Use this to mark a
 * shared field explicitly, as long as the combination of signatures stays unique.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class GhostSignature

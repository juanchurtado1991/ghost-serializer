package com.ghost.serialization.annotations

/**
 * Triggers the automatic generation of high-performance serializers for the annotated class.
 *
 * When applied, the Ghost KSP compiler plugin generates a specialized Serializer
 * implementation that avoids reflection and is optimized for peak throughput in
 * Kotlin Multiplatform environments.
 *
 * @param name Optional custom name for the model to avoid registry collisions.
 * @param discriminator The JSON field name used to identify the concrete type of a `sealed class`.
 *   Defaults to `"type"`. Override this when consuming third-party APIs that use a different
 *   convention (e.g. `"kind"`, `"object"`, `"@type"`).
 *   Has no effect on non-sealed classes.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class GhostSerialization(
    val name: String = "",
    val discriminator: String = "type"
)
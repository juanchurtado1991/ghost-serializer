package com.ghost.serialization.annotations

/**
 * Triggers the automatic generation of high-performance serializers for the annotated class.
 *
 * When applied, the Ghost KSP compiler plugin generates a specialized Serializer
 * implementation that avoids reflection and is optimized for peak throughput in
 * Kotlin Multiplatform environments.
 *
 * @param name Optional custom name for the model to avoid registry collisions.
 * @param discriminator The JSON field name used to identify the concrete type of `sealed class`.
 *   Defaults to `"type"`. Override this when consuming third-party APIs that use a different
 *   convention (e.g. `"kind"`, `"object"`, `"@type"`).
 *   Has no effect on non-sealed classes.
 * @param inferred Whether the type should be inferred automatically.
 * @param textChannel When `true`, generates native [com.ghost.serialization.parser.GhostJsonStringReader]
 *   deserialize/serialize overloads for this model and any nested `@GhostSerialization` types
 *   reachable from its property graph. Defaults to `false`. Module-wide `ghost.textChannel=true`
 *   still enables the string channel for every model in the module (legacy).
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class GhostSerialization(
    val name: String = "",
    val discriminator: String = "type",
    val inferred: Boolean = false,
    val textChannel: Boolean = false,
)
package com.ghost.serialization.annotations

/**
 * Generates a reflection-free serializer for the annotated class via the Ghost KSP plugin.
 *
 * @param name Optional custom name to avoid registry collisions.
 * @param discriminator JSON field identifying the concrete type of a `sealed class`. Defaults to
 *   `"type"`; override for APIs using a different convention (e.g. `"kind"`, `"@type"`). No effect
 *   on non-sealed classes.
 * @param inferred Whether the type should be inferred automatically.
 * @param textChannel When `true` (default), generates `GhostJsonStringReader` overloads so decoding
 *   a `String` skips UTF-8 re-encoding. Set `false` for smaller generated code on byte/stream-only
 *   types; module-wide `ghost.textChannel=false` forces this for every model.
 *
 * YAML support requires [GhostYamlSerialization] on the same class as well.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class GhostSerialization(
    val name: String = "",
    val discriminator: String = "type",
    val inferred: Boolean = false,
    val textChannel: Boolean = true,
)

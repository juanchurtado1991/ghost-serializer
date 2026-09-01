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
 *   Defaults to `"type"`; override for third-party APIs using a different convention (e.g.
 *   `"kind"`, `"@type"`). Has no effect on non-sealed classes.
 * @param inferred Whether the type should be inferred automatically.
 * @param textChannel When `true` (the default), generates native `GhostJsonStringReader`
 *   overloads so decoding a `String` avoids re-encoding to UTF-8 and delegating to the byte
 *   reader. Set `false` to trade that for smaller generated code on types only ever decoded
 *   from bytes/streams; module-wide `ghost.textChannel=false` forces this for every model.
 *
 * YAML serialization is **not** enabled by this annotation alone — add [GhostYamlSerialization]
 * on the same class when you need `Ghost.decodeFromYaml` / framework YAML adapters.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class GhostSerialization(
    val name: String = "",
    val discriminator: String = "type",
    val inferred: Boolean = false,
    val textChannel: Boolean = true,
)

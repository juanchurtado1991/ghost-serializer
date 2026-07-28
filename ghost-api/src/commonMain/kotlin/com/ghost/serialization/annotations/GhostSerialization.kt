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
 * @param textChannel When `true` (the default), generates native
 *   [com.ghost.serialization.parser.strings.GhostJsonStringReader] deserialize/serialize overloads for
 *   this model and any nested `@GhostSerialization` types reachable from its property graph.
 *   Without a native string-channel overload, decoding from a `String` (e.g.
 *   `Ghost.deserialize<T>(json: String)`) falls back to re-encoding the whole document to UTF-8
 *   and delegating to the byte-mode reader — correct, but not zero-allocation. Set to `false`
 *   only to trade that String-mode performance for smaller generated code (roughly a third less
 *   per model) on types that are only ever decoded from bytes/streams. Module-wide
 *   `ghost.textChannel=false` forces the bridging behavior for every model in the module
 *   regardless of this per-class value, for projects that want to opt out everywhere at once.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class GhostSerialization(
    val name: String = "",
    val discriminator: String = "type",
    val inferred: Boolean = false,
    val textChannel: Boolean = true,
)

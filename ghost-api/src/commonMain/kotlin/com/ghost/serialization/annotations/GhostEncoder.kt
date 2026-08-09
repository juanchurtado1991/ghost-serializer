package com.ghost.serialization.annotations

import kotlin.reflect.KClass

/**
 * Delegates serialization of the annotated property to a static function in [provider].
 *
 * Use this for custom wire formats (dates, legacy booleans, and similar) without writing a
 * full serializer. JSON-only — incompatible with [GhostYamlSerialization].
 *
 * @param provider Class or object that declares the encoding function.
 * @param functionName Name of the static encoding function with signature
 *   `fun(writer, value: T)`. The writer parameter must be one of:
 *   - `GhostJsonFlatWriter` (`writer.bytes`) — flat byte output
 *   - `GhostJsonWriter` (`writer.bytes`) — streaming byte output
 *
 * Provide overloads for each byte writer you use so both serialize paths call directly.
 * The string-channel writer bridges through a temporary `GhostJsonFlatWriter`.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class GhostEncoder(
    val provider: KClass<*>,
    val functionName: String
)

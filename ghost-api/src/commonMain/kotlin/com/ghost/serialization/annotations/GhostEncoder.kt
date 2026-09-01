package com.ghost.serialization.annotations

import kotlin.reflect.KClass

/**
 * Delegates serialization of the annotated property to a static function in [provider].
 *
 * Use for custom wire formats without writing a full serializer. JSON-only — incompatible with
 * [GhostYamlSerialization].
 *
 * @param provider Class or object that declares the encoding function.
 * @param functionName Name of the static encoding function, `fun(writer, value: T)`. Provide
 *   overloads for `GhostJsonFlatWriter` and `GhostJsonWriter` so both serialize paths call
 *   directly; the string channel bridges through a temporary `GhostJsonFlatWriter`.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class GhostEncoder(
    val provider: KClass<*>,
    val functionName: String
)

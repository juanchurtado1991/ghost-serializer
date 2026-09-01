package com.ghost.serialization.annotations

import kotlin.reflect.KClass

/**
 * Delegates deserialization of the annotated property to a static function in [provider].
 *
 * Use for custom wire formats without writing a full serializer. JSON-only — incompatible with
 * [GhostYamlSerialization].
 *
 * @param provider Class or object that declares the decoding function.
 * @param functionName Name of the static decoding function. First parameter must be a
 *   `GhostJsonReader`, `GhostJsonFlatReader`, or `GhostJsonStringReader`; provide an overload for
 *   each channel you use, or KSP bridges through a temporary reader.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class GhostDecoder(
    val provider: KClass<*>,
    val functionName: String
)

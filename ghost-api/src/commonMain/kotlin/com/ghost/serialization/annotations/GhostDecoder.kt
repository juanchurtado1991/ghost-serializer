package com.ghost.serialization.annotations

import kotlin.reflect.KClass

/**
 * Delegates deserialization of the annotated property to a static function in [provider].
 *
 * Use this for custom wire formats (dates, legacy booleans, and similar) without writing a
 * full serializer. JSON-only — incompatible with [GhostYamlSerialization].
 *
 * @param provider Class or object that declares the decoding function.
 * @param functionName Name of the static decoding function. The first parameter must be one of:
 *   - `GhostJsonReader` (`parser.streaming`) — streaming byte input
 *   - `GhostJsonFlatReader` (`parser.bytes`) — flat byte buffer input
 *   - `GhostJsonStringReader` (`parser.strings`) — native string input
 *
 * Provide overloads for each channel you use. A matching overload is called directly; otherwise
 * KSP bridges through a temporary reader (string → UTF-8 when only a byte-reader overload exists).
 * Prefer a `GhostJsonStringReader` overload when [GhostSerialization.textChannel] is `true`
 * (the default).
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class GhostDecoder(
    val provider: KClass<*>,
    val functionName: String
)

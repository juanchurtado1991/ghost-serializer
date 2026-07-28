package com.ghost.serialization.annotations

import kotlin.reflect.KClass

/**
 * Delegates deserialization of the annotated property to a static function in [provider].
 *
 * Use this for custom wire formats (dates, legacy booleans, and similar) without writing a
 * full serializer.
 *
 * @param provider Class or object that declares the decoding function.
 * @param functionName Name of the static decoding function. The first parameter must be one of:
 *   - [com.ghost.serialization.parser.streaming.GhostJsonReader] — streaming byte input (default)
 *   - [com.ghost.serialization.parser.bytes.GhostJsonFlatReader] — flat byte buffer input
 *   - [com.ghost.serialization.parser.strings.GhostJsonStringReader] — native string input when
 *     [GhostSerialization.textChannel] is `true` (or module `ghost.textChannel=true`)
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class GhostDecoder(
    val provider: KClass<*>,
    val functionName: String
)
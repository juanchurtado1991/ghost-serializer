package com.ghost.serialization.annotations

import kotlin.reflect.KClass

/**
 * Delegated field decoding.
 * Useful for specific formats (custom dates, legacy booleans) without
 * writing a full serializer.
 *
 * @param provider The class/object containing the decoding function.
 * @param functionName The name of the static function. Supported first-parameter types:
 *   - `GhostJsonReader` — bytes / streaming channel (default)
 *   - `GhostJsonFlatReader` — flat byte buffer channel
 *   - `GhostJsonStringReader` — native string channel when `textChannel=true` on `@GhostSerialization` (or module `ghost.textChannel=true`)
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class GhostDecoder(
    val provider: KClass<*>,
    val functionName: String
)
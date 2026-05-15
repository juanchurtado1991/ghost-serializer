package com.ghost.serialization.annotations

import kotlin.reflect.KClass

/**
 * Delegated field decoding.
 * Useful for specific formats (custom dates, legacy booleans) without 
 * writing a full serializer.
 *
 * @param provider The class/object containing the decoding function.
 * @param functionName The name of the static function: `fun(GhostJsonReader): T`
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class GhostDecoder(
    val provider: KClass<*>,
    val functionName: String
)

/**
 * Delegated field encoding.
 *
 * @param provider The class/object containing the encoding function.
 * @param functionName The name of the static function: `fun(GhostJsonFlatWriter, T)`
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class GhostEncoder(
    val provider: KClass<*>,
    val functionName: String
)

package com.ghost.serialization.annotations

import kotlin.reflect.KClass

/**
 * Delegates serialization of the annotated property to a static function in [provider].
 *
 * @param provider Class or object that declares the encoding function.
 * @param functionName Name of the static encoding function with signature
 *   `fun([com.ghost.serialization.writer.bytes.GhostJsonFlatWriter], T)`.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class GhostEncoder(
    val provider: KClass<*>,
    val functionName: String
)
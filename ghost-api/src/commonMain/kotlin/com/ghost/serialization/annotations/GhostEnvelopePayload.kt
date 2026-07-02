package com.ghost.serialization.annotations

import kotlin.reflect.KClass

/**
 * Maps a wire discriminator value to a nullable payload field on a [GhostJsonEnvelope] class.
 *
 * The generated router selects this property when the envelope discriminator matches [value].
 * When [target] is set to a [GhostSerialization] model, `routeTyped` / `parseTyped` decode via
 * zero-copy [com.ghost.serialization.types.RawJson] slice parsing.
 *
 * @param value Wire discriminator string (e.g. `"DEVICE_EVENT"`, `"invoice.paid"`).
 * @param target Typed model to decode into; default means return [RawJson] only.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class GhostEnvelopePayload(
    val value: String,
    val target: KClass<*> = Unit::class
)

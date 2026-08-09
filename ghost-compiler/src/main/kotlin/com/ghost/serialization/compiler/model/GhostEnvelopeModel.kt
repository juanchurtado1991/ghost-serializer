package com.ghost.serialization.compiler.model

/**
 * Resolved metadata for a `@GhostJsonEnvelope` class.
 */
internal data class GhostEnvelopeModel(
    val discriminatorKotlinName: String,
    val discriminatorJsonName: String,
    val timeKotlinName: String?,
    val isGenericMode: Boolean,
    val genericDataKotlinName: String?,
    val payloadMappings: List<EnvelopePayloadMapping>,
    val fallbackMapping: EnvelopePayloadMapping?
)

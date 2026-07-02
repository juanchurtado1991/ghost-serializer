package com.ghost.serialization.compiler

import com.squareup.kotlinpoet.TypeName

/**
 * Resolved metadata for a [@GhostJsonEnvelope][com.ghost.serialization.annotations.GhostJsonEnvelope] class.
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

/**
 * One routable payload slot on an envelope (field + optional typed decode target).
 */
internal data class EnvelopePayloadMapping(
    val discriminatorValue: String,
    val kotlinName: String,
    val isRawJson: Boolean,
    val targetType: TypeName?
)

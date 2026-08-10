package com.ghost.serialization.compiler.model

import com.squareup.kotlinpoet.TypeName

/**
 * One routable payload slot on an envelope (field + optional typed decode target).
 */
internal data class EnvelopePayloadMapping(
    val discriminatorValue: String,
    val kotlinName: String,
    val isRawJson: Boolean,
    val targetType: TypeName?
)

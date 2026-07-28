package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class ContextualModel(
    val id: String,
    /** Hex RGB string serialized by [ExternalColorSerializer]. */
    val color: ExternalColor
)
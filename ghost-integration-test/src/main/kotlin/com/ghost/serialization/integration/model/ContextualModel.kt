package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

/** [color] is a hex RGB string serialized by [ExternalColorSerializer]. */
@GhostSerialization
data class ContextualModel(
    val id: String,
    val color: ExternalColor
)
package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class ContextualModel(
    val id: String,
    /** see [ExternalColorSerializer]*/
    val color: ExternalColor
)
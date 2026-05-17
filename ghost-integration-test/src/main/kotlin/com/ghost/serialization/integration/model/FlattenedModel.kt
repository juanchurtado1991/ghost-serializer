package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostFlatten
import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class FlattenedModel(
    val id: Int,
    @GhostFlatten("attributes.value.level")
    val level: Int,
    @GhostFlatten("attributes.status")
    val status: String,
    @GhostFlatten("metadata.author")
    val author: String? = null
)

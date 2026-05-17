package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class NestedContainer(
    val label: String,
    val children: List<NestedContainer>? = null
)
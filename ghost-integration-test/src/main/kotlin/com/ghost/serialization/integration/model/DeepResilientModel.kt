package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class DeepResilientModel(
    val id: String,
    val list: List<ResilientItem>
)
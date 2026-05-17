package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class CollisionChild(
    val name: String,
    val value: Int
)

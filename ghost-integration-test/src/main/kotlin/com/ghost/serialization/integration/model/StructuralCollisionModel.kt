package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostFlatten
import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class StructuralCollisionModel(
    val name: String, // Collides with child.name
    @GhostFlatten("meta")
    val child: CollisionChild
)
package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class RecursiveGraphNode(
    val name: String,
    val next: RecursiveGraphNode? = null
)
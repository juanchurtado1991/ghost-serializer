package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class RecursiveNode(
    val id: Int,
    val name: String,
    val children: List<RecursiveNode>? = null
)

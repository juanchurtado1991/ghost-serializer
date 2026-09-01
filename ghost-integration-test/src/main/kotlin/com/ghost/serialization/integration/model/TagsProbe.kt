package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class TagsProbe(
    val tags: List<String>,
    val count: Int,
)

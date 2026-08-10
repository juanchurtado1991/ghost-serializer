package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.types.RawJson

@GhostSerialization
data class TagsProbe(
    val tags: List<String>,
    val count: Int,
)

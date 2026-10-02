package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class PathHintEnumHolder(
    val status: PathHintStrictEnum,
)

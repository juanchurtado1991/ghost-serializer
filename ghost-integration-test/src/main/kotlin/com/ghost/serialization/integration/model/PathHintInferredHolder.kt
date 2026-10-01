package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class PathHintInferredHolder(
    val payload: PathHintInferredPayload,
)

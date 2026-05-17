package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostFlatten
import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class DeepFlattenedModel(
    @GhostFlatten("a.b.c.d.e.f.g")
    val value: String
)

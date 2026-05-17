package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class ModelWithExternal(
    val id: Int,
    val date: ExternalDate
)
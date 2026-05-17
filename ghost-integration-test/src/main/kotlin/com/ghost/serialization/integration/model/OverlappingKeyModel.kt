package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class OverlappingKeyModel(
    val id: Int,
    val id_internal: Int,
    val identity: String
)
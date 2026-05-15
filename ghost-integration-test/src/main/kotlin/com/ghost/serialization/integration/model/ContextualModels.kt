package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

// Simulate a third-party class that is NOT annotated
data class ThirdPartyData(val value: String)

@GhostSerialization
data class ContextualModel(
    val id: String,
    val external: ThirdPartyData
)

@GhostSerialization
data class ListContextualModel(
    val items: List<ThirdPartyData>
)

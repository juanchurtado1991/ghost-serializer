package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class GhostAdvancedProfile(
    val token: GhostUserToken,
    val shapes: List<GhostShape>
)
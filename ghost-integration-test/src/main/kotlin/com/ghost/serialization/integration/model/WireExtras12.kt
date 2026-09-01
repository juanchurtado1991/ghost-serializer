package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class WireExtras12(
    @GhostName("extra1") val extra1: String,
    @GhostName("extra2") val extra2: String,
)

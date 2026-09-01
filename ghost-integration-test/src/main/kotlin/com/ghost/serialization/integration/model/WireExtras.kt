package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class WireExtras(
    @GhostName("extra1") val extra1: String?,
    @GhostName("extra2") val extra2: String?,
    @GhostName("extra3") val extra3: String?,
    @GhostName("extra4") val extra4: String?,
)

package com.ghost.playground.features

import com.ghost.serialization.annotations.GhostResilient
import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class ResilientConfig(
    @GhostResilient
    val theme: String? = null,
    @GhostResilient
    val retryCount: Int = 3,
)

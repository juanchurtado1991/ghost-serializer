package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostResilient
import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class SmartHome(
    val id: String,
    @GhostResilient
    val active: Boolean? = null,
    @GhostResilient
    val deviceCount: Int = 0,
    val devices: List<SmartDevice>,
    @GhostResilient
    val status: HomeStatus? = null,
    @GhostResilient
    val config: HomeConfig? = null
)

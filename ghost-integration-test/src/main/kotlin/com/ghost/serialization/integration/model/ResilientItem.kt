package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostResilient
import com.ghost.serialization.annotations.GhostSerialization

@GhostResilient
@GhostSerialization
data class ResilientItem(
    val id: String,
    @GhostResilient
    val value: Int? = null
)
package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostResilient
import com.ghost.serialization.annotations.GhostSerialization

/** Soft field recovers; hard field must still report a clean path. */
@GhostSerialization
data class PathHintResilientHolder(
    @GhostResilient
    val soft: Int? = null,
    val hard: Int,
)

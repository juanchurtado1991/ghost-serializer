package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization(discriminator = "kind")
sealed class GhostKindEvent {
    @GhostSerialization
    data class Created(val id: String, val name: String) : GhostKindEvent()

    @GhostSerialization
    data class Deleted(val id: String) : GhostKindEvent()

    @GhostSerialization
    data class Updated(val id: String, val changes: String) : GhostKindEvent()
}
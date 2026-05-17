package com.ghost.serialization.sample.model

import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@GhostSerialization
@Serializable
enum class CharacterStatus {
    @SerialName("Alive")
    @GhostName("Alive")
    Alive,
    @SerialName("Dead")
    @GhostName("Dead")
    Dead,
    @SerialName("unknown")
    @GhostName("unknown")
    unknown
}

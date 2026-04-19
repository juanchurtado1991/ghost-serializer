package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.SerialName

@GhostSerialization
enum class GhostSovereigntyEnum {
    @SerialName("industrial_match") Match,
    @GhostName("ghost_match") GhostMatch,
    Standard
}

@GhostSerialization
data class GhostEnumWrapper(
    val status: GhostSovereigntyEnum,
    val optionalStatus: GhostSovereigntyEnum? = null
)

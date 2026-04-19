package com.ghost.integration.model

import com.ghostserializer.annotations.GhostName
import com.ghostserializer.annotations.GhostSerialization
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

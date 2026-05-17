package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostResilient
import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class ResilientEnumModel(
    @GhostResilient
    val status: GhostStandardsEnum = GhostStandardsEnum.Standard,
    @GhostResilient
    val nullableStatus: GhostStandardsEnum? = null
)

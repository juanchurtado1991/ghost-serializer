package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class GhostEnumWrapper(
    val status: GhostStandardsEnum,
    val optionalStatus: GhostStandardsEnum? = null
)
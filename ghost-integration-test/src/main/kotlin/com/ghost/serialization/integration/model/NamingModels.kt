package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.annotations.GhostResilient

@GhostSerialization
data class NamingModel(
    @GhostName("user_id")
    val id: Int,
    @GhostName("full_name")
    val name: String,
    @GhostName("is_active")
    val active: Boolean
)

@GhostSerialization
data class ResilientEnumModel(
    @GhostResilient
    val status: GhostStandardsEnum = GhostStandardsEnum.Standard,
    @GhostResilient
    val nullableStatus: GhostStandardsEnum? = null
)

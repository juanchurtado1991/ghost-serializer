package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class NamingModel(
    @GhostName("user_id")
    val id: Int,
    @GhostName("full_name")
    val name: String,
    @GhostName("is_active")
    val active: Boolean
)
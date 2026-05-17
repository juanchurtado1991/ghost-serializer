package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.Serializable

@GhostSerialization
@Serializable
data class UserWithValueClass(
    val id: UserId,
    val name: String
)

package com.ghost.integration.model

import com.ghost.serialization.annotations.GhostSerialization
import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

@Serializable
@JsonClass(generateAdapter = true)
@GhostSerialization
data class UserWithDefaults(
    val id: Int,
    val name: String = "Default User",
    val role: UserRole = UserRole.VIEWER
)

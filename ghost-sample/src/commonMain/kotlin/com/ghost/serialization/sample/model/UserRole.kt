package com.ghost.serialization.sample.model

import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.Serializable

@GhostSerialization
@Serializable
enum class UserRole {
    ADMIN, USER, GUEST, EDITOR
}

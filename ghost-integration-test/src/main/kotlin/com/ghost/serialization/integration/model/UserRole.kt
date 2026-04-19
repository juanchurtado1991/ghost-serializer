package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.Serializable

@GhostSerialization
@Serializable
enum class UserRole { 
    EDITOR, 
    VIEWER 
}

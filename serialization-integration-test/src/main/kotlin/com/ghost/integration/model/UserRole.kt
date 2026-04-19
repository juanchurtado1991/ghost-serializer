package com.ghost.integration.model

import com.ghostserializer.annotations.GhostSerialization
import kotlinx.serialization.Serializable

@GhostSerialization
@Serializable
enum class UserRole { 
    EDITOR, 
    VIEWER 
}

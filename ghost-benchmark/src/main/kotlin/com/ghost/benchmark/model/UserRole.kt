package com.ghost.benchmark.model

import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.Serializable

@GhostSerialization
@Serializable
enum class UserRole { 
    ADMIN, 
    EDITOR, 
    VIEWER 
}

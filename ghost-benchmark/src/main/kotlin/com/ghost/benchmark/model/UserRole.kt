package com.ghost.benchmark.model

import kotlinx.serialization.Serializable

@Serializable
enum class UserRole { 
    ADMIN, 
    EDITOR, 
    VIEWER 
}

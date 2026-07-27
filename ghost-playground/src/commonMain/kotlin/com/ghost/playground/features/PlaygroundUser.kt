package com.ghost.playground.features

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class PlaygroundUser(
    val id: Long,
    val name: String,
    val email: String? = null,
)

package com.ghost.playground.features

import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.annotations.GhostYamlSerialization

@GhostSerialization
@GhostYamlSerialization
data class PlaygroundUser(
    val id: Long,
    val name: String,
    val email: String? = null,
)

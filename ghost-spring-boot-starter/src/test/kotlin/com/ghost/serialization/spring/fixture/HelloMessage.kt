package com.ghost.serialization.spring.fixture

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class HelloMessage(
    val id: Int,
    val name: String,
)

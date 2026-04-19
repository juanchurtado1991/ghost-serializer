package com.ghost.serialization.sample

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class User(
    val name: String,
    val age: Int
)
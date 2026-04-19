package com.ghostserializer.sample

import com.ghostserializer.annotations.GhostSerialization

@GhostSerialization
data class User(
    val name: String,
    val age: Int
)
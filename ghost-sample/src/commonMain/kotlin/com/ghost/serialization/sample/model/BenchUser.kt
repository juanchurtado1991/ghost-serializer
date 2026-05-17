package com.ghost.serialization.sample.model

import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.Serializable

@GhostSerialization
@Serializable
data class BenchUser(
    val id: Int,
    val name: String,
    val email: String,
    val isActive: Boolean,
    val score: Double
)

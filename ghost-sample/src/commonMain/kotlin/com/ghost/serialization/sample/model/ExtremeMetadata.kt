package com.ghost.serialization.sample.model

import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.Serializable

@Suppress("ArrayInDataClass")
@GhostSerialization
@Serializable
data class ExtremeMetadata(
    val timestamp: Long,
    val role: UserRole,
    val tags: List<String>,
    val precision: Double,
    val history: IntArray
)

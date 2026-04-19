package com.ghost.serialization.sample.domain

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class BenchUser(
    val id: Int,
    val name: String,
    val email: String,
    val isActive: Boolean,
    val score: Double
)

@GhostSerialization
enum class UserRole {
    ADMIN, USER, GUEST, EDITOR
}

@Suppress("ArrayInDataClass")
@GhostSerialization
data class ExtremeMetadata(
    val timestamp: Long,
    val role: UserRole,
    val tags: List<String>,
    val precision: Double,
    val history: IntArray
)

@GhostSerialization
data class ComplexResponse(
    val status: String,
    val users: List<BenchUser>,
    val metadata: ExtremeMetadata,
    val shards: Map<String, String>
)

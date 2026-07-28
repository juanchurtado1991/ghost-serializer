package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostProtoSerialization

/** Flat proto3-JSON benchmark fixture — representative `@GhostProtoSerialization` message. */
@GhostProtoSerialization
data class ProtoBenchUser(
    val user_id: Long,
    val name: String,
    val email: String,
    val score: Double,
    val is_active: Boolean = true,
    val role: String = "VIEWER",
    val bio: String? = null,
)

package com.ghost.integration.model

import com.ghostserializer.annotations.GhostSerialization
import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

@Serializable
@JsonClass(generateAdapter = true)
@GhostSerialization
data class BenchUser(
    val id: Int,
    val name: String,
    val email: String,
    val isActive: Boolean,
    val score: Double
)

@file:Suppress("ArrayInDataClass")

package com.ghost.integration.model

import com.ghostserializer.annotations.GhostSerialization
import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

@Serializable
@JsonClass(generateAdapter = true)
@GhostSerialization
data class ExtremeMetadata(
    val lastLogin: Long,
    val role: UserRole,
    val tags: List<String>,
    val precisionScore: Double,
    val accessHistory: IntArray
)

package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization
import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

/**
 * Multi-branch constructor fixture with 2 required + 2 default-valued properties (N=2, 4 branches).
 */
@Serializable
@JsonClass(generateAdapter = true)
@GhostSerialization
data class ApiProductConfig(
    val id: Int,
    val name: String,
    val maxRetries: Int = 3,
    val isEnabled: Boolean = true
)

package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization
import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

/**
 * Realistic model with 2 required + 2 default-valued properties.
 * Exercises the N=2 multi-branch path (4 constructor branches, 0 .copy() calls).
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

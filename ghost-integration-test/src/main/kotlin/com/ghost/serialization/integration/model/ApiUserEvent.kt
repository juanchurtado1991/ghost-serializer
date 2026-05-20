package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization
import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

/**
 * Realistic model with 2 required + 3 default-valued properties.
 * Exercises the N=3 multi-branch path (8 constructor branches, 0 .copy() calls).
 * This is the maximum threshold for the multi-branch optimization.
 */
@Serializable
@JsonClass(generateAdapter = true)
@GhostSerialization
data class ApiUserEvent(
    val userId: Int,
    val eventType: String,
    val version: Int = 1,
    val retryCount: Int = 0,
    val isProcessed: Boolean = false
)

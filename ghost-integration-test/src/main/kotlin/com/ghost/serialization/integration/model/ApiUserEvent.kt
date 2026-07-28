package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization
import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

/**
 * Multi-branch constructor fixture with 2 required + 3 default-valued properties (N=3, 8 branches).
 * Stays below the compiler limit of N=4 (`MAX_DEFAULT_BRANCH_COUNT`).
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

package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization
import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

/**
 * Realistic model with 2 required + 3 default-valued properties.
 * Exercises the N=3 multi-branch path (8 constructor branches, 0 `.copy()` calls).
 * The compiler allows up to N=4 (`MAX_DEFAULT_BRANCH_COUNT`); this fixture stays at N=3.
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

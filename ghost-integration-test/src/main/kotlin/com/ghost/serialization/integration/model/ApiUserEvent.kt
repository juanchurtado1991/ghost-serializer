package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization
import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

/** N=3 default-branch fixture (2 required + 3 defaulted) — below the N=4 `MAX_DEFAULT_BRANCH_COUNT` compiler limit. */
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

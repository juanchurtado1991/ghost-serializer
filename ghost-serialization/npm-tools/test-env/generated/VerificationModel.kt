package com.ghost.serialization.generated

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class VerificationModel(
    val id: Int = 0,
    val `when`: String = "",
    val tags: List<String> = emptyList(),
    val scores: List<Int> = emptyList(),
    val metadata: String? = null
)

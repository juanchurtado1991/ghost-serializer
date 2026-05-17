package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class UniCodeModel(
    val text: String,
    val emoji: String,
    val escaped: String
)
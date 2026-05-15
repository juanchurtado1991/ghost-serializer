package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostIgnore
import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class IgnoreModel(
    val id: Int,
    @GhostIgnore
    val secret: String = "default",
    val name: String
)

@GhostSerialization
data class UnicodeModel(
    val text: String,
    val emoji: String,
    val escaped: String
)

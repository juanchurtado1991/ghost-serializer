package com.ghost.serialization.sample.model

import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.Serializable

@GhostSerialization
@Serializable
data class LocationRef(
    val name: String,
    val url: String
)

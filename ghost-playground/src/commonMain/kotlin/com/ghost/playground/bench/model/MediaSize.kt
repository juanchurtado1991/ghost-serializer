package com.ghost.playground.bench.model

import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.Serializable

@Serializable
@GhostSerialization
data class MediaSize(
    val w: Int,
    val h: Int,
    val resize: String,
)

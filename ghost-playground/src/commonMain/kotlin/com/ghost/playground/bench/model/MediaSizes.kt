package com.ghost.playground.bench.model

import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.Serializable

@Serializable
@GhostSerialization
data class MediaSizes(
    val medium: MediaSize,
    val small: MediaSize,
    val thumb: MediaSize,
    val large: MediaSize,
)

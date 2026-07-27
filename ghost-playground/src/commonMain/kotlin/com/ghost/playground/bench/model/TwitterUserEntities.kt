package com.ghost.playground.bench.model

import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.Serializable

@Serializable
@GhostSerialization
data class TwitterUserEntities(
    val url: UrlContainer? = null,
    val description: UrlContainer,
)

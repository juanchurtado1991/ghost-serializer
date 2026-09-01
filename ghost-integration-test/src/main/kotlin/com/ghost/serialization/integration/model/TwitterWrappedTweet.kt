package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.annotations.GhostWrap
import kotlinx.serialization.Serializable

@Serializable
@GhostSerialization(textChannel = true)
data class TwitterWrappedTweet(
    val id: Long,
    @GhostWrap("details")
    val text: String
)

package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.Serializable

@Serializable
@GhostSerialization(textChannel = true)
data class TwitterSpecialResponse(
    val statuses: List<TwitterSpecialTweet>
)

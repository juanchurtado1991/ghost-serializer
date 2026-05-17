package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class EmojiKeyModel(
    val familyName: String,
    val rocketCount: Int,
    val emojiMap: Map<String, String> = emptyMap()
)
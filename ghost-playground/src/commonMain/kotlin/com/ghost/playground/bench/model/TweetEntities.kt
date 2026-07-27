package com.ghost.playground.bench.model

import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@GhostSerialization
data class TweetEntities(
    val hashtags: List<HashtagItem> = emptyList(),
    val symbols: List<SymbolItem> = emptyList(),
    val urls: List<UrlItem> = emptyList(),
    @SerialName("user_mentions") @GhostName("user_mentions") val userMentions: List<UserMention> = emptyList(),
    val media: List<MediaItem> = emptyList(),
)

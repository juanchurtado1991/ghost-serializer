package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostFlatten
import com.ghost.serialization.annotations.GhostIgnore
import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.annotations.GhostWrap
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@JsonClass(generateAdapter = true)
@Serializable
@GhostSerialization
data class TweetEntities(
    val hashtags: List<HashtagItem> = emptyList(),
    val symbols: List<SymbolItem> = emptyList(),
    val urls: List<UrlItem> = emptyList(),
    @Json(name = "user_mentions")
    @SerialName("user_mentions") @GhostName("user_mentions") val userMentions: List<UserMention> = emptyList(),
    val media: List<MediaItem> = emptyList()
)

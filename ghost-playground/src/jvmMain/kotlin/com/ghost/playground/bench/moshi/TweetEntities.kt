package com.ghost.playground.bench.moshi

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** JVM-only Moshi codegen models for the Speed Test lab, mirroring `com.ghost.playground.bench.model`. */
@JsonClass(generateAdapter = true)
data class TweetEntities(
    val hashtags: List<HashtagItem> = emptyList(),
    val symbols: List<SymbolItem> = emptyList(),
    val urls: List<UrlItem> = emptyList(),
    @Json(name = "user_mentions") val userMentions: List<UserMention> = emptyList(),
    val media: List<MediaItem> = emptyList(),
)

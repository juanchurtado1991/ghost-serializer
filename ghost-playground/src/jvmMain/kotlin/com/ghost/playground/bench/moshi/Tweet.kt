package com.ghost.playground.bench.moshi

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** JVM-only Moshi codegen models for the Speed Test lab, mirroring `com.ghost.playground.bench.model`. */
@JsonClass(generateAdapter = true)
data class Tweet(
    val metadata: TweetMetadata,
    @Json(name = "created_at") val createdAt: String,
    val id: Long,
    @Json(name = "id_str") val idStr: String,
    val text: String,
    val source: String,
    val truncated: Boolean,
    @Json(name = "in_reply_to_status_id") val inReplyToStatusId: Long? = null,
    @Json(name = "in_reply_to_status_id_str") val inReplyToStatusIdStr: String? = null,
    @Json(name = "in_reply_to_user_id") val inReplyToUserId: Long? = null,
    @Json(name = "in_reply_to_user_id_str") val inReplyToUserIdStr: String? = null,
    @Json(name = "in_reply_to_screen_name") val inReplyToScreenName: String? = null,
    val user: TwitterUser,
    val geo: String? = null,
    val coordinates: String? = null,
    val place: String? = null,
    val contributors: String? = null,
    @Json(name = "retweet_count") val retweetCount: Int,
    @Json(name = "favorite_count") val favoriteCount: Int,
    val entities: TweetEntities,
    val favorited: Boolean,
    val retweeted: Boolean,
    @Json(name = "possibly_sensitive") val possiblySensitive: Boolean = false,
    val lang: String? = null,
    @Json(name = "retweeted_status") val retweetedStatus: Tweet? = null,
)

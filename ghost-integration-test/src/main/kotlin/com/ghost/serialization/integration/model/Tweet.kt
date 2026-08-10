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
data class Tweet(
    val metadata: TweetMetadata,
    @Json(name = "created_at")
    @SerialName("created_at") @GhostName("created_at")
    val createdAt: String,
    val id: Long,
    @Json(name = "id_str")
    @SerialName("id_str") @GhostName("id_str") val idStr: String,
    val text: String,
    val source: String,
    val truncated: Boolean,
    @Json(name = "in_reply_to_status_id")
    @SerialName("in_reply_to_status_id") @GhostName("in_reply_to_status_id") val inReplyToStatusId: Long? = null,
    @Json(name = "in_reply_to_status_id_str")
    @SerialName("in_reply_to_status_id_str") @GhostName("in_reply_to_status_id_str") val inReplyToStatusIdStr: String? = null,
    @Json(name = "in_reply_to_user_id")
    @SerialName("in_reply_to_user_id") @GhostName("in_reply_to_user_id") val inReplyToUserId: Long? = null,
    @Json(name = "in_reply_to_user_id_str")
    @SerialName("in_reply_to_user_id_str") @GhostName("in_reply_to_user_id_str") val inReplyToUserIdStr: String? = null,
    @Json(name = "in_reply_to_screen_name")
    @SerialName("in_reply_to_screen_name") @GhostName("in_reply_to_screen_name") val inReplyToScreenName: String? = null,
    val user: User,
    val geo: String? = null,
    val coordinates: String? = null,
    val place: String? = null,
    val contributors: String? = null,
    @Json(name = "retweet_count")
    @SerialName("retweet_count") @GhostName("retweet_count") val retweetCount: Int,
    @Json(name = "favorite_count")
    @SerialName("favorite_count") @GhostName("favorite_count") val favoriteCount: Int,
    val entities: TweetEntities,
    val favorited: Boolean,
    val retweeted: Boolean,
    @Json(name = "possibly_sensitive")
    @SerialName("possibly_sensitive") @GhostName("possibly_sensitive") val possiblySensitive: Boolean = false,
    val lang: String? = null,
    @Json(name = "retweeted_status")
    @SerialName("retweeted_status") @GhostName("retweeted_status") val retweetedStatus: Tweet? = null
)

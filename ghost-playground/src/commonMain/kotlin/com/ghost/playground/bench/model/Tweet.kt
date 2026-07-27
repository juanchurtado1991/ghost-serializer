package com.ghost.playground.bench.model

import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@GhostSerialization
data class Tweet(
    val metadata: TweetMetadata,
    @SerialName("created_at") @GhostName("created_at") val createdAt: String,
    val id: Long,
    @SerialName("id_str") @GhostName("id_str") val idStr: String,
    val text: String,
    val source: String,
    val truncated: Boolean,
    @SerialName("in_reply_to_status_id") @GhostName("in_reply_to_status_id") val inReplyToStatusId: Long? = null,
    @SerialName("in_reply_to_status_id_str") @GhostName("in_reply_to_status_id_str") val inReplyToStatusIdStr: String? = null,
    @SerialName("in_reply_to_user_id") @GhostName("in_reply_to_user_id") val inReplyToUserId: Long? = null,
    @SerialName("in_reply_to_user_id_str") @GhostName("in_reply_to_user_id_str") val inReplyToUserIdStr: String? = null,
    @SerialName("in_reply_to_screen_name") @GhostName("in_reply_to_screen_name") val inReplyToScreenName: String? = null,
    val user: TwitterUser,
    val geo: String? = null,
    val coordinates: String? = null,
    val place: String? = null,
    val contributors: String? = null,
    @SerialName("retweet_count") @GhostName("retweet_count") val retweetCount: Int,
    @SerialName("favorite_count") @GhostName("favorite_count") val favoriteCount: Int,
    val entities: TweetEntities,
    val favorited: Boolean,
    val retweeted: Boolean,
    @SerialName("possibly_sensitive") @GhostName("possibly_sensitive") val possiblySensitive: Boolean = false,
    val lang: String? = null,
    @SerialName("retweeted_status") @GhostName("retweeted_status") val retweetedStatus: Tweet? = null,
)

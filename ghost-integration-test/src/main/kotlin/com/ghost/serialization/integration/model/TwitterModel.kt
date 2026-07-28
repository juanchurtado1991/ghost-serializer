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
@GhostSerialization(textChannel = true)
data class TwitterResponse(
    val statuses: List<Tweet>
)

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

@JsonClass(generateAdapter = true)
@Serializable
@GhostSerialization
data class TweetMetadata(
    @Json(name = "result_type")
    @SerialName("result_type") @GhostName("result_type") val resultType: String,
    @Json(name = "iso_language_code")
    @SerialName("iso_language_code") @GhostName("iso_language_code") val isoLanguageCode: String
)

@JsonClass(generateAdapter = true)
@Serializable
@GhostSerialization
data class User(
    val id: Long,
    @Json(name = "id_str")
    @SerialName("id_str") @GhostName("id_str") val idStr: String,
    val name: String? = null,
    @Json(name = "screen_name")
    @SerialName("screen_name") @GhostName("screen_name") val screenName: String,
    val location: String? = null,
    val description: String? = null,
    val url: String? = null,
    val entities: UserEntities,
    val protected: Boolean = false,
    @Json(name = "followers_count")
    @SerialName("followers_count") @GhostName("followers_count") val followersCount: Int = 0,
    @Json(name = "friends_count")
    @SerialName("friends_count") @GhostName("friends_count") val friendsCount: Int = 0,
    @Json(name = "listed_count")
    @SerialName("listed_count") @GhostName("listed_count") val listedCount: Int = 0,
    @Json(name = "created_at")
    @SerialName("created_at") @GhostName("created_at") val createdAt: String,
    @Json(name = "favourites_count")
    @SerialName("favourites_count") @GhostName("favourites_count") val favouritesCount: Int = 0,
    @Json(name = "utc_offset")
    @SerialName("utc_offset") @GhostName("utc_offset") val utcOffset: Int? = null,
    @Json(name = "time_zone")
    @SerialName("time_zone") @GhostName("time_zone") val timeZone: String? = null,
    @Json(name = "geo_enabled")
    @SerialName("geo_enabled") @GhostName("geo_enabled") val geoEnabled: Boolean = false,
    val verified: Boolean = false,
    @Json(name = "statuses_count")
    @SerialName("statuses_count") @GhostName("statuses_count") val statusesCount: Int = 0,
    val lang: String? = null,
    @Json(name = "contributors_enabled")
    @SerialName("contributors_enabled") @GhostName("contributors_enabled") val contributorsEnabled: Boolean = false,
    @Json(name = "is_translator")
    @SerialName("is_translator") @GhostName("is_translator") val isTranslator: Boolean = false,
    @Json(name = "is_translation_enabled")
    @SerialName("is_translation_enabled") @GhostName("is_translation_enabled") val isTranslationEnabled: Boolean = false,
    @Json(name = "profile_background_color")
    @SerialName("profile_background_color") @GhostName("profile_background_color") val profileBackgroundColor: String,
    @Json(name = "profile_background_image_url")
    @SerialName("profile_background_image_url") @GhostName("profile_background_image_url") val profileBackgroundImageUrl: String,
    @Json(name = "profile_background_image_url_https")
    @SerialName("profile_background_image_url_https") @GhostName("profile_background_image_url_https") val profileBackgroundImageUrlHttps: String,
    @Json(name = "profile_background_tile")
    @SerialName("profile_background_tile") @GhostName("profile_background_tile") val profileBackgroundTile: Boolean = false,
    @Json(name = "profile_image_url")
    @SerialName("profile_image_url") @GhostName("profile_image_url") val profileImageUrl: String,
    @Json(name = "profile_image_url_https")
    @SerialName("profile_image_url_https") @GhostName("profile_image_url_https") val profileImageUrlHttps: String,
    @Json(name = "profile_banner_url")
    @SerialName("profile_banner_url") @GhostName("profile_banner_url") val profileBannerUrl: String? = null,
    @Json(name = "profile_link_color")
    @SerialName("profile_link_color") @GhostName("profile_link_color") val profileLinkColor: String,
    @Json(name = "profile_sidebar_border_color")
    @SerialName("profile_sidebar_border_color") @GhostName("profile_sidebar_border_color") val profileSidebarBorderColor: String,
    @Json(name = "profile_sidebar_fill_color")
    @SerialName("profile_sidebar_fill_color") @GhostName("profile_sidebar_fill_color") val profileSidebarFillColor: String,
    @Json(name = "profile_text_color")
    @SerialName("profile_text_color") @GhostName("profile_text_color") val profileTextColor: String? = null,
    @Json(name = "profile_use_background_image")
    @SerialName("profile_use_background_image") @GhostName("profile_use_background_image") val profileUseBackgroundImage: Boolean = false,
    @Json(name = "default_profile")
    @SerialName("default_profile") @GhostName("default_profile") val defaultProfile: Boolean = false,
    @Json(name = "default_profile_image")
    @SerialName("default_profile_image") @GhostName("default_profile_image") val defaultProfileImage: Boolean = false,
    val following: Boolean = false,
    @Json(name = "follow_request_sent")
    @SerialName("follow_request_sent") @GhostName("follow_request_sent") val followRequestSent: Boolean = false,
    val notifications: Boolean = false
)

@JsonClass(generateAdapter = true)
@Serializable
@GhostSerialization
data class UserEntities(
    val url: UrlContainer? = null,
    val description: UrlContainer
)

@JsonClass(generateAdapter = true)
@Serializable
@GhostSerialization
data class UrlContainer(
    val urls: List<UrlItem> = emptyList()
)

@JsonClass(generateAdapter = true)
@Serializable
@GhostSerialization
data class UrlItem(
    val url: String,
    @Json(name = "expanded_url")
    @SerialName("expanded_url") @GhostName("expanded_url") val expandedUrl: String? = null,
    @Json(name = "display_url")
    @SerialName("display_url") @GhostName("display_url") val displayUrl: String? = null,
    val indices: List<Int>
)

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

@JsonClass(generateAdapter = true)
@Serializable
@GhostSerialization
data class HashtagItem(
    val text: String,
    val indices: List<Int>
)

@JsonClass(generateAdapter = true)
@Serializable
@GhostSerialization
data class SymbolItem(
    val text: String = "",
    val indices: List<Int> = emptyList()
)

@JsonClass(generateAdapter = true)
@Serializable
@GhostSerialization
data class UserMention(
    @Json(name = "screen_name")
    @SerialName("screen_name") @GhostName("screen_name") val screenName: String,
    val name: String,
    val id: Long,
    @Json(name = "id_str")
    @SerialName("id_str") @GhostName("id_str") val idStr: String,
    val indices: List<Int>
)

@JsonClass(generateAdapter = true)
@Serializable
@GhostSerialization
data class MediaItem(
    val id: Long,
    @Json(name = "id_str")
    @SerialName("id_str") @GhostName("id_str") val idStr: String,
    val indices: List<Int>,
    @Json(name = "media_url")
    @SerialName("media_url") @GhostName("media_url") val mediaUrl: String,
    @Json(name = "media_url_https")
    @SerialName("media_url_https") @GhostName("media_url_https") val mediaUrlHttps: String,
    val url: String,
    @Json(name = "display_url")
    @SerialName("display_url") @GhostName("display_url") val displayUrl: String,
    @Json(name = "expanded_url")
    @SerialName("expanded_url") @GhostName("expanded_url") val expandedUrl: String,
    val type: String,
    val sizes: MediaSizes,
    @Json(name = "source_status_id")
    @SerialName("source_status_id") @GhostName("source_status_id") val sourceStatusId: Long? = null,
    @Json(name = "source_status_id_str")
    @SerialName("source_status_id_str") @GhostName("source_status_id_str") val sourceStatusIdStr: String? = null
)

@JsonClass(generateAdapter = true)
@Serializable
@GhostSerialization
data class MediaSizes(
    val medium: MediaSize,
    val small: MediaSize,
    val thumb: MediaSize,
    val large: MediaSize
)

@JsonClass(generateAdapter = true)
@Serializable
@GhostSerialization
data class MediaSize(
    val w: Int,
    val h: Int,
    val resize: String
)

@Serializable
@GhostSerialization
data class TwitterSpecialTweet(
    val id: Long,

    // 1. Flattening: extracts user screen_name directly from nested user object
    @GhostFlatten("user.screen_name")
    val screenName: String,

    // 2. Flattening 2 levels: extracts metadata result_type
    @GhostFlatten("metadata.result_type")
    val resultType: String,

    // 3. Regular text field (no GhostWrap so we can parse from original JSON)
    val text: String,

    // 4. Ignored field: ignored during serialization
    @GhostIgnore
    val source: String = ""
)

@Serializable
@GhostSerialization(textChannel = true)
data class TwitterSpecialResponse(
    val statuses: List<TwitterSpecialTweet>
)

@Serializable
@GhostSerialization(textChannel = true)
data class TwitterWrappedTweet(
    val id: Long,
    @GhostWrap("details")
    val text: String
)

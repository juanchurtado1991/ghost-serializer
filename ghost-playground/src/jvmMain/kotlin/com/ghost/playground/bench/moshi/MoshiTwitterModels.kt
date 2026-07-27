package com.ghost.playground.bench.moshi

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** JVM-only Moshi codegen models for the Speed Test lab (mirrors [com.ghost.playground.bench.model]). */
@JsonClass(generateAdapter = true)
data class TwitterResponse(
    val statuses: List<Tweet>,
)

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

@JsonClass(generateAdapter = true)
data class TweetMetadata(
    @Json(name = "result_type") val resultType: String,
    @Json(name = "iso_language_code") val isoLanguageCode: String,
)

@JsonClass(generateAdapter = true)
data class TwitterUser(
    val id: Long,
    @Json(name = "id_str") val idStr: String,
    val name: String? = null,
    @Json(name = "screen_name") val screenName: String,
    val location: String? = null,
    val description: String? = null,
    val url: String? = null,
    val entities: TwitterUserEntities,
    val protected: Boolean = false,
    @Json(name = "followers_count") val followersCount: Int = 0,
    @Json(name = "friends_count") val friendsCount: Int = 0,
    @Json(name = "listed_count") val listedCount: Int = 0,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "favourites_count") val favouritesCount: Int = 0,
    @Json(name = "utc_offset") val utcOffset: Int? = null,
    @Json(name = "time_zone") val timeZone: String? = null,
    @Json(name = "geo_enabled") val geoEnabled: Boolean = false,
    val verified: Boolean = false,
    @Json(name = "statuses_count") val statusesCount: Int = 0,
    val lang: String? = null,
    @Json(name = "contributors_enabled") val contributorsEnabled: Boolean = false,
    @Json(name = "is_translator") val isTranslator: Boolean = false,
    @Json(name = "is_translation_enabled") val isTranslationEnabled: Boolean = false,
    @Json(name = "profile_background_color") val profileBackgroundColor: String,
    @Json(name = "profile_background_image_url") val profileBackgroundImageUrl: String,
    @Json(name = "profile_background_image_url_https") val profileBackgroundImageUrlHttps: String,
    @Json(name = "profile_background_tile") val profileBackgroundTile: Boolean = false,
    @Json(name = "profile_image_url") val profileImageUrl: String,
    @Json(name = "profile_image_url_https") val profileImageUrlHttps: String,
    @Json(name = "profile_banner_url") val profileBannerUrl: String? = null,
    @Json(name = "profile_link_color") val profileLinkColor: String,
    @Json(name = "profile_sidebar_border_color") val profileSidebarBorderColor: String,
    @Json(name = "profile_sidebar_fill_color") val profileSidebarFillColor: String,
    @Json(name = "profile_text_color") val profileTextColor: String? = null,
    @Json(name = "profile_use_background_image") val profileUseBackgroundImage: Boolean = false,
    @Json(name = "default_profile") val defaultProfile: Boolean = false,
    @Json(name = "default_profile_image") val defaultProfileImage: Boolean = false,
    val following: Boolean = false,
    @Json(name = "follow_request_sent") val followRequestSent: Boolean = false,
    val notifications: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class TwitterUserEntities(
    val url: UrlContainer? = null,
    val description: UrlContainer,
)

@JsonClass(generateAdapter = true)
data class UrlContainer(
    val urls: List<UrlItem> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class UrlItem(
    val url: String,
    @Json(name = "expanded_url") val expandedUrl: String? = null,
    @Json(name = "display_url") val displayUrl: String? = null,
    val indices: List<Int>,
)

@JsonClass(generateAdapter = true)
data class TweetEntities(
    val hashtags: List<HashtagItem> = emptyList(),
    val symbols: List<SymbolItem> = emptyList(),
    val urls: List<UrlItem> = emptyList(),
    @Json(name = "user_mentions") val userMentions: List<UserMention> = emptyList(),
    val media: List<MediaItem> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class HashtagItem(
    val text: String,
    val indices: List<Int>,
)

@JsonClass(generateAdapter = true)
data class SymbolItem(
    val text: String = "",
    val indices: List<Int> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class UserMention(
    @Json(name = "screen_name") val screenName: String,
    val name: String,
    val id: Long,
    @Json(name = "id_str") val idStr: String,
    val indices: List<Int>,
)

@JsonClass(generateAdapter = true)
data class MediaItem(
    val id: Long,
    @Json(name = "id_str") val idStr: String,
    val indices: List<Int>,
    @Json(name = "media_url") val mediaUrl: String,
    @Json(name = "media_url_https") val mediaUrlHttps: String,
    val url: String,
    @Json(name = "display_url") val displayUrl: String,
    @Json(name = "expanded_url") val expandedUrl: String,
    val type: String,
    val sizes: MediaSizes,
    @Json(name = "source_status_id") val sourceStatusId: Long? = null,
    @Json(name = "source_status_id_str") val sourceStatusIdStr: String? = null,
)

@JsonClass(generateAdapter = true)
data class MediaSizes(
    val medium: MediaSize,
    val small: MediaSize,
    val thumb: MediaSize,
    val large: MediaSize,
)

@JsonClass(generateAdapter = true)
data class MediaSize(
    val w: Int,
    val h: Int,
    val resize: String,
)

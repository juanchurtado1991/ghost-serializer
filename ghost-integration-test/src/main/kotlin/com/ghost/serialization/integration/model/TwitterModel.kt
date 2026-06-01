package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostFlatten
import com.ghost.serialization.annotations.GhostWrap
import com.ghost.serialization.annotations.GhostIgnore
import com.ghost.serialization.annotations.GhostResilient
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
@GhostSerialization
data class TwitterResponse(
    val statuses: List<Tweet>
)

@Serializable
@GhostSerialization
data class Tweet(
    val metadata: TweetMetadata,
    @SerialName("created_at") @GhostName("created_at")
    val createdAt: String,
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
    val user: User,
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
    @SerialName("retweeted_status") @GhostName("retweeted_status") val retweetedStatus: Tweet? = null
)

@Serializable
@GhostSerialization
data class TweetMetadata(
    @SerialName("result_type") @GhostName("result_type") val resultType: String,
    @SerialName("iso_language_code") @GhostName("iso_language_code") val isoLanguageCode: String
)

@Serializable
@GhostSerialization
data class User(
    val id: Long,
    @SerialName("id_str") @GhostName("id_str") val idStr: String,
    val name: String? = null,
    @SerialName("screen_name") @GhostName("screen_name") val screenName: String,
    val location: String? = null,
    val description: String? = null,
    val url: String? = null,
    val entities: UserEntities,
    val protected: Boolean = false,
    @SerialName("followers_count") @GhostName("followers_count") val followersCount: Int = 0,
    @SerialName("friends_count") @GhostName("friends_count") val friendsCount: Int = 0,
    @SerialName("listed_count") @GhostName("listed_count") val listedCount: Int = 0,
    @SerialName("created_at") @GhostName("created_at") val createdAt: String,
    @SerialName("favourites_count") @GhostName("favourites_count") val favouritesCount: Int = 0,
    @SerialName("utc_offset") @GhostName("utc_offset") val utcOffset: Int? = null,
    @SerialName("time_zone") @GhostName("time_zone") val timeZone: String? = null,
    @SerialName("geo_enabled") @GhostName("geo_enabled") val geoEnabled: Boolean = false,
    val verified: Boolean = false,
    @SerialName("statuses_count") @GhostName("statuses_count") val statusesCount: Int = 0,
    val lang: String? = null,
    @SerialName("contributors_enabled") @GhostName("contributors_enabled") val contributorsEnabled: Boolean = false,
    @SerialName("is_translator") @GhostName("is_translator") val isTranslator: Boolean = false,
    @SerialName("is_translation_enabled") @GhostName("is_translation_enabled") val isTranslationEnabled: Boolean = false,
    @SerialName("profile_background_color") @GhostName("profile_background_color") val profileBackgroundColor: String,
    @SerialName("profile_background_image_url") @GhostName("profile_background_image_url") val profileBackgroundImageUrl: String,
    @SerialName("profile_background_image_url_https") @GhostName("profile_background_image_url_https") val profileBackgroundImageUrlHttps: String,
    @SerialName("profile_background_tile") @GhostName("profile_background_tile") val profileBackgroundTile: Boolean = false,
    @SerialName("profile_image_url") @GhostName("profile_image_url") val profileImageUrl: String,
    @SerialName("profile_image_url_https") @GhostName("profile_image_url_https") val profileImageUrlHttps: String,
    @SerialName("profile_banner_url") @GhostName("profile_banner_url") val profileBannerUrl: String? = null,
    @SerialName("profile_link_color") @GhostName("profile_link_color") val profileLinkColor: String,
    @SerialName("profile_sidebar_border_color") @GhostName("profile_sidebar_border_color") val profileSidebarBorderColor: String,
    @SerialName("profile_sidebar_fill_color") @GhostName("profile_sidebar_fill_color") val profileSidebarFillColor: String,
    @SerialName("profile_text_color") @GhostName("profile_text_color") val profileTextColor: String? = null,
    @SerialName("profile_use_background_image") @GhostName("profile_use_background_image") val profileUseBackgroundImage: Boolean = false,
    @SerialName("default_profile") @GhostName("default_profile") val defaultProfile: Boolean = false,
    @SerialName("default_profile_image") @GhostName("default_profile_image") val defaultProfileImage: Boolean = false,
    val following: Boolean = false,
    @SerialName("follow_request_sent") @GhostName("follow_request_sent") val followRequestSent: Boolean = false,
    val notifications: Boolean = false
)

@Serializable
@GhostSerialization
data class UserEntities(
    val url: UrlContainer? = null,
    val description: UrlContainer
)

@Serializable
@GhostSerialization
data class UrlContainer(
    val urls: List<UrlItem> = emptyList()
)

@Serializable
@GhostSerialization
data class UrlItem(
    val url: String,
    @SerialName("expanded_url") @GhostName("expanded_url") val expandedUrl: String? = null,
    @SerialName("display_url") @GhostName("display_url") val displayUrl: String? = null,
    val indices: List<Int>
)

@Serializable
@GhostSerialization
data class TweetEntities(
    val hashtags: List<HashtagItem> = emptyList(),
    val symbols: List<SymbolItem> = emptyList(),
    val urls: List<UrlItem> = emptyList(),
    @SerialName("user_mentions") @GhostName("user_mentions") val userMentions: List<UserMention> = emptyList(),
    val media: List<MediaItem> = emptyList()
)

@Serializable
@GhostSerialization
data class HashtagItem(
    val text: String,
    val indices: List<Int>
)

@Serializable
@GhostSerialization
data class SymbolItem(
    val text: String = "",
    val indices: List<Int> = emptyList()
)

@Serializable
@GhostSerialization
data class UserMention(
    @SerialName("screen_name") @GhostName("screen_name") val screenName: String,
    val name: String,
    val id: Long,
    @SerialName("id_str") @GhostName("id_str") val idStr: String,
    val indices: List<Int>
)

@Serializable
@GhostSerialization
data class MediaItem(
    val id: Long,
    @SerialName("id_str") @GhostName("id_str") val idStr: String,
    val indices: List<Int>,
    @SerialName("media_url") @GhostName("media_url") val mediaUrl: String,
    @SerialName("media_url_https") @GhostName("media_url_https") val mediaUrlHttps: String,
    val url: String,
    @SerialName("display_url") @GhostName("display_url") val displayUrl: String,
    @SerialName("expanded_url") @GhostName("expanded_url") val expandedUrl: String,
    val type: String,
    val sizes: MediaSizes,
    @SerialName("source_status_id") @GhostName("source_status_id") val sourceStatusId: Long? = null,
    @SerialName("source_status_id_str") @GhostName("source_status_id_str") val sourceStatusIdStr: String? = null
)

@Serializable
@GhostSerialization
data class MediaSizes(
    val medium: MediaSize,
    val small: MediaSize,
    val thumb: MediaSize,
    val large: MediaSize
)

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
@GhostSerialization
data class TwitterSpecialResponse(
    val statuses: List<TwitterSpecialTweet>
)

@Serializable
@GhostSerialization
data class TwitterWrappedTweet(
    val id: Long,
    @GhostWrap("details")
    val text: String
)



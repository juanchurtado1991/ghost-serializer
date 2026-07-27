package com.ghost.playground.bench.model

import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@GhostSerialization
data class TwitterUser(
    val id: Long,
    @SerialName("id_str") @GhostName("id_str") val idStr: String,
    val name: String? = null,
    @SerialName("screen_name") @GhostName("screen_name") val screenName: String,
    val location: String? = null,
    val description: String? = null,
    val url: String? = null,
    val entities: TwitterUserEntities,
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
    val notifications: Boolean = false,
)

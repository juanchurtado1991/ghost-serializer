package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

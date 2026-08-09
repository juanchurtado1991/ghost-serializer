package com.ghost.playground.bench.moshi

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** JVM-only Moshi codegen models for the Speed Test lab, mirroring `com.ghost.playground.bench.model`. */
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

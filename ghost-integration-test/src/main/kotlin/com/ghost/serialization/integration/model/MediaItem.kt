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
